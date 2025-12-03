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

/**
 * Manages HTTP/1.1 connection pooling.
 *
 * <p>Pools idle connections per route using LIFO queues. Connections are
 * validated before reuse and cleaned up when idle too long.
 */
final class H1ConnectionManager {

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
     * @param maxConnections function to get max connections for route (called lazily)
     * @return a valid pooled connection, or null if none available
     */
    PooledConnection tryAcquire(Route route, IntFunction<HostPool> poolFactory) {
        HostPool hostPool = pools.computeIfAbsent(route, k -> poolFactory.apply(0));

        PooledConnection pooled;
        while ((pooled = hostPool.poll()) != null) {
            if (validateConnection(pooled)) {
                return pooled;
            }
            // Connection failed validation - caller should close and release permit
            return new PooledConnection(pooled.connection, -1); // -1 signals invalid
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
            return false;
        }

        HostPool hostPool = pools.get(route);
        if (hostPool == null) {
            return false;
        }

        try {
            return hostPool.offer(
                    new PooledConnection(connection, System.nanoTime()),
                    10,
                    TimeUnit.MILLISECONDS);
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
    record PooledConnection(HttpConnection connection, long idleSinceNanos) {
        boolean isValid() {
            return idleSinceNanos >= 0;
        }
    }

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
                    } catch (IOException ignored) {}
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
