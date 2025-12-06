/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.http.client.h2.H2Connection;

/**
 * Manages HTTP/2 connections for multiplexing.
 */
final class H2ConnectionManager {
    private final ConcurrentHashMap<Route, List<H2Connection>> connections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Route, Object> locks = new ConcurrentHashMap<>();
    private final int streamsPerConnection;
    private final List<ConnectionPoolListener> listeners;
    private final ConnectionFactory connectionFactory;

    @FunctionalInterface
    interface ConnectionFactory {
        H2Connection create(Route route) throws IOException;
    }

    H2ConnectionManager(int streamsPerConnection, List<ConnectionPoolListener> listeners, ConnectionFactory connectionFactory) {
        this.streamsPerConnection = streamsPerConnection;
        this.listeners = listeners;
        this.connectionFactory = connectionFactory;
    }

    /**
     * Acquire an H2 connection for the route, creating one if needed.
     */
    H2Connection acquire(Route route) throws IOException {
        // Fast path: find existing connection with capacity
        H2Connection conn = tryAcquire(route);
        if (conn != null) {
            notifyAcquire(conn, true);
            return conn;
        }

        // Slow path: need new connection, serialize per route
        synchronized (locks.computeIfAbsent(route, k -> new Object())) {
            // Double-check with strict limit
            H2Connection rechecked = tryAcquireUnderLimit(route);
            if (rechecked != null) {
                notifyAcquire(rechecked, true);
                return rechecked;
            }

            // Create new connection
            System.out.println("Making new h2 connection");
            H2Connection newConn = connectionFactory.create(route);
            register(route, newConn);
            notifyAcquire(newConn, false);
            return newConn;
        }
    }

    private void notifyAcquire(H2Connection conn, boolean reused) {
        for (ConnectionPoolListener listener : listeners) {
            listener.onAcquire(conn, reused);
        }
    }

    /**
     * Find a reusable connection for the route, or null if none available.
     *
     * <p>Prefers connections with low stream count to spread load.
     */
    private H2Connection tryAcquire(Route route) {
        List<H2Connection> conns = connections.get(route);
        if (conns == null) {
            return null;
        }

        // Prefer low stream count (spreads load)
        for (H2Connection conn : conns) {
            if (conn.canAcceptMoreStreams()
                    && conn.getActiveStreamCount() < streamsPerConnection
                    && conn.validateForReuse()) {
                return conn;
            }
        }

        // Fall back to any available
        for (H2Connection conn : conns) {
            if (conn.canAcceptMoreStreams() && conn.validateForReuse()) {
                return conn;
            }
        }

        return null;
    }

    /**
     * Find a connection under the soft limit, or null.
     */
    private H2Connection tryAcquireUnderLimit(Route route) {
        List<H2Connection> conns = connections.get(route);
        if (conns != null) {
            for (H2Connection conn : conns) {
                if (conn.canAcceptMoreStreams()
                        && conn.getActiveStreamCount() < streamsPerConnection
                        && conn.validateForReuse()) {
                    return conn;
                }
            }
        }
        return null;
    }

    /**
     * Register a new connection for the route.
     */
    void register(Route route, H2Connection conn) {
        connections.computeIfAbsent(route, k -> new CopyOnWriteArrayList<>()).add(conn);
    }

    /**
     * Unregister a connection from the route.
     */
    void unregister(Route route, H2Connection conn) {
        List<H2Connection> conns = connections.get(route);
        if (conns != null) {
            conns.remove(conn);
        }
    }

    /**
     * Remove dead or exhausted connections for the route.
     */
    void cleanupDead(Route route, BiConsumer<H2Connection, CloseReason> onRemove) {
        List<H2Connection> conns = connections.get(route);
        if (conns != null) {
            conns.removeIf(conn -> {
                if (!conn.canAcceptMoreStreams() || !conn.isActive()) {
                    CloseReason reason = conn.isActive() ? CloseReason.EVICTED : CloseReason.UNEXPECTED_CLOSE;
                    onRemove.accept(conn, reason);
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Clean up dead connections for all routes.
     */
    void cleanupAllDead(BiConsumer<H2Connection, CloseReason> onRemove) {
        for (Route route : connections.keySet()) {
            cleanupDead(route, onRemove);
        }
    }

    /**
     * Close all connections.
     */
    void closeAll(BiConsumer<H2Connection, CloseReason> onClose) {
        for (List<H2Connection> conns : connections.values()) {
            for (H2Connection conn : conns) {
                onClose.accept(conn, CloseReason.POOL_SHUTDOWN);
            }
        }
        connections.clear();
    }
}
