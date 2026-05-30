/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Manages HTTP/1.1 connection pooling.
 *
 * <p>Pools idle connections per route using LIFO queues. Connections are
 * validated before reuse and cleaned up when idle too long.
 */
final class H1ConnectionManager {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(H1ConnectionManager.class);

    // Skip expensive socket validation for connections idle < 1 second
    private static final long VALIDATION_THRESHOLD_NANOS = 1_000_000_000L;

    private final ConcurrentHashMap<Route, HostPool> pools = new ConcurrentHashMap<>();
    private final long maxIdleTimeNanos;
    private volatile Route cachedRoute;
    private volatile HostPool cachedPool;

    H1ConnectionManager(long maxIdleTimeNanos) {
        this.maxIdleTimeNanos = maxIdleTimeNanos;
    }

    /**
     * Try to acquire a pooled connection for the route.
     *
     * @param route the route
     * @param maxConnections max pooled connections for this route (used if pool doesn't exist)
     * @return a valid pooled connection, or null if none available
     */
    PooledConnection tryAcquire(Route route, int maxConnections, ActiveConnectionLimit activeLimit, int maxTotalConnections) {
        HostPool hostPool = getOrCreatePool(route, maxConnections, activeLimit, maxTotalConnections);

        PooledConnection pooled;
        while ((pooled = hostPool.poll()) != null) {
            if (validateConnection(pooled)) {
                LOGGER.debug("Reusing pooled connection to {}", route);
                return pooled;
            }

            // Connection failed validation - close it and try next
            LOGGER.debug("Closing invalid pooled connection to {}", route);
            try {
                pooled.connection.close();
            } catch (IOException e) {
                LOGGER.debug("Error closing invalid connection to {}: {}", route, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Get or create a pool for the route.
     *
     * @param route the route
     * @param maxConnections max pooled connections for this route
     * @return the pool for the route
     * @throws IllegalStateException if a pool exists with a different maxConnections
     */
    HostPool getOrCreatePool(
            Route route,
            int maxConnections,
            ActiveConnectionLimit activeLimit,
            int maxTotalConnections) {
        Route currentRoute = cachedRoute;
        HostPool currentPool = cachedPool;
        if (route.equals(currentRoute) && currentPool != null) {
            validatePoolConfig(route, currentPool, maxConnections, activeLimit);
            return currentPool;
        }

        return pools.compute(route, (k, existing) -> {
            if (existing == null) {
                existing = new HostPool(maxConnections, activeLimit, maxTotalConnections);
            } else {
                validatePoolConfig(route, existing, maxConnections, activeLimit);
            }
            cachedRoute = route;
            cachedPool = existing;
            return existing;
        });
    }

    long acquireActive(
            Route route,
            int maxConnections,
            ActiveConnectionLimit activeLimit,
            int maxTotalConnections,
            long acquireTimeoutMs) throws IOException {
        HostPool hostPool = getOrCreatePool(route, maxConnections, activeLimit, maxTotalConnections);
        try {
            if (!hostPool.tryAcquireActive(acquireTimeoutMs)) {
                throw new IOException("Connection pool exhausted for route " + route
                        + ": " + hostPool.activeLimit() + " active connections in use (timed out after "
                        + acquireTimeoutMs + "ms)");
            }
            return System.nanoTime();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for active connection permit", e);
        }
    }

    void releaseActive(Route route) {
        HostPool hostPool = getCachedPool(route);
        if (hostPool == null) {
            hostPool = pools.get(route);
        }
        if (hostPool != null) {
            hostPool.releaseActive();
        }
    }

    void trackActive(Route route, HttpConnection connection, long leaseStartedNanos) {
        HostPool hostPool = getCachedPool(route);
        if (hostPool == null) {
            hostPool = pools.get(route);
        }
        if (hostPool != null) {
            hostPool.trackActive(connection, leaseStartedNanos);
        }
    }

    void releaseActive(HttpConnection connection) {
        HostPool hostPool = getCachedPool(connection.route());
        if (hostPool == null) {
            hostPool = pools.get(connection.route());
        }
        if (hostPool != null) {
            hostPool.releaseActive(connection);
        }
    }

    private static void validatePoolConfig(Route route, HostPool pool, int maxConnections, ActiveConnectionLimit limit) {
        if (pool.maxConnections != maxConnections) {
            throw new IllegalStateException(
                    "Pool for " + route + " already exists with maxConnections=" + pool.maxConnections
                            + ", cannot change to " + maxConnections);
        }
        if (!pool.activeLimit.equals(limit)) {
            throw new IllegalStateException(
                    "Pool for " + route + " already exists with activeConnectionLimit=" + pool.activeLimit
                            + ", cannot change to " + limit);
        }
    }

    /**
     * Release a connection back to the pool.
     *
     * <p>This method may block if the pool is temporarily full, allowing short-lived contention to resolve and
     * keeping the pool warm under bursty workloads.
     *
     * @return true if pooled, false if pool full or closed
     */
    boolean release(Route route, HttpConnection connection, boolean poolClosed) {
        if (!connection.isActive() || poolClosed) {
            LOGGER.debug("Not pooling inactive connection to {} (poolClosed={})", route, poolClosed);
            return false;
        }

        HostPool hostPool = getCachedPool(route);
        if (hostPool == null) {
            hostPool = pools.get(route);
        }
        if (hostPool == null) {
            return false;
        }

        var conn = new PooledConnection(connection, System.nanoTime());
        boolean pooled = hostPool.offer(conn);
        if (pooled) {
            LOGGER.debug("Released h1 connection to pool for {}", route);
        } else {
            LOGGER.debug("h1 pool full, not pooling connection to {}", route);
        }
        return pooled;
    }

    /**
     * Remove a specific connection from the pool.
     */
    void remove(Route route, HttpConnection connection) {
        HostPool hostPool = pools.get(route);
        if (hostPool != null) {
            hostPool.remove(connection);
        }
    }

    private HostPool getCachedPool(Route route) {
        Route currentRoute = cachedRoute;
        HostPool currentPool = cachedPool;
        return route.equals(currentRoute) ? currentPool : null;
    }

    /**
     * Clean up idle and unhealthy connections, and remove empty pools.
     *
     * @param onRemove callback for each removed connection
     * @return total number of connections removed
     */
    int cleanupIdle(BiConsumer<HttpConnection, CloseReason> onRemove) {
        int totalRemoved = 0;
        for (HostPool pool : pools.values()) {
            totalRemoved += pool.removeIdleConnections(maxIdleTimeNanos, onRemove);
        }

        // Remove unused pools to prevent unbounded growth with dynamic routes.
        // A pool with no idle connections can still have leased connections; dropping it would reset
        // active permits and allow the active connection limit to be exceeded.
        pools.entrySet().removeIf(e -> e.getValue().isUnused());
        clearStaleCache();
        return totalRemoved;
    }

    /**
     * Close all pooled connections.
     */
    void closeAll(List<IOException> exceptions, Consumer<HttpConnection> onClose) {
        for (HostPool pool : pools.values()) {
            pool.closeAll(exceptions, onClose);
        }
        pools.clear();
        cachedRoute = null;
        cachedPool = null;
    }

    private void clearStaleCache() {
        Route currentRoute = cachedRoute;
        HostPool currentPool = cachedPool;
        if (currentRoute != null && currentPool != null && pools.get(currentRoute) != currentPool) {
            cachedRoute = null;
            cachedPool = null;
        }
    }

    private boolean validateConnection(PooledConnection pooled) {
        long idleNanos = System.nanoTime() - pooled.idleSinceNanos;
        if (idleNanos >= maxIdleTimeNanos) {
            return false;
        }

        if (!pooled.connection.isActive()) {
            return false;
        }

        if (idleNanos > VALIDATION_THRESHOLD_NANOS) {
            return pooled.connection.validateForReuse();
        }

        return true;
    }

    /**
     * A pooled connection with idle timestamp.
     */
    record PooledConnection(HttpConnection connection, long idleSinceNanos) {}

    /**
     * Per-route connection pool using a lock-protected LIFO stack.
     */
    private static final class HostPool {
        private final ArrayDeque<PooledConnection> available;
        private final IdentityHashMap<HttpConnection, Long> active = new IdentityHashMap<>();
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition activeReleased = lock.newCondition();
        private final int maxConnections;
        private final ActiveConnectionLimit activeLimit;
        private final ActiveConnectionLimit.State activeState;
        private int activeLeases;

        HostPool(int maxConnections, ActiveConnectionLimit activeLimit, int maxTotalConnections) {
            this.maxConnections = maxConnections;
            this.activeLimit = activeLimit;
            this.available = new ArrayDeque<>(maxConnections);
            this.activeState = activeLimit.newState(maxTotalConnections);
        }

        boolean tryAcquireActive(long acquireTimeoutMs) throws InterruptedException {
            long nanos = TimeUnit.MILLISECONDS.toNanos(acquireTimeoutMs);
            lock.lockInterruptibly();
            try {
                while (activeLeases >= activeState.limit()) {
                    if (nanos <= 0) {
                        return false;
                    }
                    nanos = activeReleased.awaitNanos(nanos);
                }
                activeLeases++;
                return true;
            } finally {
                lock.unlock();
            }
        }

        int activeLimit() {
            return activeState.limit();
        }

        void trackActive(HttpConnection connection, long leaseStartedNanos) {
            lock.lock();
            try {
                active.put(connection, leaseStartedNanos);
            } finally {
                lock.unlock();
            }
        }

        void releaseActive() {
            releaseActive(null);
        }

        void releaseActive(HttpConnection connection) {
            lock.lock();
            try {
                long leasedNanos = 0;
                if (connection != null) {
                    Long started = active.remove(connection);
                    if (started != null) {
                        leasedNanos = System.nanoTime() - started;
                    }
                }
                if (activeLeases > 0) {
                    int peakInflight = activeLeases;
                    activeLeases--;
                    if (leasedNanos > 0) {
                        activeState.onSample(leasedNanos, peakInflight);
                    }
                    activeReleased.signal();
                }
            } finally {
                lock.unlock();
            }
        }

        boolean isUnused() {
            lock.lock();
            try {
                return available.isEmpty() && activeLeases == 0;
            } finally {
                lock.unlock();
            }
        }

        PooledConnection poll() {
            lock.lock();
            try {
                return available.pollFirst();
            } finally {
                lock.unlock();
            }
        }

        boolean offer(PooledConnection connection) {
            lock.lock();
            try {
                if (available.size() >= maxConnections) {
                    return false;
                }
                available.offerFirst(connection);
                return true;
            } finally {
                lock.unlock();
            }
        }

        void remove(HttpConnection connection) {
            lock.lock();
            try {
                available.removeIf(pc -> pc.connection == connection);
            } finally {
                lock.unlock();
            }
        }

        int removeIdleConnections(long maxIdleNanos, BiConsumer<HttpConnection, CloseReason> onRemove) {
            int removed = 0;
            long now = System.nanoTime();
            lock.lock();
            try {
                Iterator<PooledConnection> iter = available.iterator();
                while (iter.hasNext()) {
                    PooledConnection pc = iter.next();
                    long idleNanos = now - pc.idleSinceNanos;
                    boolean unhealthy = !pc.connection.isActive();
                    boolean expired = idleNanos > maxIdleNanos;
                    if (unhealthy || expired) {
                        CloseReason reason = expired && !unhealthy
                                ? CloseReason.IDLE_TIMEOUT
                                : CloseReason.UNEXPECTED_CLOSE;
                        try {
                            pc.connection.close();
                        } catch (IOException ignored) {
                            // ignored
                        }
                        onRemove.accept(pc.connection, reason);
                        iter.remove();
                        removed++;
                    }
                }
            } finally {
                lock.unlock();
            }
            return removed;
        }

        void closeAll(List<IOException> exceptions, Consumer<HttpConnection> onClose) {
            lock.lock();
            try {
                PooledConnection pc;
                while ((pc = available.poll()) != null) {
                    try {
                        pc.connection.close();
                    } catch (IOException e) {
                        exceptions.add(e);
                    }
                    onClose.accept(pc.connection);
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
