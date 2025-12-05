/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.h2.H2Connection;

/**
 * HTTP connection pool optimized for virtual threads.
 *
 * <p>Manages connection lifecycle including:
 * <ul>
 *   <li>Connection creation with configured SSLContext and version policy</li>
 *   <li>Connection reuse via pooling (keyed by {@link Route})</li>
 *   <li>Health monitoring and stale connection cleanup</li>
 *   <li>DNS resolution with multi-IP failover</li>
 *   <li>Per-route connection limits with host-specific overrides</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe for concurrent access. Multiple virtual threads
 * can safely acquire and release connections simultaneously.
 *
 * <h2>Connection Pooling Strategy</h2>
 * <p>Connections are pooled by {@link Route}, which represents a unique
 * destination (scheme + host + port + proxy). Two requests to different paths
 * on the same host will share connections:
 *
 * <pre>{@code
 * Route route1 = Route.from(URI.create("https://api.example.com/users"));
 * Route route2 = Route.from(URI.create("https://api.example.com/posts"));
 * // route1.equals(route2) == true, so connections are shared
 * }</pre>
 *
 * <h2>Per-Route Connection Limits</h2>
 * <p>You can set different connection limits for different hosts:
 *
 * <pre>{@code
 * HttpConnectionPool pool = HttpConnectionPool.builder()
 *     .maxConnectionsPerRoute(20)  // Default for all routes
 *     .maxConnectionsForHost("slow-api.example.com", 2)  // Limit slow API
 *     .maxConnectionsForHost("fast-cdn.example.com", 100)  // Allow more for CDN
 *     .build();
 * }</pre>
 *
 * <h2>Health Monitoring</h2>
 * <p>A background virtual thread runs every 30 seconds to remove idle and
 * unhealthy connections from the pool. Connections are considered stale if:
 * <ul>
 *   <li>They've been idle longer than {@code maxIdleTime}</li>
 *   <li>The underlying socket is closed</li>
 *   <li>{@link HttpConnection#isActive()} returns false</li>
 * </ul>
 *
 * <h2>DNS Resolution and Failover</h2>
 * <p>When creating new connections, the pool resolves hostnames to IP addresses
 * using the configured {@link DnsResolver}. If resolution returns multiple IPs,
 * the pool attempts to connect to each one until successful:
 *
 * <pre>{@code
 * // api.example.com resolves to [203.0.113.1, 203.0.113.2]
 * // If connection to .1 fails, automatically tries .2
 * HttpConnection conn = pool.acquire(route);
 * }</pre>
 *
 * <h2>Pool Exhaustion and Backpressure</h2>
 * <p>When the pool reaches {@code maxTotalConnections}, {@link #acquire(Route)}
 * blocks for up to {@code acquireTimeout} (default: 30 seconds) waiting for a
 * connection permit to become available. This behavior is consistent for both
 * HTTP/1.1 and HTTP/2 connections.
 *
 * <p>The blocking wait is on the global connection semaphore, so any connection
 * release from any route can unblock waiting callers. With virtual threads,
 * this blocking is cheap and provides natural backpressure under load.
 *
 * <p>Configure via {@link HttpConnectionPoolBuilder#acquireTimeout(Duration)}:
 * <ul>
 *   <li>Default (30s): Good backpressure for load spikes, requests queue briefly</li>
 *   <li>{@link Duration#ZERO}: Fail-fast behavior, immediate failure when exhausted</li>
 *   <li>Longer timeout: More tolerance for sustained high load</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create pool
 * HttpConnectionPool pool = HttpConnectionPool.builder()
 *     .maxConnectionsPerRoute(20)
 *     .maxTotalConnections(200)
 *     .maxIdleTime(Duration.ofMinutes(2))
 *     .sslContext(customSSLContext)
 *     .httpVersionPolicy(HttpVersionPolicy.AUTOMATIC)
 *     .build();
 *
 * // Acquire connection
 * Route route = Route.from(URI.create("https://api.example.com/users"));
 * HttpConnection conn = pool.acquire(route);
 *
 * try {
 *     // Use connection
 *     HttpExchange exchange = conn.newExchange(request);
 *     // ...
 * } finally {
 *     // Return to pool for reuse
 *     pool.release(conn, route);
 * }
 *
 * // Cleanup
 * pool.close();
 * }</pre>
 *
 * @see Route
 * @see HttpConnection
 * @see HttpVersionPolicy
 */
public final class HttpConnectionPool implements ConnectionPool {
    // Target streams per connection before creating a new one.
    // Lower = more connections, better throughput under contention
    // Higher = fewer connections, better multiplexing efficiency
    private static final int STREAMS_PER_CONNECTION = 50;

    private final int defaultMaxConnectionsPerRoute;
    private final Map<String, Integer> perHostLimits;
    private final int maxTotalConnections;
    private final long maxIdleTimeNanos; // Cached to avoid Duration.toNanos() in hot path
    private final long acquireTimeoutMs; // Timeout for acquiring a connection when pool is exhausted
    private final HttpVersionPolicy versionPolicy;
    private final HttpConnectionFactory connectionFactory;

    // HTTP/1.1 connection manager (handles pooling)
    private final H1ConnectionManager h1Manager;

    // HTTP/2 connection manager (handles multiplexing)
    private final H2ConnectionManager h2Manager = new H2ConnectionManager(STREAMS_PER_CONNECTION);

    // Semaphore to limit total connections - better contention than AtomicInteger CAS loop
    private final Semaphore connectionPermits;

    // Cleanup thread
    private final Thread cleanupThread;
    private volatile boolean closed = false;

    // Listeners for pool lifecycle events
    private final List<ConnectionPoolListener> listeners;

    HttpConnectionPool(HttpConnectionPoolBuilder builder) {
        this.defaultMaxConnectionsPerRoute = builder.maxConnectionsPerRoute;
        this.perHostLimits = Map.copyOf(builder.perHostLimits);
        this.maxTotalConnections = builder.maxTotalConnections;
        this.maxIdleTimeNanos = builder.maxIdleTime.toNanos();
        this.acquireTimeoutMs = builder.acquireTimeout.toMillis();
        this.versionPolicy = builder.versionPolicy;
        DnsResolver dnsResolver = builder.dnsResolver != null ? builder.dnsResolver : DnsResolver.system();
        SSLContext sslContext = builder.sslContext;

        if (sslContext == null) {
            try {
                sslContext = SSLContext.getDefault();
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("No default SSLContext available", e);
            }
        }

        this.connectionFactory = new HttpConnectionFactory(
                builder.connectTimeout,
                builder.tlsNegotiationTimeout,
                builder.readTimeout,
                builder.writeTimeout,
                sslContext,
                builder.sslParameters,
                builder.versionPolicy,
                dnsResolver,
                builder.socketFactory);

        this.h1Manager = new H1ConnectionManager(maxIdleTimeNanos);
        this.connectionPermits = new Semaphore(builder.maxTotalConnections, false);
        this.listeners = List.copyOf(builder.listeners);
        this.cleanupThread = Thread.ofVirtual().name("http-pool-cleanup").start(this::cleanupIdleConnections);
    }

    /**
     * Create a new builder for HttpConnectionPool.
     *
     * @return a new builder instance
     */
    public static HttpConnectionPoolBuilder builder() {
        return new HttpConnectionPoolBuilder();
    }

    @Override
    public HttpConnection acquire(Route route) throws IOException {
        if (closed) {
            throw new IllegalStateException("Connection pool is closed");
        } else if ((route.isSecure() && versionPolicy != HttpVersionPolicy.ENFORCE_HTTP_1_1)
                || (!route.isSecure() && versionPolicy.usesH2cForCleartext())) {
            return acquireH2(route);
        } else {
            return acquireH1(route);
        }
    }

    private HttpConnection acquireH1(Route route) throws IOException {
        int maxConns = getMaxConnectionsForRoute(route);

        // Quick check: try to reuse a pooled connection
        H1ConnectionManager.PooledConnection pooled = h1Manager.tryAcquire(
                route,
                ignored -> new H1ConnectionManager.HostPool(maxConns));

        if (pooled != null) {
            notifyAcquire(pooled.connection(), true);
            return pooled.connection();
        }

        // No valid pooled connection available, so block on global capacity with timeout.
        acquirePermit();

        // Create new HTTP/1.1 connection
        try {
            HttpConnection conn = connectionFactory.create(route);
            notifyConnected(conn);
            notifyAcquire(conn, false);
            return conn;
        } catch (IOException | RuntimeException e) {
            connectionPermits.release();
            throw e;
        }
    }

    private HttpConnection acquireH2(Route route) throws IOException {
        // Fast path: find an existing H2 connection with capacity
        H2Connection reusable = h2Manager.tryAcquire(route);
        if (reusable != null) {
            notifyAcquire(reusable, true);
            return reusable;
        }

        // Slow path: need to create a new H2 connection.
        return h2Manager.newConnection(route, r -> {
            // Double-check: another thread might have created while we waited.
            H2Connection rechecked = h2Manager.tryAcquire(r);
            if (rechecked != null) {
                notifyAcquire(rechecked, true);
                return rechecked;
            }

            // Clean up dead or unhealthy connections
            h2Manager.cleanupDead(r, this::closeAndReleasePermit);

            // Create new H2 connection - block on global capacity with timeout
            acquirePermit();

            HttpConnection conn = null;
            try {
                conn = connectionFactory.create(r);
                notifyConnected(conn);
                if (conn instanceof H2Connection newH2conn) {
                    h2Manager.register(r, newH2conn);
                    notifyAcquire(newH2conn, false);
                    return newH2conn;
                } else {
                    // ALPN negotiated HTTP/1.1 instead of H2.
                    h1Manager.ensurePool(r, getMaxConnectionsForRoute(r));
                    notifyAcquire(conn, false);
                    return conn;
                }
            } catch (IOException | RuntimeException e) {
                if (conn != null) {
                    closeConnection(conn);
                }
                connectionPermits.release();
                throw e;
            }
        });
    }

    /**
     * Acquire a connection permit, blocking up to acquireTimeout.
     */
    private void acquirePermit() throws IOException {
        try {
            if (!connectionPermits.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IOException("Connection pool exhausted: " + maxTotalConnections +
                        " connections in use (timed out after " + acquireTimeoutMs + "ms)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for connection", e);
        }
    }

    @Override
    public void release(HttpConnection connection) {
        Objects.requireNonNull(connection, "connection cannot be null");
        Route route = connection.route();

        notifyReturn(connection);

        // H2 connections stay active for multiplexing - don't pool them
        if (connection instanceof H2Connection h2conn) {
            if (!connection.isActive() || closed) {
                h2Manager.unregister(route, h2conn);
                closeAndReleasePermit(connection, CloseReason.UNEXPECTED_CLOSE);
            }
            return;
        }

        // H1 connection handling
        if (!h1Manager.release(route, connection, closed)) {
            closeAndReleasePermit(connection, CloseReason.POOL_FULL);
        }
    }

    @Override
    public void evict(HttpConnection connection, boolean isError) {
        Objects.requireNonNull(connection, "connection cannot be null");
        Route route = connection.route();

        if (connection instanceof H2Connection h2conn) {
            h2Manager.unregister(route, h2conn);
        } else {
            h1Manager.remove(route, connection);
        }

        closeAndReleasePermit(connection, isError ? CloseReason.ERRORED : CloseReason.EVICTED);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        closed = true;
        cleanupThread.interrupt();

        List<IOException> exceptions = new ArrayList<>();

        // Close active H2 connections
        h2Manager.closeAll((conn, reason) -> {
            try {
                conn.close();
            } catch (IOException e) {
                exceptions.add(e);
            }
            notifyClosed(conn, CloseReason.POOL_SHUTDOWN);
        });

        // Close pooled H1 connections
        h1Manager.closeAll(exceptions, conn -> notifyClosed(conn, CloseReason.POOL_SHUTDOWN));

        if (!exceptions.isEmpty()) {
            IOException e = new IOException("Errors closing connections");
            exceptions.forEach(e::addSuppressed);
            throw e;
        }
    }

    @Override
    public void shutdown(Duration gracePeriod) throws IOException {
        Objects.requireNonNull(gracePeriod, "gracePeriod cannot be null");

        if (closed) {
            return;
        }

        closed = true; // Stop new acquires
        cleanupThread.interrupt();

        // Wait for connections to be closed (permits represent physical connections, not streams).
        // For HTTP/2, permits are released when the connection closes, not when streams finish.
        Instant deadline = Instant.now().plus(gracePeriod);
        while (connectionPermits.availablePermits() < maxTotalConnections
                && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Force close remaining
        close();
    }

    /**
     * Get max connections for a specific route.
     *
     * <p>Checks host-specific limits configured via
     * {@link HttpConnectionPoolBuilder#maxConnectionsForHost(String, int)}, falling back to
     * the default limit if no specific limit is configured.
     *
     * <p>Host matching is case-insensitive and supports:
     * <ul>
     *   <li>Hostname only: "api.example.com" (matches default ports 80/443)</li>
     *   <li>Hostname with port: "api.example.com:8080" (matches only port 8080)</li>
     * </ul>
     *
     * @param route the route to get limit for
     * @return maximum connections for this route
     */
    private int getMaxConnectionsForRoute(Route route) {
        // common case: no custom per-host limits configured
        if (perHostLimits.isEmpty()) {
            return defaultMaxConnectionsPerRoute;
        }

        String host = route.host();

        // Check with port first (more specific)
        if (route.port() != 80 && route.port() != 443) {
            String hostWithPort = host + ":" + route.port();
            Integer limit = perHostLimits.get(hostWithPort);
            if (limit != null) {
                return limit;
            }
        }

        // Check without port (less specific)
        Integer limit = perHostLimits.get(host);
        if (limit != null) {
            return limit;
        }

        // Use default
        return defaultMaxConnectionsPerRoute;
    }

    /**
     * Close a connection, ignoring any IOException.
     *
     * @param connection the connection to close
     */
    private void closeConnection(HttpConnection connection) {
        try {
            connection.close();
        } catch (IOException ignored) {
            // ignored
        }
    }

    /**
     * Close a connection, notify listeners, and release its permit.
     */
    private void closeAndReleasePermit(HttpConnection connection, CloseReason reason) {
        closeConnection(connection);
        notifyClosed(connection, reason);
        connectionPermits.release();
    }

    private void notifyConnected(HttpConnection connection) {
        for (ConnectionPoolListener listener : listeners) {
            listener.onConnected(connection);
        }
    }

    private void notifyAcquire(HttpConnection connection, boolean reused) {
        for (ConnectionPoolListener listener : listeners) {
            listener.onAcquire(connection, reused);
        }
    }

    private void notifyReturn(HttpConnection connection) {
        for (ConnectionPoolListener listener : listeners) {
            listener.onReturn(connection);
        }
    }

    private void notifyClosed(HttpConnection connection, CloseReason reason) {
        for (ConnectionPoolListener listener : listeners) {
            listener.onClosed(connection, reason);
        }
    }

    /**
     * Background cleanup task that runs every 30 seconds.
     *
     * <p>For HTTP/1.1 connections, removes connections that:
     * <ul>
     *   <li>Have been idle longer than {@code maxIdleTime}</li>
     *   <li>Are no longer active ({@link HttpConnection#isActive()} is false)</li>
     * </ul>
     *
     * <p>For HTTP/2 connections, removes connections that:
     * <ul>
     *   <li>Are no longer active or can't accept more streams</li>
     * </ul>
     *
     * <p>Note: {@code maxIdleTime} currently only applies to HTTP/1.1 connections.
     * HTTP/2 connections remain open until they become unhealthy.
     *
     * <p>Runs on a virtual thread, so blocking is cheap.
     */
    private void cleanupIdleConnections() {
        while (!closed) {
            try {
                Thread.sleep(Duration.ofSeconds(30));

                // Clean up HTTP/1.1 connections
                int removed = h1Manager.cleanupIdle(this::notifyClosed);
                if (removed > 0) {
                    connectionPermits.release(removed);
                }

                // Clean up unhealthy HTTP/2 connections
                h2Manager.cleanupAllDead(this::closeAndReleasePermit);

            } catch (InterruptedException e) {
                break;
            }
        }
    }
}
