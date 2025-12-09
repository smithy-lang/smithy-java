/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.http.client.h2.H2Connection;

/**
 * Manages HTTP/2 connections for multiplexing.
 *
 * <p>Uses a per-route state object containing a lock and volatile connection array.
 * Fast path (acquire existing connection) requires no locking - just a volatile read
 * and array scan. Slow path (create new connection) synchronizes on per-route lock.
 */
final class H2ConnectionManager {

    /**
     * Per-route state: synchronize on this object for mutations, volatile array for lock-free reads.
     */
    private static final class RouteState {
        volatile H2Connection[] conns = new H2Connection[0];
        // Number of connections currently being created (to prevent over-creation)
        int pendingCreations = 0;
    }

    private static final H2Connection[] EMPTY = new H2Connection[0];

    private final ConcurrentHashMap<Route, RouteState> routes = new ConcurrentHashMap<>();
    private final int streamsPerConnection;
    private final long acquireTimeoutMs;
    private final List<ConnectionPoolListener> listeners;
    private final ConnectionFactory connectionFactory;

    @FunctionalInterface
    interface ConnectionFactory {
        H2Connection create(Route route) throws IOException;
    }

    H2ConnectionManager(
            int streamsPerConnection,
            long acquireTimeoutMs,
            List<ConnectionPoolListener> listeners,
            ConnectionFactory connectionFactory
    ) {
        this.streamsPerConnection = streamsPerConnection;
        this.acquireTimeoutMs = acquireTimeoutMs;
        this.listeners = listeners;
        this.connectionFactory = connectionFactory;
    }

    private RouteState stateFor(Route route) {
        return routes.computeIfAbsent(route, r -> new RouteState());
    }

    /**
     * Acquire an H2 connection for the route, creating one if needed.
     *
     * <p>Connection creation happens OUTSIDE the synchronized block to prevent deadlock.
     * The deadlock scenario: Thread A holds RouteState lock while creating a connection,
     * which blocks waiting for a permit. Thread B tries to release a permit but first
     * needs to unregister, which requires the RouteState lock held by A.
     */
    H2Connection acquire(Route route, int maxConnectionsForRoute) throws IOException {
        RouteState state = stateFor(route);
        long deadline = System.currentTimeMillis() + acquireTimeoutMs;
        boolean shouldCreate = false;

        synchronized (state) {
            while (true) {
                H2Connection[] snapshot = state.conns;
                int totalConns = snapshot.length + state.pendingCreations;

                // Try to find a connection under the soft limit
                H2Connection conn = tryAcquireUnderLimit(snapshot);
                if (conn != null) {
                    notifyAcquire(conn, true);
                    return conn;
                }

                // All connections at/above soft limit - create new if under connection limit
                if (totalConns < maxConnectionsForRoute) {
                    state.pendingCreations++;
                    shouldCreate = true;
                    break;
                }

                // At connection limit - use any connection even if over soft limit
                conn = tryAcquire(snapshot);
                if (conn != null) {
                    notifyAcquire(conn, true);
                    return conn;
                }

                // Wait for capacity
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    throw new IOException("Acquire timeout: no connection available after "
                            + acquireTimeoutMs + "ms for " + route);
                }

                try {
                    state.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for connection", e);
                }
            }
        }

        if (shouldCreate) {
            return createNewH2Connection(route, state);
        }
        throw new IllegalStateException("unreachable");
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
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
            synchronized (state) {
                state.pendingCreations--;
                if (newConn != null) {
                    H2Connection[] cur = state.conns;
                    H2Connection[] next = new H2Connection[cur.length + 1];
                    System.arraycopy(cur, 0, next, 0, cur.length);
                    next[cur.length] = newConn;
                    state.conns = next;
                }
                state.notifyAll(); // Wake waiters
            }
        }

        if (createException != null) {
            throw createException;
        }

        notifyAcquire(newConn, false);
        return newConn;
    }

    /**
     * Find a reusable connection, preferring low stream count.
     *
     * <p>Single pass: tracks best candidate (lowest active streams under limit),
     * falls back to any valid connection if none under limit.
     *
     * <p>Note: getActiveStreamCountIfAccepting() checks active state, write errors, and muxer capacity,
     * returning the stream count in a single call to avoid redundant atomic reads.
     * Socket health is monitored by the reader thread, so separate validateForReuse() is not
     * needed in this hot path.
     */
    private H2Connection tryAcquire(H2Connection[] conns) {
        H2Connection best = null;
        int bestActive = Integer.MAX_VALUE;

        for (H2Connection conn : conns) {
            if (conn == null) {
                continue;
            }

            int active = conn.getActiveStreamCountIfAccepting();
            if (active < 0) {
                continue;
            }

            if (active < streamsPerConnection) {
                // Prefer lowest active count
                if (active < bestActive) {
                    best = conn;
                    bestActive = active;
                    if (active == 0) {
                        break; // Can't do better than idle
                    }
                }
            } else if (best == null) {
                // Fallback: any valid connection
                best = conn;
            }
        }

        return best;
    }

    /**
     * Find a connection strictly under the soft limit.
     */
    private H2Connection tryAcquireUnderLimit(H2Connection[] conns) {
        for (H2Connection conn : conns) {
            if (conn == null) {
                continue;
            }
            // getActiveStreamCountIfAccepting() checks: active, writeError, muxer capacity
            // and returns the count in a single call to avoid redundant atomic reads
            int active = conn.getActiveStreamCountIfAccepting();
            if (active >= 0 && active < streamsPerConnection) {
                return conn;
            }
        }
        return null;
    }

    /**
     * Unregister a connection from the route.
     */
    void unregister(Route route, H2Connection conn) {
        RouteState state = routes.get(route);
        if (state == null) {
            return;
        }
        synchronized (state) {
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
                // Compact array: copy elements before and after removed connection
                H2Connection[] next = new H2Connection[n - 1];
                System.arraycopy(cur, 0, next, 0, idx);
                System.arraycopy(cur, idx + 1, next, idx, n - idx - 1);
                state.conns = next;
            }
            state.notifyAll(); // Wake threads waiting for capacity
        }
    }

    /**
     * Remove dead or exhausted connections for the route.
     */
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
        synchronized (state) {
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
            }
        }
    }

    /**
     * Clean up dead connections for all routes.
     */
    void cleanupAllDead(BiConsumer<H2Connection, CloseReason> onRemove) {
        for (Route route : routes.keySet()) {
            cleanupDead(route, onRemove);
        }
    }

    /**
     * Clean up idle connections that have no active streams and have been idle
     * longer than the specified timeout.
     *
     * @param maxIdleTimeNanos maximum idle time in nanoseconds
     * @param onRemove callback for removed connections
     * @return number of connections removed
     */
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    int cleanupIdle(long maxIdleTimeNanos, BiConsumer<H2Connection, CloseReason> onRemove) {
        int removed = 0;
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
            synchronized (state) {
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
                        removed++;
                    } else {
                        tmp[w++] = conn;
                    }
                }
                if (w != n) {
                    H2Connection[] next = new H2Connection[w];
                    System.arraycopy(tmp, 0, next, 0, w);
                    state.conns = next;
                }
            }
        }
        return removed;
    }

    /**
     * Close all connections.
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
