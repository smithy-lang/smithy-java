/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.connection.Route;

/**
 * Benchmark-only route-aware pool for the codec-backed connection-agent H2C transport.
 *
 * <p>This mirrors the production pool shape more closely than the earlier single-host pool:
 * it is keyed by {@link Route}, grows connections per route on demand, and balances by
 * active + reserved stream slots.
 */
public final class ConnectionAgentH2cPool implements AutoCloseable {

    private static final class Entry {
        final ConnectionAgentH2cTransport connection;
        int reservedStreams;

        private Entry(ConnectionAgentH2cTransport connection) {
            this.connection = connection;
        }
    }

    private static final class RouteState {
        final ReentrantLock lock = new ReentrantLock();
        final Condition available = lock.newCondition();
        final List<Entry> connections = new ArrayList<>();
        int pendingCreations;
    }

    private final int maxConnectionsPerRoute;
    private final int maxStreamsPerConnection;
    private final long acquireTimeoutMs;
    private final ConcurrentHashMap<Route, RouteState> routes = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public ConnectionAgentH2cPool(int maxConnectionsPerRoute, int maxStreamsPerConnection, long acquireTimeoutMs) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
        this.maxStreamsPerConnection = maxStreamsPerConnection;
        this.acquireTimeoutMs = acquireTimeoutMs;
    }

    public HttpResponse send(HttpRequest request) throws IOException {
        Route route = Route.from(request.uri());
        Entry entry = acquire(route);
        try {
            return entry.connection.send(request);
        } catch (IOException e) {
            if (!entry.connection.isActive()) {
                invalidate(route, entry.connection);
            }
            throw e;
        }
    }

    private Entry acquire(Route route) throws IOException {
        RouteState state = routes.computeIfAbsent(route, ignored -> new RouteState());
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(acquireTimeoutMs);

        state.lock.lock();
        try {
            while (true) {
                if (closed) {
                    throw new IOException("Connection-agent pool is closed");
                }

                Entry selected = selectLeastLoaded(state);
                if (selected != null) {
                    selected.reservedStreams++;
                    return selected;
                }

                if (state.connections.size() + state.pendingCreations < maxConnectionsPerRoute) {
                    state.pendingCreations++;
                    break;
                }

                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) {
                    throw new IOException("Timed out waiting for route capacity after "
                            + acquireTimeoutMs + "ms for " + route);
                }
                try {
                    state.available.awaitNanos(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for route capacity for " + route, e);
                }
            }
        } finally {
            state.lock.unlock();
        }

        return createConnection(route, state);
    }

    private Entry selectLeastLoaded(RouteState state) {
        Entry best = null;
        int bestLoad = Integer.MAX_VALUE;
        for (Entry candidate : state.connections) {
            int activeLoad = candidate.connection.getActiveStreamCountIfAccepting();
            if (activeLoad < 0) {
                continue;
            }
            int load = activeLoad + candidate.reservedStreams;
            if (load >= maxStreamsPerConnection) {
                continue;
            }
            if (load < bestLoad) {
                best = candidate;
                bestLoad = load;
            }
        }
        return best;
    }

    private Entry createConnection(Route route, RouteState state) throws IOException {
        ConnectionAgentH2cTransport conn = null;
        Entry entry = null;
        IOException failure = null;
        try {
            conn = new ConnectionAgentH2cTransport(route);
            entry = new Entry(conn);
            entry.reservedStreams = 1;
            ConnectionAgentH2cTransport finalConn = conn;
            conn.setStreamReleaseCallback(() -> signalAvailable(route, finalConn));
        } catch (IOException e) {
            failure = e;
        } catch (Exception e) {
            failure = new IOException("Failed to create connection-agent H2C transport for " + route, e);
        } finally {
            state.lock.lock();
            try {
                state.pendingCreations--;
                if (entry != null && !closed) {
                    state.connections.add(entry);
                }
                state.available.signalAll();
            } finally {
                state.lock.unlock();
            }
        }

        if (failure != null) {
            throw failure;
        }
        if (closed) {
            conn.close();
            throw new IOException("Connection-agent pool closed during connection creation");
        }
        return entry;
    }

    private void signalAvailable(Route route, ConnectionAgentH2cTransport connection) {
        RouteState state = routes.get(route);
        if (state == null) {
            return;
        }
        state.lock.lock();
        try {
            Entry entry = findEntry(state, connection);
            if (entry == null) {
                state.available.signalAll();
                return;
            }
            if (entry.reservedStreams > 0) {
                entry.reservedStreams--;
            }
            if (!connection.isActive()) {
                state.connections.remove(entry);
            }
            state.available.signalAll();
        } finally {
            state.lock.unlock();
        }
    }

    private void invalidate(Route route, ConnectionAgentH2cTransport connection) {
        RouteState state = routes.get(route);
        if (state == null) {
            return;
        }
        state.lock.lock();
        try {
            Entry entry = findEntry(state, connection);
            if (entry != null) {
                state.connections.remove(entry);
            }
            state.available.signalAll();
        } finally {
            state.lock.unlock();
        }
    }

    private static Entry findEntry(RouteState state, ConnectionAgentH2cTransport connection) {
        for (Entry entry : state.connections) {
            if (entry.connection == connection) {
                return entry;
            }
        }
        return null;
    }

    @Override
    public void close() {
        closed = true;
        List<ConnectionAgentH2cTransport> snapshot = new ArrayList<>();
        for (RouteState state : routes.values()) {
            state.lock.lock();
            try {
                for (Entry entry : state.connections) {
                    snapshot.add(entry.connection);
                }
                state.connections.clear();
                state.available.signalAll();
            } finally {
                state.lock.unlock();
            }
        }
        routes.clear();
        for (ConnectionAgentH2cTransport connection : snapshot) {
            connection.close();
        }
    }
}
