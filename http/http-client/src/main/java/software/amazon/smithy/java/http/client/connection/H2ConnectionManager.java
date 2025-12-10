/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.http.client.h2.H2Connection;

/**
 * Manages HTTP/2 connections with adaptive load balancing.
 *
 * <h2>Load Balancing Strategy</h2>
 * <p>Uses a two-tier "watermark" strategy to distribute streams across connections:
 *
 * <ol>
 *   <li><b>Green Zone</b> (streams &lt; soft limit): Uses atomic round-robin to distribute
 *       requests evenly. Each caller atomically increments an index to get a unique starting
 *       position, ensuring fair distribution even under high concurrency.</li>
 *   <li><b>Expansion</b>: When all connections exceed the soft limit, creates a new connection
 *       (if under the maximum). This prevents overloading a single TCP connection.</li>
 *   <li><b>Red Zone</b> (at max connections): Uses least-loaded selection to find the
 *       connection with the fewest active streams, up to the hard limit.</li>
 *   <li><b>Saturation</b>: When all connections are at the hard limit, callers wait
 *       for capacity with a configurable timeout.</li>
 * </ol>
 *
 * <h2>Threading</h2>
 * <p>Uses per-route state with a volatile connection array for lock-free reads in the
 * common case. Connection creation and removal synchronize on the per-route state object.
 * The round-robin index uses {@link AtomicInteger} for visibility and atomicity across
 * thousands of concurrent virtual threads.
 */
final class H2ConnectionManager {

    /**
     * Per-route connection state.
     */
    private static final class RouteState {
        /** Connections for this route. Volatile for lock-free reads. */
        volatile H2Connection[] conns = new H2Connection[0];

        /** Connections currently being created (prevents over-creation). Guarded by sync on this. */
        int pendingCreations = 0;

        /** Round-robin index for connection selection. Atomic for concurrent access. */
        final AtomicInteger nextIndex = new AtomicInteger(0);
    }

    private static final H2Connection[] EMPTY = new H2Connection[0];

    // Soft limit as a fraction of streamsPerConnection. When all connections exceed this threshold,
    // we try to create a new connection (if under max).
    // This prevents overloading a single TCP connection even when the server allows many streams.
    private static final int SOFT_LIMIT_DIVISOR = 8;
    private static final int SOFT_LIMIT_FLOOR = 50;

    private final ConcurrentHashMap<Route, RouteState> routes = new ConcurrentHashMap<>();
    private final int streamsPerConnection; // Hard limit from server
    private final int softConcurrencyLimit; // Soft limit for load balancing
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
        this.softConcurrencyLimit = Math.max(SOFT_LIMIT_FLOOR, streamsPerConnection / SOFT_LIMIT_DIVISOR);
        this.acquireTimeoutMs = acquireTimeoutMs;
        this.listeners = listeners;
        this.connectionFactory = connectionFactory;
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
     * @param route the target route
     * @param maxConnectionsForRoute maximum connections allowed for this route
     * @return an H2 connection ready for use
     * @throws IOException if acquisition times out or is interrupted
     */
    H2Connection acquire(Route route, int maxConnectionsForRoute) throws IOException {
        RouteState state = stateFor(route);
        long deadline = System.currentTimeMillis() + acquireTimeoutMs;

        synchronized (state) {
            while (true) {
                H2Connection[] snapshot = state.conns;
                int totalConns = snapshot.length + state.pendingCreations;

                // Green zone: round-robin among connections under soft limit
                H2Connection conn = tryAcquireRoundRobin(snapshot, state, softConcurrencyLimit);
                if (conn != null) {
                    notifyAcquire(conn, true);
                    return conn;
                }

                // Expansion: all connections above soft limit, create new if allowed
                if (totalConns < maxConnectionsForRoute) {
                    state.pendingCreations++;
                    break;
                }

                // Red zone: at max connections, use least-loaded up to hard limit
                conn = tryAcquireLeastLoaded(snapshot, streamsPerConnection);
                if (conn != null) {
                    notifyAcquire(conn, true);
                    return conn;
                }

                // Saturation: all connections at hard limit, wait for capacity
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

        return createNewH2Connection(route, state);
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
     * Round-robin selection: find a connection under the limit, starting from a unique index.
     *
     * <p>Each caller atomically claims a starting index, then scans connections from that
     * position. This ensures even distribution under high concurrency, each gets a different starting point rather
     * than all hammering connection[0].
     */
    private H2Connection tryAcquireRoundRobin(H2Connection[] conns, RouteState state, int limit) {
        int n = conns.length;
        if (n == 0) {
            return null;
        }

        // Mask with MAX_VALUE handles overflow when counter wraps to negative.
        int start = (state.nextIndex.getAndIncrement() & Integer.MAX_VALUE) % n;

        for (int i = 0; i < n; i++) {
            int idx = (start + i) % n;
            H2Connection conn = conns[idx];
            if (conn == null) {
                continue;
            }
            int active = conn.getActiveStreamCountIfAccepting();
            if (active >= 0 && active < limit) {
                return conn;
            }
        }
        return null;
    }

    /**
     * Least-loaded selection: find the connection with the fewest active streams.
     *
     * <p>Scans all connections to find the best candidate. Used in the red zone when all connections exceed the
     * soft limit and we need to balance the load.
     */
    private H2Connection tryAcquireLeastLoaded(H2Connection[] conns, int limit) {
        H2Connection best = null;
        int bestActive = Integer.MAX_VALUE;

        for (H2Connection conn : conns) {
            if (conn == null) {
                continue;
            }
            int active = conn.getActiveStreamCountIfAccepting();
            if (active >= 0 && active < limit && active < bestActive) {
                best = conn;
                bestActive = active;
                if (active == 0) {
                    break;
                }
            }
        }

        return best;
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
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
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
    }

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
