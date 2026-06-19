/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.connection.ConnectionConfig;
import software.amazon.smithy.java.http.client.connection.ConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpSocketFactory;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.connection.TlsProvider;
import software.amazon.smithy.java.http.client.dns.DnsResolver;

/**
 * Blocking, virtual-thread-friendly HTTP client.
 */
public interface HttpClient extends AutoCloseable {
    /**
     * Sends a request and returns a streaming response.
     *
     * <p>The response body streams directly from the socket. The caller must close the response body
     * when done to release the connection back to the pool.
     *
     * <p>For HTTP/2, the request body is written concurrently with reading the response (full duplex).
     * For HTTP/1.1, the request body is fully sent before the response is returned.
     *
     * @param request the HTTP request to send
     * @return the HTTP response with streaming body
     * @throws IOException if the request fails
     */
    default HttpResponse send(HttpRequest request) throws IOException {
        return send(request, RequestOptions.defaults());
    }

    /**
     * Send a request with request options.
     *
     * @param request request to send.
     * @param options options to apply.
     * @return the HTTP response
     * @throws IOException if the request fails
     */
    HttpResponse send(HttpRequest request, RequestOptions options) throws IOException;

    /**
     * Closes the client and its underlying connection pool.
     */
    @Override
    void close() throws IOException;

    /**
     * Gracefully shuts down the client, waiting for in-flight requests to complete.
     *
     * @param timeout maximum time to wait for in-flight requests to complete
     */
    void shutdown(Duration timeout);

    /**
     * Builder to create a new default HTTP client.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder used to create a default HTTP client implementation.
     */
    final class Builder {
        private static final ProxySelector DIRECT = ProxySelector.direct();
        private final ConnectionConfig.Builder connectionConfig = ConnectionConfig.builder();
        Function<ConnectionConfig, ? extends ConnectionPool> connectionPoolFactory = HttpConnectionPool::new;
        Duration requestTimeout;
        ProxySelector proxySelector = DIRECT;
        ConnectionConfig resolvedConnectionConfig;
        ConnectionPool connectionPool;

        private Builder() {}

        /**
         * Set a custom connection pool factory.
         *
         * <p>The factory receives the final immutable connection configuration, including listeners registered on this
         * client builder. This keeps request-level and connection-level listener events wired consistently for custom
         * pools.
         *
         * @param factory the connection pool factory to use
         * @return this builder
         */
        public Builder connectionPoolFactory(Function<ConnectionConfig, ? extends ConnectionPool> factory) {
            this.connectionPoolFactory = Objects.requireNonNull(factory, "connectionPoolFactory");
            return this;
        }

        /**
         * Set total request timeout (default: none).
         *
         * <p>If set, the entire buffered request must complete within this duration, or an {@link IOException} is
         * thrown. Timeout is not enforced for streaming responses as control flow is handed back to the caller.
         *
         * <p>If not set (null), requests have no overall timeout and are only limited by
         * the connect and read timeouts.
         *
         * @param timeout total request timeout duration, or null for no timeout
         * @return this builder
         * @throws IllegalArgumentException if timeout is negative or zero
         */
        public Builder requestTimeout(Duration timeout) {
            if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
                throw new IllegalArgumentException("requestTimeout must be positive or null: " + timeout);
            }
            this.requestTimeout = timeout;
            return this;
        }

        /**
         * Set proxy configuration for all connections made by this client.
         *
         * <p>When configured, all HTTP requests will be routed through the proxy unless the target host matches
         * one of the non-proxy hosts.
         *
         * <p>For HTTPS requests, the client establishes a CONNECT tunnel through the proxy, then performs TLS
         * handshake through the tunnel.
         *
         * <p>For HTTP requests, the client connects to the proxy and sends requests with absolute URIs.
         *
         * @param proxy the proxy configuration, or null for direct connections
         * @return this builder
         * @see ProxyConfiguration
         */
        public Builder proxy(ProxyConfiguration proxy) {
            return proxySelector(proxy != null ? ProxySelector.of(proxy) : DIRECT);
        }

        /**
         * Set a custom proxy selector for dynamic proxy selection.
         *
         * <p>The selector is called for each request and can return multiple proxies to try in order.
         * If a proxy fails, the next one is attempted.
         *
         * @param selector the proxy selector to use
         * @return this builder
         */
        public Builder proxySelector(ProxySelector selector) {
            this.proxySelector = Objects.requireNonNull(selector, "proxySelector");
            return this;
        }

        /**
         * Add a listener for HTTP client lifecycle events.
         *
         * <p>Listeners are invoked synchronously on the thread performing the work. Implementations should avoid
         * blocking and keep allocation low.
         *
         * @param listener listener to add
         * @return this builder
         */
        public Builder addListener(HttpClientListener listener) {
            connectionConfig.addListener(listener);
            return this;
        }

        /**
         * Add a listener at the front of the listener list.
         *
         * @param listener listener to add
         * @return this builder
         */
        public Builder addListenerFirst(HttpClientListener listener) {
            connectionConfig.addListenerFirst(listener);
            return this;
        }

        /**
         * Set the maximum number of connections per route (scheme + host + port + proxy). Default: 256.
         *
         * <p>For HTTP/2 this caps the connections opened to a route before streams are multiplexed onto
         * existing connections; for HTTP/1.1 it caps the pooled connections per route.
         *
         * @param max maximum connections per route (must be positive)
         * @return this builder
         */
        public Builder maxConnectionsPerRoute(int max) {
            connectionConfig.maxConnectionsPerRoute(max);
            return this;
        }

        /**
         * Set the maximum number of open physical connections across all routes. Default: 256.
         *
         * <p>When the limit is reached, acquiring a connection blocks for up to {@link #acquireTimeout}.
         * Must be greater than or equal to {@link #maxConnectionsPerRoute}.
         *
         * @param max maximum total connections (must be positive)
         * @return this builder
         */
        public Builder maxTotalConnections(int max) {
            connectionConfig.maxTotalConnections(max);
            return this;
        }

        /**
         * Set how long an idle pooled connection is kept before the background cleanup closes it.
         * Default: 2 minutes.
         *
         * @param duration maximum idle time (must be positive)
         * @return this builder
         */
        public Builder maxIdleTime(Duration duration) {
            connectionConfig.maxIdleTime(duration);
            return this;
        }

        /**
         * Set how long {@code acquire} blocks waiting for capacity when the pool is exhausted before
         * failing with an {@link IOException}. Default: 30 seconds. {@link Duration#ZERO} fails fast.
         *
         * @param timeout acquire timeout (must be non-negative)
         * @return this builder
         */
        public Builder acquireTimeout(Duration timeout) {
            connectionConfig.acquireTimeout(timeout);
            return this;
        }

        /**
         * Set the TCP connect timeout for establishing a new socket. Default: 10 seconds.
         *
         * @param timeout connect timeout (must be non-negative)
         * @return this builder
         */
        public Builder connectTimeout(Duration timeout) {
            connectionConfig.connectTimeout(timeout);
            return this;
        }

        /**
         * Set the timeout for completing the TLS handshake on a new secure connection. Default: 10 seconds.
         *
         * @param timeout TLS negotiation timeout (must be non-negative)
         * @return this builder
         */
        public Builder tlsNegotiationTimeout(Duration timeout) {
            connectionConfig.tlsNegotiationTimeout(timeout);
            return this;
        }

        /**
         * Set the read timeout applied to each socket read while receiving a response. Default: 30 seconds.
         *
         * @param timeout read timeout (must be non-negative)
         * @return this builder
         */
        public Builder readTimeout(Duration timeout) {
            connectionConfig.readTimeout(timeout);
            return this;
        }

        /**
         * Set the write timeout applied while sending a request body. Default: 30 seconds.
         *
         * @param timeout write timeout (must be non-negative)
         * @return this builder
         */
        public Builder writeTimeout(Duration timeout) {
            connectionConfig.writeTimeout(timeout);
            return this;
        }

        /**
         * Sets the {@link SSLContext} for the JDK TLS path. When null, {@link SSLContext#getDefault()}
         * is used.
         *
         * <p>This configures the built-in JDK provider and is convenience equivalent to
         * {@code tlsProvider(JdkTlsProvider.builder().sslContext(context).build())}. It governs:
         * <ul>
         *   <li>the default (JDK) TLS connection to the target, when no custom
         *       {@link #tlsProvider(TlsProvider)} is set; and</li>
         *   <li>the HTTP/1.1-only {@code SSLSocket} fast path.</li>
         * </ul>
         *
         * <p>It is <em>ignored</em> for the target connection when a custom {@link #tlsProvider} is set
         * (that provider supplies its own TLS configuration).
         *
         * <p><b>HTTPS proxies:</b> the TLS connection <em>to an {@code https} proxy</em> always uses this
         * context (and {@link #sslParameters}), independent of {@link #tlsProvider} — a custom provider
         * applies only to the end-to-end connection through the tunnel, not to the proxy leg. To trust a
         * proxy differently from the target, set a context here that covers both.
         *
         * @param context the SSL context, or null for the JDK default
         * @return this builder
         */
        public Builder sslContext(SSLContext context) {
            connectionConfig.sslContext(context);
            return this;
        }

        /**
         * Sets {@link SSLParameters} (cipher suites, protocols, SNI, etc.) for the JDK TLS path. When
         * null, parameters derived from the {@link #sslContext} are used.
         *
         * <p>Convenience equivalent to
         * {@code tlsProvider(JdkTlsProvider.builder().sslParameters(params).build())}. Applies to the
         * same connections as {@link #sslContext} — the JDK target path, the HTTP/1.1 {@code SSLSocket}
         * fast path, and the {@code https}-proxy leg — and is likewise ignored for the target connection
         * when a custom {@link #tlsProvider} is set.
         *
         * @param parameters the SSL parameters, or null for defaults
         * @return this builder
         */
        public Builder sslParameters(SSLParameters parameters) {
            connectionConfig.sslParameters(parameters);
            return this;
        }

        /**
         * Select the TLS provider used for secure connections, replacing the built-in JDK provider.
         *
         * <p>A provider turns a connected socket into a handshaken transport; it need not be based on a
         * {@code javax.net.ssl.SSLEngine}. This is the provider-neutral way to choose a TLS
         * implementation (e.g. a native stack).
         *
         * <p>An explicit provider set here always takes precedence. When none is set, a provider may be
         * selected by the {@value TlsProvider#PROVIDER_PROPERTY} system property (set to a registered
         * provider's fully-qualified class name); otherwise the JDK provider is used. Merely having a
         * provider module on the classpath does not engage it — the property is the opt-in.
         *
         * @param provider the TLS provider, or null to use property selection / the JDK provider
         * @return this builder
         */
        public Builder tlsProvider(TlsProvider provider) {
            connectionConfig.tlsProvider(provider);
            return this;
        }

        /**
         * Set the HTTP version policy (e.g. negotiate via ALPN, enforce HTTP/1.1, enforce HTTP/2,
         * h2c prior knowledge). Default: {@link HttpVersionPolicy#AUTOMATIC}.
         *
         * @param policy the version policy (must not be null)
         * @return this builder
         */
        public Builder httpVersionPolicy(HttpVersionPolicy policy) {
            connectionConfig.httpVersionPolicy(policy);
            return this;
        }

        /**
         * Set the DNS resolver used to resolve hostnames to addresses. When null, a default round-robin
         * resolver is used.
         *
         * @param resolver the DNS resolver (must not be null)
         * @return this builder
         */
        public Builder dnsResolver(DnsResolver resolver) {
            connectionConfig.dnsResolver(resolver);
            return this;
        }

        /**
         * Set a custom factory for creating the underlying {@link java.net.Socket}, honored verbatim.
         * When not set, sockets are created with the library defaults plus any
         * {@link #socketReceiveBufferSize(int)}/{@link #socketSendBufferSize(int)} knobs.
         *
         * @param socketFactory the socket factory (must not be null)
         * @return this builder
         */
        public Builder socketFactory(HttpSocketFactory socketFactory) {
            connectionConfig.socketFactory(socketFactory);
            return this;
        }

        /**
         * Set the socket receive buffer size ({@code SO_RCVBUF}) in bytes. Unset by default (kernel
         * default); {@code -1} requests kernel autotuning. Ignored when a custom
         * {@link #socketFactory(HttpSocketFactory)} is supplied.
         *
         * @param bytes receive buffer size in bytes (positive, or -1 for autotune)
         * @return this builder
         */
        public Builder socketReceiveBufferSize(int bytes) {
            connectionConfig.socketReceiveBufferSize(bytes);
            return this;
        }

        /**
         * Set the socket send buffer size ({@code SO_SNDBUF}) in bytes. Unset by default (kernel
         * default); {@code -1} requests kernel autotuning. Ignored when a custom
         * {@link #socketFactory(HttpSocketFactory)} is supplied.
         *
         * @param bytes send buffer size in bytes (positive, or -1 for autotune)
         * @return this builder
         */
        public Builder socketSendBufferSize(int bytes) {
            connectionConfig.socketSendBufferSize(bytes);
            return this;
        }

        /**
         * Set the application-side read buffer size for the TLS engine transport, in bytes. Default: 16 KiB.
         * Larger buffers reduce read syscalls for bulk downloads.
         *
         * @param bytes TLS read buffer size in bytes (must be positive)
         * @return this builder
         */
        public Builder tlsReadBufferSize(int bytes) {
            connectionConfig.tlsReadBufferSize(bytes);
            return this;
        }

        /**
         * Set the application-side write buffer size for the TLS engine transport, in bytes. Default: 16 KiB.
         * Larger buffers can coalesce write syscalls for bulk uploads.
         *
         * @param bytes TLS write buffer size in bytes (must be positive)
         * @return this builder
         */
        public Builder tlsWriteBufferSize(int bytes) {
            connectionConfig.tlsWriteBufferSize(bytes);
            return this;
        }

        /**
         * Set the HTTP/2 initial stream flow-control window advertised to the peer, in bytes. Default: 65535.
         *
         * @param windowSize initial window size in bytes (must be positive)
         * @return this builder
         */
        public Builder h2InitialWindowSize(int windowSize) {
            connectionConfig.h2InitialWindowSize(windowSize);
            return this;
        }

        /**
         * Set the HTTP/2 maximum frame size advertised to the peer, in bytes. Default: 16384. Must be
         * between 16384 and 16777215 inclusive (per RFC 9113).
         *
         * @param frameSize maximum frame size in bytes (16384..16777215)
         * @return this builder
         */
        public Builder h2MaxFrameSize(int frameSize) {
            connectionConfig.h2MaxFrameSize(frameSize);
            return this;
        }

        /**
         * Set the maximum number of concurrent HTTP/2 streams to multiplex per connection. Default: 100.
         *
         * @param streams maximum concurrent streams per connection (must be positive)
         * @return this builder
         */
        public Builder h2StreamsPerConnection(int streams) {
            connectionConfig.h2StreamsPerConnection(streams);
            return this;
        }

        /**
         * Set the HTTP/2 connection I/O buffer size in bytes. Default: 256 KiB. Must be at least 16 KiB.
         *
         * @param bufferSize I/O buffer size in bytes (at least 16384)
         * @return this builder
         */
        public Builder h2BufferSize(int bufferSize) {
            connectionConfig.h2BufferSize(bufferSize);
            return this;
        }

        /**
         * Build the HTTP client.
         *
         * @return a new HTTP client instance
         */
        public HttpClient build() {
            resolvedConnectionConfig = connectionConfig.build();
            connectionPool = Objects.requireNonNull(
                    connectionPoolFactory.apply(resolvedConnectionConfig),
                    "connectionPoolFactory returned null");
            return new DefaultHttpClient(this);
        }
    }
}
