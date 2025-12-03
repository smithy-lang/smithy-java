/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.SSLContext;
import software.amazon.smithy.java.http.client.dns.DnsResolver;

/**
 * Builder for HttpConnectionPool.
 */
public final class HttpConnectionPoolBuilder {
    int maxConnectionsPerRoute = 20;
    final Map<String, Integer> perHostLimits = new HashMap<>();
    int maxTotalConnections = 200;
    Duration maxIdleTime = Duration.ofMinutes(2);
    Duration acquireTimeout = Duration.ofSeconds(30);
    Duration connectTimeout = Duration.ofSeconds(10);
    Duration tlsNegotiationTimeout = Duration.ofSeconds(10);
    Duration readTimeout = Duration.ofSeconds(30);
    Duration writeTimeout = Duration.ofSeconds(30);
    SSLContext sslContext;
    HttpVersionPolicy versionPolicy = HttpVersionPolicy.AUTOMATIC;
    DnsResolver dnsResolver;
    HttpSocketFactory socketFactory = HttpSocketFactory::defaultSocketFactory;
    final List<ConnectionPoolListener> listeners = new LinkedList<>();

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
    public HttpConnectionPoolBuilder maxConnectionsPerRoute(int max) {
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
     * @param max  maximum connections for this specific host, must be positive
     * @return this builder
     * @throws IllegalArgumentException if host is null/empty or max is not positive
     */
    public HttpConnectionPoolBuilder maxConnectionsForHost(String host, int max) {
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
     * connection growth. When this limit is reached, {@link HttpConnectionPool#acquire(Route)}
     * will throw IOException.
     *
     * <p>Must be at least as large as {@code maxConnectionsPerRoute}.
     *
     * @param max maximum total connections, must be positive
     * @return this builder
     * @throws IllegalArgumentException if max is not positive
     */
    public HttpConnectionPoolBuilder maxTotalConnections(int max) {
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
    public HttpConnectionPoolBuilder maxIdleTime(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("maxIdleTime must be positive: " + duration);
        }
        this.maxIdleTime = duration;
        return this;
    }

    /**
     * Set acquire timeout for waiting when pool is exhausted (default: 30 seconds).
     *
     * <p>When {@link #maxTotalConnections(int)} is reached, {@link HttpConnectionPool#acquire(Route)}
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
    public HttpConnectionPoolBuilder acquireTimeout(Duration timeout) {
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
    public HttpConnectionPoolBuilder connectTimeout(Duration timeout) {
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
    public HttpConnectionPoolBuilder tlsNegotiationTimeout(Duration timeout) {
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
    public HttpConnectionPoolBuilder readTimeout(Duration timeout) {
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
    public HttpConnectionPoolBuilder writeTimeout(Duration timeout) {
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
    public HttpConnectionPoolBuilder sslContext(SSLContext context) {
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
    public HttpConnectionPoolBuilder httpVersionPolicy(HttpVersionPolicy policy) {
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
    public HttpConnectionPoolBuilder dnsResolver(DnsResolver resolver) {
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
    public HttpConnectionPoolBuilder socketFactory(HttpSocketFactory socketFactory) {
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
    public HttpConnectionPoolBuilder addListener(ConnectionPoolListener listener) {
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
    public HttpConnectionPoolBuilder addListenerFirst(ConnectionPoolListener listener) {
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
