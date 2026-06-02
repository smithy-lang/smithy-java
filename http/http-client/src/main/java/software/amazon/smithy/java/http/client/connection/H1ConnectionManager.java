/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.util.Arrays;
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
    HttpConnection tryAcquire(Route route, int maxConnections) {
        return tryAcquire(route, maxConnections, (connection, reason) -> {});
    }

    HttpConnection tryAcquire(
            Route route,
            int maxConnections,
            BiConsumer<HttpConnection, CloseReason> onInvalidClose
    ) {
        HostPool hostPool = getOrCreatePool(route, maxConnections);
        return hostPool.tryAcquireValid(route, maxIdleTimeNanos, onInvalidClose);
    }

    /**
     * Get or create a pool for the route.
     *
     * @param route the route
     * @param maxConnections max pooled connections for this route
     * @return the pool for the route
     * @throws IllegalStateException if a pool exists with a different maxConnections
     */
    HostPool getOrCreatePool(Route route, int maxConnections) {
        Route currentRoute = cachedRoute;
        HostPool currentPool = cachedPool;
        if (route.equals(currentRoute) && currentPool != null) {
            validatePoolConfig(route, currentPool, maxConnections);
            return currentPool;
        }

        return pools.compute(route, (k, existing) -> {
            if (existing == null) {
                existing = new HostPool(maxConnections);
            } else {
                validatePoolConfig(route, existing, maxConnections);
            }
            cachedRoute = route;
            cachedPool = existing;
            return existing;
        });
    }

    void acquireActive(Route route, int maxConnections, long acquireTimeoutMs) throws IOException {
        HostPool hostPool = getOrCreatePool(route, maxConnections);
        try {
            if (!hostPool.tryAcquireActive(acquireTimeoutMs)) {
                throw new IOException("Connection pool exhausted for route " + route
                        + ": " + maxConnections + " connections in use (timed out after "
                        + acquireTimeoutMs + "ms)");
            }
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

    private static void validatePoolConfig(Route route, HostPool pool, int maxConnections) {
        if (pool.maxConnections != maxConnections) {
            throw new IllegalStateException(
                    "Pool for " + route + " already exists with maxConnections=" + pool.maxConnections
                            + ", cannot change to " + maxConnections);
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
        HostPool hostPool = getCachedPool(route);
        if (hostPool == null) {
            hostPool = pools.get(route);
        }
        if (hostPool == null) {
            return false;
        }

        boolean pooled = hostPool.release(connection, poolClosed);
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
        // route permits and allow maxConnectionsPerRoute to be exceeded.
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

    private static CloseReason invalidReason(HttpConnection connection, long idleSinceNanos, long maxIdleTimeNanos) {
        long idleNanos = System.nanoTime() - idleSinceNanos;
        if (idleNanos >= maxIdleTimeNanos) {
            return CloseReason.IDLE_TIMEOUT;
        }

        if (!connection.isActive()) {
            return CloseReason.UNEXPECTED_CLOSE;
        }

        if (idleNanos > VALIDATION_THRESHOLD_NANOS) {
            return connection.validateForReuse() ? null : CloseReason.UNEXPECTED_CLOSE;
        }

        return null;
    }

    /**
     * Per-route connection pool using a lock-protected LIFO stack.
     */
    private static final class HostPool {
        private static final int INITIAL_IDLE_CAPACITY = 8;

        private HttpConnection[] available;
        private long[] idleSinceNanos;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition activeReleased = lock.newCondition();
        private final int maxConnections;
        private int availableCount;
        private int activeLeases;

        HostPool(int maxConnections) {
            this.maxConnections = maxConnections;
            int initialCapacity = Math.min(INITIAL_IDLE_CAPACITY, maxConnections);
            this.available = new HttpConnection[initialCapacity];
            this.idleSinceNanos = new long[initialCapacity];
        }

        boolean tryAcquireActive(long acquireTimeoutMs) throws InterruptedException {
            long nanos = TimeUnit.MILLISECONDS.toNanos(acquireTimeoutMs);
            lock.lockInterruptibly();
            try {
                while (activeLeases >= maxConnections) {
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

        void releaseActive() {
            lock.lock();
            try {
                releaseActiveLocked();
            } finally {
                lock.unlock();
            }
        }

        private void releaseActiveLocked() {
            if (activeLeases > 0) {
                activeLeases--;
                activeReleased.signal();
            }
        }

        boolean isUnused() {
            lock.lock();
            try {
                return availableCount == 0 && activeLeases == 0;
            } finally {
                lock.unlock();
            }
        }

        HttpConnection tryAcquireValid(
                Route route,
                long maxIdleTimeNanos,
                BiConsumer<HttpConnection, CloseReason> onInvalidClose
        ) {
            for (;;) {
                HttpConnection connection;
                long idleSince;
                lock.lock();
                try {
                    if (availableCount == 0) {
                        return null;
                    }
                    int index = --availableCount;
                    connection = available[index];
                    idleSince = idleSinceNanos[index];
                    available[index] = null;
                    idleSinceNanos[index] = 0;
                } finally {
                    lock.unlock();
                }

                CloseReason invalidReason = invalidReason(connection, idleSince, maxIdleTimeNanos);
                if (invalidReason == null) {
                    LOGGER.debug("Reusing pooled connection to {}", route);
                    return connection;
                }

                // Connection failed validation - close it and try next
                LOGGER.debug("Closing invalid pooled connection to {}", route);
                try {
                    connection.close();
                } catch (IOException e) {
                    LOGGER.debug("Error closing invalid connection to {}: {}", route, e.getMessage());
                }
                onInvalidClose.accept(connection, invalidReason);
            }
        }

        boolean release(HttpConnection connection, boolean poolClosed) {
            lock.lock();
            try {
                releaseActiveLocked();
                if (!connection.isActive() || poolClosed || availableCount >= maxConnections) {
                    return false;
                }
                ensureIdleCapacity();
                available[availableCount] = connection;
                idleSinceNanos[availableCount] = System.nanoTime();
                availableCount++;
                return true;
            } finally {
                lock.unlock();
            }
        }

        private void ensureIdleCapacity() {
            if (availableCount < available.length) {
                return;
            }

            int nextCapacity;
            if (available.length == 0) {
                nextCapacity = 1;
            } else {
                nextCapacity = Math.min(maxConnections, available.length * 2);
            }
            available = Arrays.copyOf(available, nextCapacity);
            idleSinceNanos = Arrays.copyOf(idleSinceNanos, nextCapacity);
        }

        void remove(HttpConnection connection) {
            lock.lock();
            try {
                for (int i = 0; i < availableCount; i++) {
                    if (available[i] == connection) {
                        removeAt(i);
                        return;
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        private void removeAt(int index) {
            int last = --availableCount;
            available[index] = available[last];
            idleSinceNanos[index] = idleSinceNanos[last];
            available[last] = null;
            idleSinceNanos[last] = 0;
        }

        int removeIdleConnections(long maxIdleNanos, BiConsumer<HttpConnection, CloseReason> onRemove) {
            int removed = 0;
            long now = System.nanoTime();
            lock.lock();
            try {
                for (int i = 0; i < availableCount;) {
                    HttpConnection connection = available[i];
                    long idleNanos = now - idleSinceNanos[i];
                    boolean unhealthy = !connection.isActive();
                    boolean expired = idleNanos > maxIdleNanos;
                    if (unhealthy || expired) {
                        CloseReason reason = expired && !unhealthy
                                ? CloseReason.IDLE_TIMEOUT
                                : CloseReason.UNEXPECTED_CLOSE;
                        try {
                            connection.close();
                        } catch (IOException ignored) {
                            // ignored
                        }
                        onRemove.accept(connection, reason);
                        removeAt(i);
                        removed++;
                    } else {
                        i++;
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
                while (availableCount > 0) {
                    int index = --availableCount;
                    HttpConnection connection = available[index];
                    available[index] = null;
                    idleSinceNanos[index] = 0;
                    try {
                        connection.close();
                    } catch (IOException e) {
                        exceptions.add(e);
                    }
                    onClose.accept(connection);
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
