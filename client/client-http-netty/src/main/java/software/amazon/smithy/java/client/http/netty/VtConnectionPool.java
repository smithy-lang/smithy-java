/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Per-route pool of blocking {@link VtH1Connection}s for the virtual-thread transport.
 *
 * <p>Each route has its own lock and idle LIFO stack (no single global lock / {@code signalAll}
 * thundering herd). Connections idle longer than the reuse-idle window are validated before reuse;
 * connections idle past {@code maxIdleTime} are evicted. When a route is at capacity, acquire blocks
 * on that route's condition until a connection is released — cheap under virtual threads. An
 * unbounded route ({@code maxConnectionsPerHost == Integer.MAX_VALUE}) skips the capacity gate and
 * its condition entirely.
 */
final class VtConnectionPool implements AutoCloseable {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(VtConnectionPool.class);

    private final NettyHttpTransportConfig config;
    private final VtTlsContext tlsContext;
    private final int maxPerHost;
    private final boolean unbounded;
    private final long reuseIdleNanos;
    private final long maxIdleNanos;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final long acquireTimeoutMs;

    private final Map<Route, RoutePool> pools = new ConcurrentHashMap<>();
    private volatile boolean closed;

    VtConnectionPool(NettyHttpTransportConfig config, VtTlsContext tlsContext) {
        this.config = config;
        this.tlsContext = tlsContext;
        this.maxPerHost = config.maxConnectionsPerHost();
        this.unbounded = maxPerHost == Integer.MAX_VALUE;
        this.reuseIdleNanos = config.reuseIdleTimeout().toNanos();
        this.maxIdleNanos = config.maxIdleTime().toNanos();
        this.connectTimeoutMs = 10_000;
        this.readTimeoutMs = 30_000;
        this.acquireTimeoutMs = config.acquireTimeout().toMillis();
    }

    /** Acquire a connection for the route, reusing a healthy pooled one when available. */
    VtH1Connection acquire(Route route) throws IOException {
        return acquire(route, false);
    }

    /** Acquire a guaranteed-fresh connection (stale-retry path). */
    VtH1Connection acquireFresh(Route route) throws IOException {
        return acquire(route, true);
    }

    private VtH1Connection acquire(Route route, boolean forceFresh) throws IOException {
        if (closed) {
            throw new IOException("Pool closed");
        }
        RoutePool pool = pools.computeIfAbsent(route, RoutePool::new);

        // Permit-first model (mirrors the proven native H1 pool): take a permit — which counts only
        // in-use connections and blocks at capacity — then reuse a pooled idle connection if one is
        // available, otherwise open a new one. This keeps total open connections <= maxPerHost and
        // lets a waiter that wakes on a release actually pick up the just-freed connection.
        pool.acquirePermit();
        try {
            if (!forceFresh) {
                VtH1Connection reused = pool.pollValid();
                if (reused != null) {
                    reused.setFromReuse(true);
                    return reused;
                }
            }
            VtH1Connection conn = VtH1Connection.open(route, tlsContext, connectTimeoutMs, readTimeoutMs);
            conn.setFromReuse(false);
            return conn;
        } catch (IOException | RuntimeException e) {
            pool.releasePermit();
            throw e;
        }
    }

    /** Return a healthy, fully-drained connection to the pool for reuse. */
    void release(VtH1Connection conn) {
        RoutePool pool = pools.get(conn.route());
        if (closed || pool == null || !conn.isKeepAlive() || !conn.isOpen()) {
            conn.close();
            if (pool != null) {
                pool.releasePermit();
            }
            return;
        }
        conn.markUsedNow();
        // Hand the connection to the idle stack AND release the permit so a waiter can take it.
        pool.releaseToIdle(conn);
    }

    /** Close a connection and free its route permit (the connection is not reusable). */
    void dispose(VtH1Connection conn) {
        conn.close();
        RoutePool pool = pools.get(conn.route());
        if (pool != null) {
            pool.releasePermit();
        }
    }

    /** Evict connections idle longer than maxIdleTime. */
    void evictIdle() {
        long now = System.nanoTime();
        for (RoutePool pool : pools.values()) {
            pool.evictIdle(now);
        }
    }

    @Override
    public void close() {
        closed = true;
        for (RoutePool pool : pools.values()) {
            pool.closeAll();
        }
        pools.clear();
    }

    /**
     * Per-route state. A <em>permit</em> represents one in-use lease and is bounded by
     * {@code maxPerHost}; idle (pooled) connections are <em>not</em> permit-charged. Thus the number
     * of physical connections equals {@code active + idle.size()}, which never exceeds the bound
     * because opening a new connection requires holding a permit and only happens when the idle
     * stack is empty.
     */
    private final class RoutePool {
        private final Route route;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition permitFreed = lock.newCondition();
        private final ArrayDeque<VtH1Connection> idle = new ArrayDeque<>();
        private int active; // in-use leases, bounded by maxPerHost (unbounded => just a counter)

        RoutePool(Route route) {
            this.route = route;
        }

        /** Take a lease permit, blocking up to acquireTimeout when bounded and at capacity. */
        void acquirePermit() throws IOException {
            lock.lock();
            try {
                if (!unbounded) {
                    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(acquireTimeoutMs);
                    while (active >= maxPerHost) {
                        long remaining = deadline - System.nanoTime();
                        if (remaining <= 0) {
                            throw new IOException("Timed out acquiring connection for " + route);
                        }
                        try {
                            permitFreed.awaitNanos(remaining);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted acquiring connection", e);
                        }
                    }
                }
                active++;
            } finally {
                lock.unlock();
            }
        }

        /** Free a lease permit without pooling the connection. */
        void releasePermit() {
            lock.lock();
            try {
                if (active > 0) {
                    active--;
                    permitFreed.signal();
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * Pool a healthy connection for reuse and free the caller's permit. The connection moves
         * from "active lease" to "idle"; the freed permit lets a waiter lease it.
         */
        void releaseToIdle(VtH1Connection conn) {
            lock.lock();
            try {
                if (!closed) {
                    idle.offerFirst(conn);
                    conn = null;
                }
                if (active > 0) {
                    active--;
                    permitFreed.signal();
                }
            } finally {
                lock.unlock();
            }
            if (conn != null) {
                conn.close(); // pool closed: don't retain
            }
        }

        /**
         * Pull a healthy idle connection (caller already holds a permit), validating per idle age.
         * Returns null if none usable — the caller then opens a new connection under its permit.
         */
        VtH1Connection pollValid() {
            lock.lock();
            try {
                VtH1Connection c;
                long now = System.nanoTime();
                while ((c = idle.pollFirst()) != null) {
                    long idleNanos = now - c.lastUsedNanos();
                    if (idleNanos >= maxIdleNanos || !c.isOpen()) {
                        c.close();
                        continue;
                    }
                    // Validate connections idle past the reuse window: a server may have closed a
                    // long-idle keep-alive. Fresh-enough connections skip the probe.
                    if (reuseIdleNanos > 0 && idleNanos >= reuseIdleNanos && !c.validateForReuse()) {
                        c.close();
                        continue;
                    }
                    return c;
                }
                return null;
            } finally {
                lock.unlock();
            }
        }

        void evictIdle(long now) {
            lock.lock();
            try {
                Iterator<VtH1Connection> it = idle.iterator();
                while (it.hasNext()) {
                    VtH1Connection c = it.next();
                    if (now - c.lastUsedNanos() >= maxIdleNanos || !c.isOpen()) {
                        it.remove();
                        c.close();
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        void closeAll() {
            lock.lock();
            try {
                VtH1Connection c;
                while ((c = idle.pollFirst()) != null) {
                    c.close();
                }
                active = 0;
                permitFreed.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
}
