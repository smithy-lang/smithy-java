/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

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

    H2ConnectionManager(int streamPerConnection) {
        this.streamsPerConnection = streamPerConnection;
    }

    /**
     * Find a reusable connection for the route, or null if none available.
     *
     * <p>Prefers connections with low stream count to spread load.
     */
    H2Connection tryAcquire(Route route) {
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
     * Execute an action while holding the lock for this route to make a new connection.
     * Prevents duplicate connection creation to the same route.
     */
    <T, E extends Exception> T newConnection(Route route, ThrowingFunction<T, E> action) throws E {
        synchronized (locks.computeIfAbsent(route, k -> new Object())) {
            return action.apply(route);
        }
    }

    @FunctionalInterface
    interface ThrowingFunction<T, E extends Exception> {
        T apply(Route route) throws E;
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
     *
     * @param route the route to clean up
     * @param onRemove callback for each removed connection (conn, reason)
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
     *
     * @param onClose callback for each connection
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
