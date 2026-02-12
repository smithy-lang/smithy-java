/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.http.client.h2.H2Connection;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Manages HTTP/2 connections with adaptive load balancing.
 *
 * <h2>Load Balancing Strategy</h2>
 * <p>Uses {@link H2LoadBalancer}, which by default uses a high-watermark strategy.
 *
 * <h2>Threading</h2>
 * <p>Uses per-route state with a volatile connection array for lock-free reads in the
 * common case. Connection creation and removal synchronize on the per-route state object.
 */
final class H2ConnectionManager {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(H2ConnectionManager.class);

    /**
     * Per-route connection state.
     */
    private static final class RouteState {
        /** Connections for this route. Volatile for lock-free reads. */
        volatile H2Connection[] conns = new H2Connection[0];

        /** Connections currently being created (prevents over-creation). Guarded by lock. */
        int pendingCreations = 0;

        /** Scratch buffer for active stream counts, guarded by lock. */
        int[] activeStreamsBuf = new int[4];

        /** Lock for state modifications. ReentrantLock avoids VT pinning unlike synchronized. */
        final ReentrantLock lock = new ReentrantLock();
        final Condition available = lock.newCondition();
    }

    private static final H2Connection[] EMPTY = new H2Connection[0];

    // Soft limit as a fraction of streamsPerConnection. When all connections exceed this threshold,
    // we try to create a new connection (if under max).
    private static final int DEFAULT_SOFT_LIMIT_DIVISOR = 4;
    private static final int DEFAULT_SOFT_LIMIT_FLOOR = 25;

    private final ConcurrentHashMap<Route, RouteState> routes = new ConcurrentHashMap<>();
    private final int streamsPerConnection;
    private final H2LoadBalancer loadBalancer;
    private final long acquireTimeoutMs;
    private final List<ConnectionPoolListener> listeners;
    private final ConnectionFactory connectionFactory;

    @FunctionalInterface
    interface ConnectionFactory {
        H2Connection create(Route route) throws IOException;
    }

    H2ConnectionManager(
            int streamsPerConnection,
            H2LoadBalancer loadBalancer,
            long acquireTimeoutMs,
            List<ConnectionPoolListener> listeners,
            ConnectionFactory connectionFactory
    ) {
        this.streamsPerConnection = streamsPerConnection;
        this.acquireTimeoutMs = acquireTimeoutMs;
        this.listeners = listeners;
        this.connectionFactory = connectionFactory;

        if (loadBalancer != null) {
            this.loadBalancer = loadBalancer;
        } else {
            this.loadBalancer = H2LoadBalancer.watermark(
                    Math.max(DEFAULT_SOFT_LIMIT_FLOOR, streamsPerConnection / DEFAULT_SOFT_LIMIT_DIVISOR),
                    streamsPerConnection);
        }
    }

    private RouteState stateFor(Route route) {
        return routes.computeIfAbsent(route, r -> new RouteState());
    }

    /**
     * Acquire an HTTP/2 connection for the route, creating one if needed.
     *
     * <p>Connection creation happens outside the synchronized block to prevent deadlock
     * when connection establishment blocks on I/O.
     *
     * <p>Note: Under high contention, we may create slightly more connections than strictly
     * necessary. This is intentional - we bias toward expansion to avoid coordination
     * bottlenecks, accepting minor over-provisioning as a tradeoff.
     *
     * @param route the target route
     * @param maxConnectionsForRoute maximum connections allowed for this route
     * @return an H2 connection ready for use
     * @throws IOException if acquisition times out or is interrupted
     */
    H2Connection acquire(Route route, int maxConnectionsForRoute) throws IOException {
        RouteState state = stateFor(route);
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(acquireTimeoutMs);

        state.lock.lock();
        try {
            while (true) {
                H2Connection[] snapshot = state.conns;
                int connCount = snapshot.length;
                int totalConns = connCount + state.pendingCreations;

                // Build active stream counts for the load balancer
                if (state.activeStreamsBuf.length < connCount) {
                    state.activeStreamsBuf = new int[connCount];
                }
                for (int i = 0; i < connCount; i++) {
                    state.activeStreamsBuf[i] = snapshot[i].getActiveStreamCountIfAccepting();
                }

                boolean canExpand = totalConns < maxConnectionsForRoute;
                int selected = loadBalancer.select(state.activeStreamsBuf, connCount,
                        canExpand ? maxConnectionsForRoute : connCount);

                if (selected >= 0) {
                    notifyAcquire(snapshot[selected], true);
                    return snapshot[selected];
                } else if (selected == H2LoadBalancer.CREATE_NEW && canExpand) {
                    state.pendingCreations++;
                    break;
                }

                // Saturated: wait for capacity
                LOGGER.debug("All {} connections saturated for route {}, waiting for capacity "
                        + "(canExpand={}, selected={})", connCount, route, canExpand, selected);
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new IOException("Acquire timeout: no connection available after "
                            + acquireTimeoutMs + "ms for " + route);
                }

                try {
                    state.available.awaitNanos(remainingNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for connection", e);
                }
            }
        } finally {
            state.lock.unlock();
        }

        return createNewH2Connection(route, state);
    }

    private H2Connection createNewH2Connection(Route route, RouteState state) throws IOException {
        // Create new connection OUTSIDE the lock to avoid deadlock.
        H2Connection newConn = null;
        IOException createException = null;
        try {
            newConn = connectionFactory.create(route);
        } catch (IOException e) {
            createException = e;
        } finally {
            // Register under lock (or decrement pending on failure)
            state.lock.lock();
            try {
                state.pendingCreations--;
                if (newConn != null) {
                    H2Connection[] cur = state.conns;
                    H2Connection[] next = new H2Connection[cur.length + 1];
                    System.arraycopy(cur, 0, next, 0, cur.length);
                    next[cur.length] = newConn;
                    state.conns = next;
                }
                state.available.signalAll(); // Wake waiters
            } finally {
                state.lock.unlock();
            }
        }

        if (createException != null) {
            throw createException;
        }

        notifyAcquire(newConn, false);
        return newConn;
    }

    /**
     * Unregister a connection from the route.
     */
    void unregister(Route route, H2Connection conn) {
        RouteState state = routes.get(route);
        if (state == null) {
            return;
        }
        state.lock.lock();
        try {
            H2Connection[] cur = state.conns;
            int n = cur.length;
            int idx = -1;
            for (int i = 0; i < n; i++) {
                if (cur[i] == conn) {
                    idx = i;
                    break;
                }
            }

            if (idx < 0) {
                return;
            } else if (n == 1) {
                state.conns = EMPTY;
            } else {
                H2Connection[] next = new H2Connection[n - 1];
                System.arraycopy(cur, 0, next, 0, idx);
                System.arraycopy(cur, idx + 1, next, idx, n - idx - 1);
                state.conns = next;
            }
            state.available.signalAll();
        } finally {
            state.lock.unlock();
        }
    }

    void cleanupDead(Route route, BiConsumer<H2Connection, CloseReason> onRemove) {
        RouteState state = routes.get(route);
        if (state == null) {
            return;
        }

        H2Connection[] cur = state.conns;

        // Quick check without lock - if all look healthy, skip
        boolean anyDead = false;
        for (H2Connection conn : cur) {
            if (conn != null && (!conn.canAcceptMoreStreams() || !conn.isActive())) {
                anyDead = true;
                break;
            }
        }
        if (!anyDead) {
            return;
        }

        // Slow path: actually clean up under lock
        state.lock.lock();
        try {
            cur = state.conns; // Re-read under lock
            int n = cur.length;
            H2Connection[] tmp = new H2Connection[n];
            int w = 0;
            for (H2Connection conn : cur) {
                if (conn == null) {
                    continue;
                }
                if (!conn.canAcceptMoreStreams() || !conn.isActive()) {
                    CloseReason reason = conn.isActive()
                            ? CloseReason.EVICTED
                            : CloseReason.UNEXPECTED_CLOSE;
                    onRemove.accept(conn, reason);
                } else {
                    tmp[w++] = conn;
                }
            }
            if (w != n) {
                H2Connection[] next = new H2Connection[w];
                System.arraycopy(tmp, 0, next, 0, w);
                state.conns = next;
                // Wake waiters - removed connections free capacity
                state.available.signalAll();
            }
        } finally {
            state.lock.unlock();
        }
    }

    void cleanupAllDead(BiConsumer<H2Connection, CloseReason> onRemove) {
        for (Route route : routes.keySet()) {
            cleanupDead(route, onRemove);
        }
    }

    /**
     * Clean up idle connections that have no active streams and have been idle longer than the specified timeout.
     *
     * @param maxIdleTimeNanos maximum idle time in nanoseconds
     * @param onRemove         callback for removed connections
     */
    void cleanupIdle(long maxIdleTimeNanos, BiConsumer<H2Connection, CloseReason> onRemove) {
        for (RouteState state : routes.values()) {
            H2Connection[] cur = state.conns;

            // Quick check without lock - if none look idle, skip
            boolean anyIdle = false;
            for (H2Connection conn : cur) {
                if (conn != null && conn.getIdleTimeNanos() > maxIdleTimeNanos) {
                    anyIdle = true;
                    break;
                }
            }
            if (!anyIdle) {
                continue;
            }

            // Slow path: clean up under lock
            state.lock.lock();
            try {
                cur = state.conns; // Re-read under lock
                int n = cur.length;
                H2Connection[] tmp = new H2Connection[n];
                int w = 0;
                for (H2Connection conn : cur) {
                    if (conn == null) {
                        continue;
                    }
                    if (conn.getIdleTimeNanos() > maxIdleTimeNanos) {
                        onRemove.accept(conn, CloseReason.IDLE_TIMEOUT);
                    } else {
                        tmp[w++] = conn;
                    }
                }
                if (w != n) {
                    H2Connection[] next = new H2Connection[w];
                    System.arraycopy(tmp, 0, next, 0, w);
                    state.conns = next;
                    // Wake waiters - removed connections free capacity
                    state.available.signalAll();
                }
            } finally {
                state.lock.unlock();
            }
        }
    }

    /**
     * Close all connections for shutdown.
     *
     * <p>This is a best-effort shutdown. In-flight acquires that already have a cached
     * RouteState may continue to operate briefly. For hard shutdown semantics, callers
     * should ensure no new requests are submitted before calling this method.
     */
    void closeAll(BiConsumer<H2Connection, CloseReason> onClose) {
        for (RouteState state : routes.values()) {
            H2Connection[] snapshot = state.conns;
            for (H2Connection conn : snapshot) {
                if (conn != null) {
                    onClose.accept(conn, CloseReason.POOL_SHUTDOWN);
                }
            }
        }
        routes.clear();
    }

    private void notifyAcquire(H2Connection conn, boolean reused) {
        for (ConnectionPoolListener listener : listeners) {
            listener.onAcquire(conn, reused);
        }
    }
}
