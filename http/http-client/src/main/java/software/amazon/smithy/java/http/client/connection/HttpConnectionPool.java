/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import software.amazon.smithy.java.http.client.ProxyConfiguration;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.h1.Http1Connection;
import software.amazon.smithy.java.http.client.h2.Http2Connection;

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
 * <p>Configure via {@link Builder#acquireTimeout(Duration)}:
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
    private final int defaultMaxConnectionsPerRoute;
    private final Map<String, Integer> perHostLimits;
    private final int maxTotalConnections;
    private final long maxIdleTimeNanos; // Cached to avoid Duration.toNanos() in hot path
    private final long acquireTimeoutMs; // Timeout for acquiring a connection when pool is exhausted
    private final Duration connectTimeout;
    private final Duration tlsNegotiationTimeout;
    private final Duration readTimeout;
    private final Duration writeTimeout;
    private final SSLContext sslContext;
    private final HttpVersionPolicy versionPolicy;
    private final DnsResolver dnsResolver;
    private final HttpSocketFactory socketFactory;

    // Pool structure: Route -> HostPool
    private final ConcurrentHashMap<Route, HostPool> pools = new ConcurrentHashMap<>();

    // Active H2 connections that can accept more streams (multiplexing).
    // We allow multiple connections per route to spread load when a single
    // connection becomes a bottleneck (e.g., lock contention with many concurrent streams).
    private final ConcurrentHashMap<Route, List<Http2Connection>> activeH2Connections = new ConcurrentHashMap<>();

    // Target streams per H2 connection before creating a new one.
    // Lower values spread load across more connections (better throughput), higher values maximize multiplexing
    // benefits (fewer connections).
    private static final int H2_STREAMS_PER_CONNECTION = 100;

    // Locks for H2 connection creation to prevent duplicate connections per route.
    // Using a lock per route allows concurrent connection creation to different routes.
    private final ConcurrentHashMap<Route, Object> h2ConnectionLocks = new ConcurrentHashMap<>();

    // Semaphore to limit total connections - better contention than AtomicInteger CAS loop
    private final Semaphore connectionPermits;

    // Cleanup thread
    private final Thread cleanupThread;
    private volatile boolean closed = false;

    // Listeners for pool lifecycle events
    private final List<ConnectionPoolListener> listeners;

    private HttpConnectionPool(Builder builder) {
        this.defaultMaxConnectionsPerRoute = builder.maxConnectionsPerRoute;
        this.perHostLimits = Map.copyOf(builder.perHostLimits);
        this.maxTotalConnections = builder.maxTotalConnections;
        this.maxIdleTimeNanos = builder.maxIdleTime.toNanos();
        this.acquireTimeoutMs = builder.acquireTimeout.toMillis();
        this.connectTimeout = builder.connectTimeout;
        this.tlsNegotiationTimeout = builder.tlsNegotiationTimeout;
        this.readTimeout = builder.readTimeout;
        this.writeTimeout = builder.writeTimeout;
        this.sslContext = builder.sslContext;
        this.versionPolicy = builder.versionPolicy;
        this.dnsResolver = builder.dnsResolver != null
                ? builder.dnsResolver
                : DnsResolver.caching(DnsResolver.system(), Duration.ofMinutes(1));
        this.socketFactory = builder.socketFactory;
        this.connectionPermits = new Semaphore(builder.maxTotalConnections, false);
        this.listeners = List.copyOf(builder.listeners);
        this.cleanupThread = Thread.ofVirtual().name("http-pool-cleanup").start(this::cleanupIdleConnections);
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
        HostPool hostPool = pools.computeIfAbsent(route, k -> new HostPool(getMaxConnectionsForRoute(route)));

        // Quick check: try to reuse a pooled connection
        PooledConnection pooled;
        while ((pooled = hostPool.poll()) != null) {
            if (validateConnection(pooled)) {
                notifyAcquire(pooled.connection, true);
                return pooled.connection;
            }
            // Connection failed validation: it's unhealthy or stale
            closeConnection(pooled.connection);
            connectionPermits.release();
        }

        // No valid pooled connection available, so block on global capacity with timeout.
        // This ensures any connection release (from any route) can help us make progress.
        acquirePermit();

        // Create new HTTP/1.1 connection
        try {
            HttpConnection conn = createNewConnection(route);
            notifyConnected(conn);
            notifyAcquire(conn, false);
            return conn;
        } catch (IOException | RuntimeException e) {
            // Catch RuntimeException too (e.g., UncheckedIOException from socketConfigurer)
            connectionPermits.release();
            throw e;
        }
    }

    private HttpConnection acquireH2(Route route) throws IOException {
        // Fast path: find an existing H2 connection with capacity
        Http2Connection reusable = findReusableH2Connection(route);
        if (reusable != null) {
            notifyAcquire(reusable, true);
            return reusable;
        }

        // Slow path: need to create a new H2 connection.
        // Synchronize per route to avoid duplicate creation.
        Object lock = h2ConnectionLocks.computeIfAbsent(route, k -> new Object());
        synchronized (lock) {
            // Double-check: another thread might have created while we waited.
            // Only check for LOW stream count connections - we intentionally prefer creating
            // new connections over piling onto busy ones (load spreading).
            List<Http2Connection> h2conns = activeH2Connections.get(route);
            if (h2conns != null) {
                for (Http2Connection h2conn : h2conns) {
                    if (h2conn.canAcceptMoreStreams()
                            && h2conn.getActiveStreamCount() < H2_STREAMS_PER_CONNECTION
                            && h2conn.validateForReuse()) {
                        notifyAcquire(h2conn, true);
                        return h2conn;
                    }
                }
            }

            // Clean up dead or unhealthy connections
            cleanupDeadH2Connections(route);

            // Create new H2 connection - block on global capacity with timeout
            acquirePermit();

            HttpConnection conn = null;
            try {
                conn = createNewConnection(route);
                notifyConnected(conn);
                if (conn instanceof Http2Connection newH2conn) {
                    activeH2Connections.computeIfAbsent(route, k -> new CopyOnWriteArrayList<>()).add(newH2conn);
                    notifyAcquire(newH2conn, false);
                    return newH2conn;
                } else {
                    // ALPN negotiated HTTP/1.1 instead of H2.
                    // Ensure HostPool exists so this connection can be pooled on release.
                    pools.computeIfAbsent(route, k -> new HostPool(getMaxConnectionsForRoute(route)));
                    notifyAcquire(conn, false);
                    return conn;
                }
            } catch (IOException | RuntimeException e) {
                // Catch RuntimeException too (e.g., UncheckedIOException from socketFactory)
                if (conn != null) {
                    closeConnection(conn);
                }
                connectionPermits.release();
                throw e;
            }
        }
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

    /**
     * Find a reusable H2 connection for the given route.
     *
     * <p>Prefers connections with low stream count to spread load, but will
     * fall back to any connection that can accept more streams.
     *
     * @return a reusable H2 connection, or null if none available
     */
    private Http2Connection findReusableH2Connection(Route route) {
        List<Http2Connection> h2conns = activeH2Connections.get(route);
        if (h2conns == null) {
            return null;
        }

        // Prefer low stream count (spreads load)
        for (Http2Connection h2conn : h2conns) {
            if (h2conn.canAcceptMoreStreams()
                    && h2conn.getActiveStreamCount() < H2_STREAMS_PER_CONNECTION
                    && h2conn.validateForReuse()) {
                return h2conn;
            }
        }

        // Fall back to any available
        for (Http2Connection h2conn : h2conns) {
            if (h2conn.canAcceptMoreStreams() && h2conn.validateForReuse()) {
                return h2conn;
            }
        }
        return null;
    }

    /**
     * Remove dead or exhausted H2 connections for the given route.
     */
    private void cleanupDeadH2Connections(Route route) {
        List<Http2Connection> h2conns = activeH2Connections.get(route);
        if (h2conns != null) {
            h2conns.removeIf(h2conn -> {
                if (!h2conn.canAcceptMoreStreams() || !h2conn.isActive()) {
                    var reason = h2conn.isActive() ? CloseReason.EVICTED : CloseReason.UNEXPECTED_CLOSE;
                    closeAndReleasePermit(h2conn, reason);
                    return true;
                }
                return false;
            });
        }
    }

    @Override
    public void release(HttpConnection connection) {
        Objects.requireNonNull(connection, "connection cannot be null");
        Route route = connection.route();

        notifyReturn(connection);

        // H2 connections stay active for multiplexing - don't pool them
        if (connection instanceof Http2Connection h2conn) {
            if (!connection.isActive() || closed) {
                // Connection is dead, remove from active list and close
                List<Http2Connection> h2conns = activeH2Connections.get(route);
                if (h2conns != null) {
                    h2conns.remove(h2conn);
                }
                closeAndReleasePermit(connection, CloseReason.UNEXPECTED_CLOSE);
            } else if (h2conn.getActiveStreamCount() == 0) {
                // No more active streams - could keep it for future streams
                // or close it. For now, keep it active until it becomes unhealthy.
                // The connection stays in activeH2Connections for future reuse.
            }
            // Otherwise, H2 connection has active streams - do nothing
            return;
        }

        // H1 connection handling (original logic)
        if (!connection.isActive() || closed) {
            closeAndReleasePermit(connection, CloseReason.UNEXPECTED_CLOSE);
            return;
        }

        HostPool hostPool = pools.get(route);
        if (hostPool == null) {
            closeAndReleasePermit(connection, CloseReason.POOL_FULL);
            return;
        }

        // Use timeout on offer to handle brief contention spikes.
        // If pool is still full after 10ms, close the connection.
        boolean added;
        try {
            added = hostPool.offer(
                    new PooledConnection(connection, System.nanoTime()),
                    10,
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            added = false;
        }

        if (!added) {
            // Pool full for this route
            closeAndReleasePermit(connection, CloseReason.POOL_FULL);
        }
    }

    @Override
    public void evict(HttpConnection connection, boolean isError) {
        Objects.requireNonNull(connection, "connection cannot be null");
        Route route = connection.route();

        // For H2 connections, remove from active list
        if (connection instanceof Http2Connection h2conn) {
            List<Http2Connection> h2conns = activeH2Connections.get(route);
            if (h2conns != null) {
                h2conns.remove(h2conn);
            }
        }

        closeAndReleasePermit(connection, isError ? CloseReason.ERRORED : CloseReason.EVICTED);

        HostPool hostPool = pools.get(route);
        if (hostPool != null) {
            hostPool.remove(connection);
        }
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
        for (List<Http2Connection> h2conns : activeH2Connections.values()) {
            for (Http2Connection h2conn : h2conns) {
                try {
                    h2conn.close();
                } catch (IOException e) {
                    exceptions.add(e);
                }
                notifyClosed(h2conn, CloseReason.POOL_SHUTDOWN);
            }
        }
        activeH2Connections.clear();

        for (HostPool pool : pools.values()) {
            pool.closeAll(exceptions, conn -> notifyClosed(conn, CloseReason.POOL_SHUTDOWN));
        }

        pools.clear();

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
     * {@link Builder#maxConnectionsForHost(String, int)}, falling back to
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

    // Skip expensive socket validation if connection was idle less than this
    private static final long VALIDATION_THRESHOLD_NANOS = 1_000_000_000L; // 1 second

    /**
     * Validate pooled connection is still usable.
     *
     * <p>A connection is considered valid if:
     * <ul>
     *   <li>{@link HttpConnection#isActive()} returns true (fast check)</li>
     *   <li>It hasn't been idle longer than {@code maxIdleTime}</li>
     *   <li>If idle > 1 second, {@link HttpConnection#validateForReuse()} passes</li>
     * </ul>
     *
     * <p>Optimization: connections idle < 1 second skip socket validation syscalls.
     * If connection was actually closed, first write will fail and caller handles retry.
     *
     * @param pooled the pooled connection to validate
     * @return true if connection is still usable
     */
    private boolean validateConnection(PooledConnection pooled) {
        long idleNanos = System.nanoTime() - pooled.idleSinceNanos;
        if (idleNanos >= maxIdleTimeNanos) {
            return false;
        }

        // Fast check first (just volatile reads)
        if (!pooled.connection.isActive()) {
            return false;
        }

        // Only do expensive socket validation if idle for a while
        // Server unlikely to close connection within 1 second of last use
        if (idleNanos > VALIDATION_THRESHOLD_NANOS) {
            return pooled.connection.validateForReuse();
        }

        return true;
    }

    /**
     * Create new connection with DNS resolution and multi-IP failover.
     *
     * <p>This method:
     * <ol>
     *   <li>Resolves hostname to one or more IP addresses</li>
     *   <li>Attempts to connect to each IP until successful</li>
     *   <li>For HTTPS, performs TLS handshake with ALPN negotiation</li>
     *   <li>Creates protocol-specific connection based on negotiated protocol</li>
     * </ol>
     *
     * <p>If all IPs fail, throws IOException with the last failure as cause.
     *
     * @param route the route to create connection for
     * @return a new connected HttpConnection
     * @throws IOException if connection fails to all resolved IPs
     */
    private HttpConnection createNewConnection(Route route) throws IOException {
        // For proxied routes, skip target DNS resolution - we connect to the proxy instead.
        // The proxy will resolve the target hostname.
        if (route.usesProxy()) {
            return connectViaProxy(route);
        }

        // Resolve DNS - may return multiple IPs
        List<InetAddress> addresses = dnsResolver.resolve(route.host());

        if (addresses.isEmpty()) {
            throw new IOException("DNS resolution failed: no addresses for " + route.host());
        }

        // Try each IP until one succeeds
        IOException lastException = null;
        for (InetAddress address : addresses) {
            try {
                return connectToAddress(address, route, addresses);
            } catch (IOException e) {
                lastException = e;
                // Report failure so this IP is deprioritized for future requests
                dnsResolver.reportFailure(address);
            }
        }

        // All IPs failed
        throw new IOException(
                "Failed to connect to " + route.host() + " on any resolved IP (" + addresses.size() + " tried)",
                lastException);
    }

    /**
     * Connect to a specific IP address for the given route.
     *
     * <p>This method is only called for direct (non-proxied) connections.
     * Proxied routes are handled by {@link #connectViaProxy(Route)}.
     *
     * @param address the IP address to connect to
     * @param route the route being connected to
     * @param allEndpoints all resolved endpoints for this route (for socket factory)
     * @return a new connected HttpConnection
     * @throws IOException if connection or TLS handshake fails
     */
    private HttpConnection connectToAddress(InetAddress address, Route route, List<InetAddress> allEndpoints)
            throws IOException {
        Socket socket = socketFactory.newSocket(route, allEndpoints);

        try {
            socket.connect(new InetSocketAddress(address, route.port()), (int) connectTimeout.toMillis());
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {}
            throw e;
        }

        // TLS negotiation for HTTPS
        if (route.isSecure()) {
            socket = performTlsHandshake(socket, route);
        }

        // Create protocol-specific connection based on negotiated protocol
        return createProtocolConnection(socket, route);
    }

    /**
     * Perform TLS handshake and ALPN negotiation.
     *
     * <p>This method:
     * <ol>
     *   <li>Wraps the socket in an SSLSocket</li>
     *   <li>Configures ALPN protocols based on {@link HttpVersionPolicy}</li>
     *   <li>Enables hostname verification</li>
     *   <li>Performs the TLS handshake</li>
     * </ol>
     *
     * <p>The ALPN protocols offered depend on version policy:
     * <ul>
     *   <li>ENFORCE_HTTP_1_1: ["http/1.1"]</li>
     *   <li>ENFORCE_HTTP_2: ["h2"]</li>
     *   <li>AUTOMATIC: ["h2", "http/1.1"]</li>
     * </ul>
     *
     * @param socket the connected TCP socket
     * @param route the route being connected to
     * @return SSLSocket after successful handshake
     * @throws IOException if TLS handshake fails or times out
     */
    private Socket performTlsHandshake(Socket socket, Route route) throws IOException {
        try {
            SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(socket, route.host(), route.port(), true);

            SSLParameters params = sslSocket.getSSLParameters();

            // Enable hostname verification
            params.setEndpointIdentificationAlgorithm("HTTPS");

            // Set ALPN based on version policy
            params.setApplicationProtocols(versionPolicy.alpnProtocols());

            sslSocket.setSSLParameters(params);

            // Perform handshake with timeout, then restore original timeout
            // so higher layers can manage read timeouts independently
            int originalTimeout = sslSocket.getSoTimeout();
            sslSocket.setSoTimeout((int) tlsNegotiationTimeout.toMillis());
            try {
                sslSocket.startHandshake();
            } finally {
                sslSocket.setSoTimeout(originalTimeout);
            }

            return sslSocket;

        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {}
            throw new IOException("TLS handshake failed for " + route.host(), e);
        }
    }

    /**
     * Create appropriate connection type based on negotiated protocol.
     *
     * <p>For HTTPS connections, inspects the ALPN negotiation result to determine
     * whether to create an HTTP/1.1 or HTTP/2 connection.
     *
     * <p>For HTTP connections:
     * <ul>
     *   <li>If {@link HttpVersionPolicy#H2C_PRIOR_KNOWLEDGE}, creates HTTP/2 connection (h2c)</li>
     *   <li>Otherwise, creates HTTP/1.1 connection</li>
     * </ul>
     *
     * @param socket the connected socket (may be SSLSocket)
     * @param route the route this connection is for
     * @return protocol-specific HttpConnection
     * @throws IOException if connection creation fails
     */
    private HttpConnection createProtocolConnection(Socket socket, Route route) throws IOException {
        String protocol = "http/1.1";

        if (socket instanceof SSLSocket sslSocket) {
            // TLS connection - check ALPN negotiation result
            String negotiated = sslSocket.getApplicationProtocol();
            if (negotiated != null && !negotiated.isEmpty()) {
                protocol = negotiated;
            }
        } else if (versionPolicy.usesH2cForCleartext()) {
            // Cleartext with h2c prior knowledge - use HTTP/2 directly
            protocol = "h2c";
        }

        try {
            if ("h2".equals(protocol) || "h2c".equals(protocol)) {
                return new Http2Connection(socket, route, readTimeout, writeTimeout);
            } else {
                return new Http1Connection(socket, route, readTimeout);
            }
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {}
            throw e;
        }
    }

    /**
     * Connect through HTTP or HTTPS proxy.
     *
     * <p>For HTTPS target through HTTP proxy:
     * <ol>
     *   <li>Resolve proxy hostname to IP addresses</li>
     *   <li>Connect to proxy host:port (plain TCP)</li>
     *   <li>Send CONNECT request to establish tunnel</li>
     *   <li>Read 200 Connection Established response</li>
     *   <li>Perform TLS handshake to target through tunnel</li>
     * </ol>
     *
     * <p>For HTTPS target through HTTPS proxy (double TLS):
     * <ol>
     *   <li>Resolve proxy hostname to IP addresses</li>
     *   <li>Connect to proxy host:port (plain TCP)</li>
     *   <li>Perform TLS handshake to proxy</li>
     *   <li>Send CONNECT request through encrypted connection</li>
     *   <li>Read 200 Connection Established response</li>
     *   <li>Perform TLS handshake to target through tunnel</li>
     * </ol>
     *
     * <p>For HTTP target through proxy:
     * <ol>
     *   <li>Resolve proxy hostname to IP addresses</li>
     *   <li>Connect to proxy host:port</li>
     *   <li>For HTTPS proxy: perform TLS handshake to proxy</li>
     *   <li>Return connection directly (requests will use absolute URIs)</li>
     * </ol>
     *
     * <p>SOCKS proxies are not yet supported.
     *
     * @param route the route including proxy configuration
     * @return a connection through the proxy
     * @throws IOException if proxy connection fails
     */
    private HttpConnection connectViaProxy(Route route) throws IOException {
        ProxyConfiguration proxy = route.proxy();

        // Validate proxy type
        if (proxy.type() == ProxyConfiguration.ProxyType.SOCKS4
                || proxy.type() == ProxyConfiguration.ProxyType.SOCKS5) {
            throw new UnsupportedOperationException(
                    "SOCKS proxies not yet supported: " + proxy.type());
        }

        // Resolve proxy hostname (not target hostname)
        List<InetAddress> proxyAddresses = dnsResolver.resolve(proxy.hostname());

        if (proxyAddresses.isEmpty()) {
            throw new IOException("DNS resolution failed for proxy: " + proxy.hostname());
        }

        // Try each proxy IP until one succeeds
        IOException lastException = null;
        for (InetAddress proxyAddress : proxyAddresses) {
            try {
                return connectToProxy(proxyAddress, route, proxy, proxyAddresses);
            } catch (IOException e) {
                lastException = e;
                // Report failure so this proxy IP is deprioritized for future requests
                dnsResolver.reportFailure(proxyAddress);
            }
        }

        // All proxy IPs failed
        throw new IOException(
                "Failed to connect to proxy " + proxy.hostname() + " on any resolved IP (" +
                        proxyAddresses.size() + " tried)",
                lastException);
    }

    /**
     * Connect to a specific proxy IP address and establish tunnel if needed.
     *
     * <p>Supports both HTTP and HTTPS proxies:
     * <ul>
     *   <li><b>HTTP proxy:</b> Connect with plain TCP, then optionally CONNECT tunnel for HTTPS targets</li>
     *   <li><b>HTTPS proxy:</b> Connect with TLS to proxy, then optionally CONNECT tunnel for HTTPS targets</li>
     * </ul>
     *
     * @param proxyAddress the proxy IP address to connect to
     * @param route the route being connected to (contains target host info)
     * @param proxy the proxy configuration
     * @param allProxyEndpoints all resolved endpoints for the proxy (for socket factory)
     * @return a connection through the proxy
     * @throws IOException if connection fails
     */
    private HttpConnection connectToProxy(
            InetAddress proxyAddress,
            Route route,
            ProxyConfiguration proxy,
            List<InetAddress> allProxyEndpoints
    ) throws IOException {
        Socket proxySocket = socketFactory.newSocket(route, allProxyEndpoints);

        try {
            // Connect to proxy
            proxySocket.connect(
                    new InetSocketAddress(proxyAddress, proxy.port()),
                    (int) connectTimeout.toMillis());

            // For HTTPS proxy, establish TLS connection to the proxy itself first
            if (proxy.type() == ProxyConfiguration.ProxyType.HTTPS) {
                proxySocket = performTlsHandshakeToProxy(proxySocket, proxy);
            }

            // For HTTPS target, establish CONNECT tunnel through proxy
            if (route.isSecure()) {
                // Establish tunnel to target through proxy (over plain or TLS depending on proxy type)
                Http1Connection.establishConnectTunnel(proxySocket, route.host(), route.port(), proxy);
                // Perform TLS handshake to target through the tunnel
                proxySocket = performTlsHandshake(proxySocket, route);
            }

            // For HTTP target through proxy, no tunnel needed - just use the socket
            // The Http1Exchange will format requests with absolute URIs

            // Create protocol connection
            return createProtocolConnection(proxySocket, route);

        } catch (IOException e) {
            try {
                proxySocket.close();
            } catch (IOException ignored) {}
            throw new IOException(
                    "Failed to connect to " + route.host() + " via proxy " +
                            proxy.hostname() + ":" + proxy.port() + " (" + proxyAddress.getHostAddress() + ")",
                    e);
        }
    }

    /**
     * Perform TLS handshake to an HTTPS proxy.
     *
     * <p>This establishes a secure connection to the proxy itself, before any
     * CONNECT tunnel or target communication. Used for HTTPS proxy type.
     *
     * @param socket the connected TCP socket to the proxy
     * @param proxy the proxy configuration (hostname used for SNI and verification)
     * @return SSLSocket connected to the proxy with TLS
     * @throws IOException if TLS handshake fails
     */
    private Socket performTlsHandshakeToProxy(Socket socket, ProxyConfiguration proxy) throws IOException {
        try {
            SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory()
                    .createSocket(socket, proxy.hostname(), proxy.port(), true);

            SSLParameters params = sslSocket.getSSLParameters();

            // Enable hostname verification for the proxy
            params.setEndpointIdentificationAlgorithm("HTTPS");

            // No ALPN for proxy connection - we'll speak HTTP/1.1 for CONNECT
            // (The actual protocol negotiation happens on the target connection)

            sslSocket.setSSLParameters(params);

            // Set handshake timeout
            int originalTimeout = sslSocket.getSoTimeout();
            sslSocket.setSoTimeout((int) tlsNegotiationTimeout.toMillis());

            try {
                sslSocket.startHandshake();
            } finally {
                sslSocket.setSoTimeout(originalTimeout);
            }

            return sslSocket;

        } catch (IOException e) {
            throw new IOException("TLS handshake to HTTPS proxy " + proxy.hostname() + " failed", e);
        }
    }

    /**
     * Close a connection, ignoring any IOException.
     *
     * @param connection the connection to close
     */
    private void closeConnection(HttpConnection connection) {
        try {
            connection.close();
        } catch (IOException ignored) {}
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
                for (HostPool pool : pools.values()) {
                    int removed = pool.removeIdleConnections(maxIdleTimeNanos,
                            (conn, reason) -> notifyClosed(conn, reason));
                    if (removed > 0) {
                        connectionPermits.release(removed);
                    }
                }

                // Clean up unhealthy HTTP/2 connections
                for (Route route : activeH2Connections.keySet()) {
                    cleanupDeadH2Connections(route);
                }

            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Per-route connection pool using blocking deque.
     *
     * <p>Uses {@link LinkedBlockingDeque} which properly blocks virtual threads, releasing the carrier thread while
     * waiting. This is the preferred approach for virtual thread-based applications over lock-free structures with
     * spin-waiting.
     *
     * <p>The deque is capacity-bounded, so size limits are enforced automatically by the data structure itself.
     */
    private static final class HostPool {
        private final LinkedBlockingDeque<PooledConnection> available;

        HostPool(int maxConnections) {
            // Capacity-bounded deque handles max size enforcement automatically
            this.available = new LinkedBlockingDeque<>(maxConnections);
        }

        /**
         * Non-blocking poll for an available connection.
         * Uses LIFO (pollFirst) to return most recently used connection.
         */
        PooledConnection poll() {
            return available.pollFirst();
        }

        /**
         * Poll for an available connection with timeout.
         * Properly blocks the virtual thread, releasing the carrier thread.
         */
        PooledConnection poll(long timeout, TimeUnit unit) throws InterruptedException {
            return available.pollFirst(timeout, unit);
        }

        /**
         * Return a connection to the pool with timeout.
         * Uses LIFO (offerFirst) so this connection is returned next.
         * Blocks briefly if pool is at capacity to handle contention spikes.
         */
        boolean offer(PooledConnection connection, long timeout, TimeUnit unit) throws InterruptedException {
            return available.offerFirst(connection, timeout, unit);
        }

        void remove(HttpConnection connection) {
            available.removeIf(pc -> pc.connection == connection);
        }

        int size() {
            return available.size();
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
                    // Use IDLE_TIMEOUT if just expired, UNEXPECTED_CLOSE if unhealthy
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

    /**
     * A connection in the pool with metadata.
     *
     * <p>Tracks when the connection became idle so we can enforce
     * {@code maxIdleTime} limits.
     */
    private record PooledConnection(HttpConnection connection, long idleSinceNanos) {}

    /**
     * Create a new builder for HttpConnectionPool.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for HttpConnectionPool.
     */
    public static final class Builder {
        private int maxConnectionsPerRoute = 20;
        private final Map<String, Integer> perHostLimits = new HashMap<>();
        private int maxTotalConnections = 200;
        private Duration maxIdleTime = Duration.ofMinutes(2);
        private Duration acquireTimeout = Duration.ofSeconds(30);
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration tlsNegotiationTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private Duration writeTimeout = Duration.ofSeconds(30);
        private SSLContext sslContext;
        private HttpVersionPolicy versionPolicy = HttpVersionPolicy.AUTOMATIC;
        private DnsResolver dnsResolver;
        private HttpSocketFactory socketFactory = HttpSocketFactory::defaultSocketFactory;
        private final List<ConnectionPoolListener> listeners = new LinkedList<>();

        /**
         * Set default maximum connections per route (default: 20).
         *
         * <p>This is the default limit for all routes unless overridden via
         * {@link #maxConnectionsForHost(String, int)}.
         *
         * <p>Each route (unique scheme+host+port+proxy combination) gets its own
         * connection pool with this capacity.
         *
         * <p><b>Note:</b> Per-route limits only apply to HTTP/1.1 connections.
         * HTTP/2 connections use multiplexing to handle many concurrent requests over
         * fewer connections, so only {@link #maxTotalConnections(int)} applies to them.
         *
         * @param max maximum connections per route, must be positive
         * @return this builder
         * @throws IllegalArgumentException if max is not positive
         */
        public Builder maxConnectionsPerRoute(int max) {
            if (max <= 0) {
                throw new IllegalArgumentException("maxConnectionsPerRoute must be positive: " + max);
            }
            this.maxConnectionsPerRoute = max;
            return this;
        }

        /**
         * Set maximum connections for a specific host (overrides default).
         *
         * <p>Host format examples:
         * <ul>
         *   <li>{@code "api.example.com"} - applies to default port (80/443)</li>
         *   <li>{@code "api.example.com:8080"} - applies only to port 8080</li>
         * </ul>
         *
         * <p>Example usage:
         * <pre>{@code
         * builder
         *     .maxConnectionsPerRoute(20)  // Default for all routes
         *     .maxConnectionsForHost("slow-api.example.com", 2)  // Limit slow API
         *     .maxConnectionsForHost("fast-cdn.example.com", 100)  // Allow more for CDN
         * }</pre>
         *
         * <p>Host matching is case-insensitive. If a port-specific limit is set,
         * it takes precedence over the host-only limit.
         *
         * <p><b>Note:</b> Per-host limits only apply to HTTP/1.1 connections and are
         * always capped by {@link #maxTotalConnections(int)}. If a per-host limit exceeds
         * {@code maxTotalConnections}, the global limit takes precedence.
         *
         * @param host the hostname (with optional port), case-insensitive
         * @param max maximum connections for this specific host, must be positive
         * @return this builder
         * @throws IllegalArgumentException if host is null/empty or max is not positive
         */
        public Builder maxConnectionsForHost(String host, int max) {
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("host must not be null or empty");
            }
            if (max <= 0) {
                throw new IllegalArgumentException("max must be positive: " + max);
            }
            perHostLimits.put(host.toLowerCase(), max);
            return this;
        }

        /**
         * Set maximum total connections across all routes (default: 200).
         *
         * <p>This is a global limit across all routes to prevent unbounded
         * connection growth. When this limit is reached, {@link #acquire(Route)}
         * will throw IOException.
         *
         * <p>Must be at least as large as {@code maxConnectionsPerRoute}.
         *
         * @param max maximum total connections, must be positive
         * @return this builder
         * @throws IllegalArgumentException if max is not positive
         */
        public Builder maxTotalConnections(int max) {
            if (max <= 0) {
                throw new IllegalArgumentException("maxTotalConnections must be positive: " + max);
            }
            this.maxTotalConnections = max;
            return this;
        }

        /**
         * Set maximum idle time before connections are closed (default: 2 minutes).
         *
         * <p>Connections that have been idle (in the pool) longer than this duration
         * are closed by the background cleanup thread.
         *
         * <p><b>Note:</b> This setting currently only applies to HTTP/1.1 connections.
         * HTTP/2 connections use multiplexing and remain open until they become unhealthy
         * (e.g., server closes the connection or GOAWAY is received).
         *
         * <p>Set lower for short-lived applications or high-churn workloads.
         * Set higher for long-running applications with steady traffic.
         *
         * @param duration maximum idle time, must be positive
         * @return this builder
         * @throws IllegalArgumentException if duration is null, negative, or zero
         */
        public Builder maxIdleTime(Duration duration) {
            if (duration == null || duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("maxIdleTime must be positive: " + duration);
            }
            this.maxIdleTime = duration;
            return this;
        }

        /**
         * Set acquire timeout for waiting when pool is exhausted (default: 30 seconds).
         *
         * <p>When {@link #maxTotalConnections(int)} is reached, {@link #acquire(Route)}
         * will block for up to this duration waiting for a connection to become available.
         * If no connection becomes available within this time, an {@link IOException} is thrown.
         *
         * <p>This timeout applies uniformly to both HTTP/1.1 and HTTP/2 connections.
         * With virtual threads, blocking is cheap, so a longer timeout (30s default)
         * provides good backpressure behavior under load spikes.
         *
         * <p>Set to {@link Duration#ZERO} for fail-fast behavior (immediate failure
         * when pool is exhausted, no waiting).
         *
         * @param timeout acquire timeout duration, must be non-negative
         * @return this builder
         * @throws IllegalArgumentException if timeout is null or negative
         */
        public Builder acquireTimeout(Duration timeout) {
            if (timeout == null || timeout.isNegative()) {
                throw new IllegalArgumentException("acquireTimeout must be non-negative: " + timeout);
            }
            this.acquireTimeout = timeout;
            return this;
        }

        /**
         * Set connection timeout (default: 10 seconds).
         *
         * <p>This is the maximum time to wait for TCP connection establishment.
         * If the connection doesn't complete within this time, the attempt fails
         * and the next resolved IP (if any) is tried.
         *
         * @param timeout connection timeout duration, must be non-negative
         * @return this builder
         * @throws IllegalArgumentException if timeout is null or negative
         */
        public Builder connectTimeout(Duration timeout) {
            if (timeout == null || timeout.isNegative()) {
                throw new IllegalArgumentException("connectTimeout must be non-negative: " + timeout);
            }
            this.connectTimeout = timeout;
            return this;
        }

        /**
         * Set TLS negotiation timeout (default: 10 seconds).
         *
         * <p>This is the maximum time to wait for TLS handshake completion.
         * If the handshake doesn't complete within this time, the connection fails.
         *
         * <p>Separate from {@link #connectTimeout(Duration)} because TLS handshake
         * happens after TCP connection is established.
         *
         * @param timeout TLS negotiation timeout, must be non-negative
         * @return this builder
         * @throws IllegalArgumentException if timeout is null or negative
         */
        public Builder tlsNegotiationTimeout(Duration timeout) {
            if (timeout == null || timeout.isNegative()) {
                throw new IllegalArgumentException("tlsNegotiationTimeout must be non-negative: " + timeout);
            }
            this.tlsNegotiationTimeout = timeout;
            return this;
        }

        /**
         * Set read timeout for waiting on response data (default: 30 seconds).
         *
         * <p>This timeout applies to:
         * <ul>
         *   <li>Waiting for response headers after sending request</li>
         *   <li>Waiting for response body data chunks</li>
         * </ul>
         *
         * <p>If no data is received within this duration, a
         * {@link java.net.SocketTimeoutException} is thrown.
         *
         * @param timeout read timeout duration, must be non-negative
         * @return this builder
         * @throws IllegalArgumentException if timeout is null or negative
         */
        public Builder readTimeout(Duration timeout) {
            if (timeout == null || timeout.isNegative()) {
                throw new IllegalArgumentException("readTimeout must be non-negative: " + timeout);
            }
            this.readTimeout = timeout;
            return this;
        }

        /**
         * Set write timeout for sending request data (default: 30 seconds).
         *
         * <p>This timeout applies to waiting for flow control window space
         * when sending request body data. If flow control prevents sending
         * within this duration, a {@link java.net.SocketTimeoutException} is thrown.
         *
         * @param timeout write timeout duration, must be non-negative
         * @return this builder
         * @throws IllegalArgumentException if timeout is null or negative
         */
        public Builder writeTimeout(Duration timeout) {
            if (timeout == null || timeout.isNegative()) {
                throw new IllegalArgumentException("writeTimeout must be non-negative: " + timeout);
            }
            this.writeTimeout = timeout;
            return this;
        }

        /**
         * Set SSL context for HTTPS connections (default: {@link SSLContext#getDefault()}).
         *
         * <p>Configure a custom SSLContext for:
         * <ul>
         *   <li>Custom CA bundles (via TrustManager)</li>
         *   <li>Client certificate authentication/mTLS (via KeyManager)</li>
         *   <li>Custom TLS settings (via SSLParameters)</li>
         * </ul>
         *
         * <p>Example with custom CA:
         * <pre>{@code
         * KeyStore trustStore = KeyStore.getInstance("PKCS12");
         * trustStore.load(...);
         *
         * TrustManagerFactory tmf = TrustManagerFactory.getInstance(
         *     TrustManagerFactory.getDefaultAlgorithm()
         * );
         * tmf.init(trustStore);
         *
         * SSLContext ctx = SSLContext.getInstance("TLS");
         * ctx.init(null, tmf.getTrustManagers(), null);
         *
         * builder.sslContext(ctx);
         * }</pre>
         *
         * @param context the SSL context to use for HTTPS connections
         * @return this builder
         */
        public Builder sslContext(SSLContext context) {
            this.sslContext = context;
            return this;
        }

        /**
         * Set HTTP version policy to control which protocol versions are negotiated via ALPN (default: AUTOMATIC).
         *
         * @param policy the version policy to use
         * @return this builder
         * @throws IllegalArgumentException if policy is null
         */
        public Builder httpVersionPolicy(HttpVersionPolicy policy) {
            Objects.requireNonNull(policy, "httpVersionPolicy cannot be null");
            this.versionPolicy = policy;
            return this;
        }

        /**
         * Set DNS resolver for hostname resolution (default: system resolver with 1-minute cache).
         *
         * @param resolver the DNS resolver to use
         * @return this builder
         * @throws IllegalArgumentException if resolver is null
         */
        public Builder dnsResolver(DnsResolver resolver) {
            Objects.requireNonNull(resolver, "dnsResolver must not be null");
            this.dnsResolver = resolver;
            return this;
        }

        /**
         * Set socket factory (default: creates socket with TCP_NODELAY=true, SO_KEEPALIVE=true).
         *
         * <p>The factory creates and configures sockets before they are connected.
         *
         * <p>Example:
         * <pre>{@code
         * builder.socketFactory((route, endpoints) -> {
         *     Socket socket = new Socket();
         *     socket.setTcpNoDelay(true);
         *     socket.setKeepAlive(true);
         *     if (route.host().endsWith(".internal")) {
         *         socket.setSendBufferSize(256 * 1024);
         *     }
         *     return socket;
         * });
         * }</pre>
         *
         * @param socketFactory creates and configures sockets before connection
         * @return this builder
         * @throws NullPointerException if socketFactory is null
         * @see HttpSocketFactory
         */
        public Builder socketFactory(HttpSocketFactory socketFactory) {
            this.socketFactory = Objects.requireNonNull(socketFactory, "socketFactory");
            return this;
        }

        /**
         * Add a listener for connection pool lifecycle events.
         *
         * <p>Listeners are notified of connection creation, acquisition, release, and eviction events. Multiple
         * listeners can be added and are called in order. Listeners are called synchronously, so calls should be fast.
         *
         * @param listener the listener to add
         * @return this builder
         * @throws NullPointerException if listener is null
         * @see ConnectionPoolListener
         */
        public Builder addListener(ConnectionPoolListener listener) {
            listeners.add(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        /**
         * Add a listener at the front of the listener list.
         *
         * <p>This listener will be called before any previously added listeners.
         * Useful for adding wrapper/decorator listeners that should see events first.
         *
         * @param listener the listener to add
         * @return this builder
         * @throws NullPointerException if listener is null
         * @see #addListener(ConnectionPoolListener)
         */
        public Builder addListenerFirst(ConnectionPoolListener listener) {
            listeners.addFirst(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        /**
         * Build the connection pool.
         *
         * @return a new connection pool instance
         * @throws IllegalStateException if the configuration is invalid
         */
        public HttpConnectionPool build() {
            if (sslContext == null) {
                try {
                    sslContext = SSLContext.getDefault();
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException("Failed to get default SSLContext", e);
                }
            }

            if (maxTotalConnections < maxConnectionsPerRoute) {
                throw new IllegalStateException(
                        "maxTotalConnections (" + maxTotalConnections + ") must be >= " +
                                "maxConnectionsPerRoute (" + maxConnectionsPerRoute + ")");
            }

            return new HttpConnectionPool(this);
        }
    }
}
