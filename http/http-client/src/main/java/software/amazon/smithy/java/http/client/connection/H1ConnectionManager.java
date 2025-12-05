/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;
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

    H1ConnectionManager(long maxIdleTimeNanos) {
        this.maxIdleTimeNanos = maxIdleTimeNanos;
    }

    /**
     * Try to acquire a pooled connection for the route.
     *
     * @param route the route
     * @param poolFactory
     * @return a valid pooled connection, or null if none available
     */
    PooledConnection tryAcquire(Route route, IntFunction<HostPool> poolFactory) {
        HostPool hostPool = pools.computeIfAbsent(route, k -> poolFactory.apply(0));

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
     * Ensure a pool exists for the route.
     */
    void ensurePool(Route route, int maxConnections) {
        pools.computeIfAbsent(route, k -> new HostPool(maxConnections));
    }

    /**
     * Release a connection back to the pool.
     *
     * @return true if pooled, false if pool full or closed
     */
    boolean release(Route route, HttpConnection connection, boolean poolClosed) {
        if (!connection.isActive() || poolClosed) {
            LOGGER.debug("Not pooling inactive connection to {} (poolClosed={})", route, poolClosed);
            return false;
        }

        HostPool hostPool = pools.get(route);
        if (hostPool == null) {
            return false;
        }

        try {
            var conn = new PooledConnection(connection, System.nanoTime());
            boolean pooled = hostPool.offer(conn, 10, TimeUnit.MILLISECONDS);
            if (pooled) {
                LOGGER.debug("Released h1 connection to pool for {}", route);
            } else {
                LOGGER.debug("h1 pool full, not pooling connection to {}", route);
            }
            return pooled;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
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

    /**
     * Clean up idle and unhealthy connections.
     *
     * @param onRemove callback for each removed connection
     * @return total number of connections removed
     */
    int cleanupIdle(BiConsumer<HttpConnection, CloseReason> onRemove) {
        int totalRemoved = 0;
        for (HostPool pool : pools.values()) {
            totalRemoved += pool.removeIdleConnections(maxIdleTimeNanos, onRemove);
        }
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
     * Per-route connection pool using blocking deque (LIFO).
     */
    static final class HostPool {
        private final LinkedBlockingDeque<PooledConnection> available;

        HostPool(int maxConnections) {
            this.available = new LinkedBlockingDeque<>(maxConnections);
        }

        PooledConnection poll() {
            return available.pollFirst();
        }

        boolean offer(PooledConnection connection, long timeout, TimeUnit unit) throws InterruptedException {
            return available.offerFirst(connection, timeout, unit);
        }

        void remove(HttpConnection connection) {
            available.removeIf(pc -> pc.connection == connection);
        }

        int removeIdleConnections(long maxIdleNanos, BiConsumer<HttpConnection, CloseReason> onRemove) {
            int removed = 0;
            long now = System.nanoTime();
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
            return removed;
        }

        void closeAll(List<IOException> exceptions, Consumer<HttpConnection> onClose) {
            PooledConnection pc;
            while ((pc = available.poll()) != null) {
                try {
                    pc.connection.close();
                } catch (IOException e) {
                    exceptions.add(e);
                }
                onClose.accept(pc.connection);
            }
        }
    }
}
