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
import javax.net.ssl.SSLParameters;
import software.amazon.smithy.java.http.client.HttpClientListener;
import software.amazon.smithy.java.http.client.dns.DnsResolver;

/**
 * Builder for HttpConnectionPool.
 */
public final class HttpConnectionPoolBuilder {
    int maxTotalConnections = 256;
    int maxConnectionsPerRoute = 256;
    int h2StreamsPerConnection = 100;
    int h2InitialWindowSize = 65535; // RFC 9113 default
    int h2MaxFrameSize = 16384; // RFC 9113 default
    int h2BufferSize = 256 * 1024; // 256KB default
    boolean usePlatformReaderForH2;
    boolean useEpollTransport;
    final Map<String, Integer> perHostLimits = new HashMap<>();

    Duration maxIdleTime = Duration.ofMinutes(2);
    Duration acquireTimeout = Duration.ofSeconds(30);
    Duration connectTimeout = Duration.ofSeconds(10);
    Duration tlsNegotiationTimeout = Duration.ofSeconds(10);
    Duration readTimeout = Duration.ofSeconds(30);
    Duration writeTimeout = Duration.ofSeconds(30);
    SSLContext sslContext;
    SSLParameters sslParameters;
    ClientSslEngineFactory sslEngineFactory;
    HttpVersionPolicy versionPolicy = HttpVersionPolicy.AUTOMATIC;
    DnsResolver dnsResolver;
    HttpSocketFactory socketFactory = HttpSocketFactory.DEFAULT;
    boolean socketFactoryExplicit;
    Integer socketReceiveBufferSize;
    Integer socketSendBufferSize;
    // Ciphertext-read buffer for the SSLEngineTransport TLS path. One TLS record (~16KB) by default
    // — one socket read per record. Larger values let one socket read pull many records that the
    // unwrap loop drains in a single locked pass, collapsing read syscalls / watchdog arms / VT
    // park-unpark proportionally. Only affects the SSLEngineTransport path (custom sslEngineFactory,
    // e.g. BoringSSL, or non-ENFORCE_HTTP_1_1 with the JDK engine); the JDK SSLSocket path is unaffected.
    int tlsReadBufferSize = 16 * 1024;
    // Ciphertext-write buffer for the SSLEngineTransport TLS path. One TLS record (~16KB) by default
    // — one socket write per wrapped record. Larger values let write() accumulate several records
    // before one socket write, collapsing write syscalls for bulk uploads. Same path scoping as
    // tlsReadBufferSize; the JDK SSLSocket path is unaffected.
    int tlsWriteBufferSize = 16 * 1024;
    final List<HttpClientListener> listeners = new LinkedList<>();

    /**
     * Set default maximum connections per route (default: 256).
     *
     * <p>This is the default limit for all routes unless overridden via
     * {@link #maxConnectionsForHost(String, int)}.
     *
     * <p>Each route (unique scheme+host+port+proxy combination) gets its own
     * connection pool with this capacity.
     *
     * <p><b>HTTP/1.1:</b> This limits how many connections can be actively leased or retained for reuse.
     *
     * <p><b>HTTP/2:</b> This limits physical connections. Maximum concurrent streams
     * per route = {@code maxConnectionsPerRoute × h2StreamsPerConnection}. For example,
     * with default settings (256 connections × 100 streams), a route can handle up to
     * 25,600 concurrent requests.
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
     *     .maxConnectionsPerRoute(20)  // Override default for all routes
     *     .maxConnectionsForHost("slow-api.example.com", 2)  // Limit slow API
     *     .maxConnectionsForHost("fast-cdn.example.com", 100)  // Allow more for CDN
     * }</pre>
     *
     * <p>Host matching is case-insensitive. If a port-specific limit is set,
     * it takes precedence over the host-only limit.
     *
     * <p><b>HTTP/1.1:</b> Limits how many connections can be actively leased or retained for reuse.
     *
     * <p><b>HTTP/2:</b> Limits physical connections to the host. Maximum concurrent
     * streams = {@code maxConnectionsForHost × h2StreamsPerConnection}. For example,
     * {@code maxConnectionsForHost("api.com", 5)} with {@code h2StreamsPerConnection(100)}
     * allows up to 500 concurrent streams to that host.
     *
     * <p><b>Note:</b> Always capped by {@link #maxTotalConnections(int)}.
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
     * Set maximum total connections across all routes (default: 256).
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
     * <p>For HTTP/2, idle connections are closed only when they have no active streams.
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
     * Set acquire timeout for waiting when connection capacity is exhausted (default: 30 seconds).
     *
     * <p>When route capacity, stream capacity, or {@link #maxTotalConnections(int)} is exhausted,
     * {@link HttpConnectionPool#acquire(Route)} will block for up to this duration waiting for capacity
     * to become available.
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
     * <p><b>Note:</b> A value of {@link Duration#ZERO} means infinite timeout (wait forever).
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
     * <p><b>Note:</b> This timeout applies per read operation during the handshake, not as a total wall-clock
     * deadline. A value of {@link Duration#ZERO} means infinite timeout (wait forever).
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
     * <p><b>Note:</b> A value of {@link Duration#ZERO} means infinite timeout (wait forever).
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
     * <p><b>Note:</b> A value of {@link Duration#ZERO} means infinite timeout (wait forever).
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
     * Set SSL parameters for HTTPS connections (default: derived from SSLContext).
     *
     * <p>Configure custom SSLParameters for:
     * <ul>
     *   <li>Specific TLS protocol versions (e.g., TLSv1.3 only)</li>
     *   <li>Custom cipher suites</li>
     *   <li>SNI configuration</li>
     *   <li>Client authentication requirements</li>
     * </ul>
     *
     * <p>Note: ALPN protocols are set automatically based on {@link #httpVersionPolicy}
     * and will override any ALPN settings in the provided parameters.
     *
     * @param parameters the SSL parameters to use
     * @return this builder
     */
    public HttpConnectionPoolBuilder sslParameters(SSLParameters parameters) {
        this.sslParameters = parameters;
        return this;
    }

    /**
     * Set a custom {@link ClientSslEngineFactory} for HTTPS connections (default: none — the JDK
     * {@link SSLContext} is used).
     *
     * <p>When set, every secure connection — HTTP/1.1 included — is driven through the ByteBuffer-based
     * {@link SSLEngineTransport} using engines minted by this factory, instead of the JDK
     * {@code SSLSocket}/{@code SSLEngine}. This is the seam an alternate TLS provider (e.g. a native
     * BoringSSL engine with faster AES-GCM) plugs into without {@code http-client} depending on it.
     *
     * @param factory the engine factory, or null to use the JDK provider
     * @return this builder
     */
    public HttpConnectionPoolBuilder sslEngineFactory(ClientSslEngineFactory factory) {
        this.sslEngineFactory = factory;
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
     * Set DNS resolver for hostname resolution (default: round-robin system resolver).
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
        this.socketFactoryExplicit = true;
        return this;
    }

    /**
     * Set the SO_RCVBUF (TCP receive buffer) size in bytes for new connection sockets.
     *
     * <p>Has no effect when an explicit {@link #socketFactory} has been set; that factory is then
     * fully responsible for socket configuration. When unset, SO_RCVBUF is left unset and the
     * kernel autotunes it. Pass {@code -1} to explicitly request the same behavior while
     * configuring the other socket buffer direction.
     *
     * <p><b>Tuning guidance:</b> A larger receive buffer helps low-concurrency throughput on
     * high-bandwidth/high-latency links because each connection needs a window large enough to
     * cover the bandwidth-delay product. At high concurrency, however, large per-connection
     * receive buffers can cause bufferbloat: each connection holds bytes the application has not
     * yet read, inflating tail latency. Leave this unset for the kernel default unless you need a
     * deterministic cap or a known workload-specific value.
     *
     * @param bytes SO_RCVBUF in bytes, or {@code -1} to defer to the kernel
     * @return this builder
     * @throws IllegalArgumentException if {@code bytes} is 0 or less than -1
     */
    public HttpConnectionPoolBuilder socketReceiveBufferSize(int bytes) {
        if (bytes < -1 || bytes == 0) {
            throw new IllegalArgumentException("socketReceiveBufferSize must be positive or -1: " + bytes);
        }
        this.socketReceiveBufferSize = bytes;
        return this;
    }

    /**
     * Set the SO_SNDBUF (TCP send buffer) size in bytes for new connection sockets.
     *
     * <p>Has no effect when an explicit {@link #socketFactory} has been set; that factory is then
     * fully responsible for socket configuration. When unset, SO_SNDBUF is left unset and the
     * kernel autotunes it. Pass {@code -1} to explicitly request the same behavior while
     * configuring the other socket buffer direction.
     *
     * @param bytes SO_SNDBUF in bytes, or {@code -1} to defer to the kernel
     * @return this builder
     * @throws IllegalArgumentException if {@code bytes} is 0 or less than -1
     */
    public HttpConnectionPoolBuilder socketSendBufferSize(int bytes) {
        if (bytes < -1 || bytes == 0) {
            throw new IllegalArgumentException("socketSendBufferSize must be positive or -1: " + bytes);
        }
        this.socketSendBufferSize = bytes;
        return this;
    }

    /**
     * Set the TLS ciphertext-read buffer size in bytes for the {@link SSLEngineTransport} path
     * (default: 16384, one TLS record).
     *
     * <p>This buffer holds ciphertext read from the socket before it is unwrapped to plaintext. At
     * the default of one TLS record, each {@code SSLEngineTransport} read performs one socket read
     * and unwraps one record. A larger value lets a single socket read pull many buffered records,
     * which the unwrap loop then drains in one locked pass (compacting once, not per record). For
     * bulk-transfer workloads (e.g. large S3 GETs) this collapses read syscalls, read-deadline
     * watchdog arms, epoll registrations, and virtual-thread park/unpark cycles roughly in
     * proportion to the records-per-read ratio.
     *
     * <p><b>Only affects the {@code SSLEngineTransport} TLS path</b> — i.e. when a custom
     * {@link #sslEngineFactory} is set (such as BoringSSL), or for secure routes not forced to
     * {@code ENFORCE_HTTP_1_1} with the JDK engine. The JDK {@code SSLSocket} fast path is
     * unaffected. To realize the syscall collapse, pair a large value with a {@code SO_RCVBUF}
     * (see {@link #socketReceiveBufferSize}) large enough for the kernel to deliver that much in one
     * read.
     *
     * <p><b>Memory note:</b> this buffer is allocated per connection (plus an equal-or-larger
     * plaintext buffer), so a large value multiplied across many concurrent connections raises
     * steady-state footprint. Leave at the default unless the workload moves large bodies.
     *
     * @param bytes ciphertext-read buffer size in bytes; values below one TLS record are raised to it
     * @return this builder
     * @throws IllegalArgumentException if {@code bytes} is not positive
     */
    public HttpConnectionPoolBuilder tlsReadBufferSize(int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("tlsReadBufferSize must be positive: " + bytes);
        }
        this.tlsReadBufferSize = bytes;
        return this;
    }

    /**
     * Set the TLS ciphertext-write buffer size in bytes for the {@link SSLEngineTransport} path
     * (default: 16384, one TLS record).
     *
     * <p>This buffer holds ciphertext produced by {@code SSLEngine.wrap} before it is written to the
     * socket. At the default of one TLS record, each wrapped record is written with its own socket
     * write. A larger value lets the stream write path accumulate several records and flush them in
     * one socket write, which for bulk uploads (e.g. large S3 PUTs) collapses write syscalls and the
     * attendant virtual-thread park/unpark cycles roughly in proportion to records-per-flush.
     *
     * <p><b>Only affects the {@code SSLEngineTransport} TLS path</b> (custom {@link #sslEngineFactory}
     * such as BoringSSL, or secure routes not forced to {@code ENFORCE_HTTP_1_1} with the JDK
     * engine). The JDK {@code SSLSocket} fast path is unaffected. Pair with a {@code SO_SNDBUF}
     * (see {@link #socketSendBufferSize}) large enough to absorb the coalesced write.
     *
     * <p><b>Memory note:</b> allocated per connection; a large value across many concurrent
     * connections raises steady-state footprint. Leave at the default unless the workload uploads
     * large bodies.
     *
     * @param bytes ciphertext-write buffer size in bytes; values below one TLS record are raised to it
     * @return this builder
     * @throws IllegalArgumentException if {@code bytes} is not positive
     */
    public HttpConnectionPoolBuilder tlsWriteBufferSize(int bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("tlsWriteBufferSize must be positive: " + bytes);
        }
        this.tlsWriteBufferSize = bytes;
        return this;
    }

    /**
     * Set HTTP/2 initial window size for flow control (default: 65535 bytes).
     *
     * <p>This controls the initial flow control window size advertised to the server
     * for both connection-level and stream-level flow control. Larger values allow
     * more data to be sent before waiting for WINDOW_UPDATE frames, which improves
     * throughput for large payloads.
     *
     * <p><b>Performance considerations:</b>
     * <ul>
     *   <li>Default (65535): RFC 9113 default, conservative memory usage</li>
     *   <li>1MB (1048576): Good for large response bodies, reduces WINDOW_UPDATE overhead</li>
     *   <li>Higher values: Better throughput but more memory per stream</li>
     * </ul>
     *
     * <p>For workloads with large response bodies (e.g., file downloads, large API responses),
     * consider setting this to 1MB or higher to reduce flow control overhead.
     *
     * @param windowSize initial window size in bytes, must be between 1 and 2^31-1
     * @return this builder
     * @throws IllegalArgumentException if windowSize is not in valid range
     */
    public HttpConnectionPoolBuilder h2InitialWindowSize(int windowSize) {
        if (windowSize <= 0) {
            throw new IllegalArgumentException("h2InitialWindowSize must be positive: " + windowSize);
        }
        this.h2InitialWindowSize = windowSize;
        return this;
    }

    /**
     * Set HTTP/2 maximum frame size for receiving DATA frames (default: 16384 bytes).
     *
     * <p>This controls the SETTINGS_MAX_FRAME_SIZE advertised to the server,
     * which determines the maximum size of DATA frames the server can send.
     * Larger frames reduce per-frame overhead and can improve throughput for
     * large response bodies.
     *
     * <p><b>Performance considerations:</b>
     * <ul>
     *   <li>Default (16384): RFC 9113 minimum, maximum compatibility</li>
     *   <li>65536 (64KB): Good balance of throughput and memory</li>
     *   <li>262144 (256KB): Better for large downloads, reduces frame overhead</li>
     * </ul>
     *
     * <p><b>Note:</b> The actual frame size used depends on the server respecting
     * this setting. Some servers may send smaller frames regardless.
     *
     * @param frameSize maximum frame size in bytes, must be between 16384 and 16777215
     * @return this builder
     * @throws IllegalArgumentException if frameSize is not in valid range
     */
    public HttpConnectionPoolBuilder h2MaxFrameSize(int frameSize) {
        if (frameSize < 16384 || frameSize > 16777215) {
            throw new IllegalArgumentException(
                    "h2MaxFrameSize must be between 16384 and 16777215: " + frameSize);
        }
        this.h2MaxFrameSize = frameSize;
        return this;
    }

    /**
     * Set maximum concurrent streams per HTTP/2 connection before creating a new connection (default: 100).
     *
     * <p>This is a <b>soft limit</b> that controls when the pool creates additional HTTP/2 connections
     * to spread load. When an existing connection reaches this many active streams, the pool
     * will prefer to create a new connection for the next request (subject to {@link #maxConnectionsPerRoute(int)}
     * and {@link #maxTotalConnections(int)}).
     *
     * <p><b>Important:</b> This limit can be exceeded when the connection limit is reached. If all
     * connections are at or above this soft limit, the pool will still multiplex additional streams
     * on existing connections rather than blocking or failing, up to the server's hard limit
     * ({@code SETTINGS_MAX_CONCURRENT_STREAMS}).
     *
     * <p>This is distinct from the server's {@code SETTINGS_MAX_CONCURRENT_STREAMS}, which is
     * a hard limit enforced by the server. This client-side soft limit helps balance load across
     * multiple connections to reduce lock contention and improve throughput under high concurrency.
     *
     * <p><a href="https://www.rfc-editor.org/rfc/rfc9113#section-6.5.2">RFC 9113 Section 6.5.2</a>
     * recommends servers set {@code SETTINGS_MAX_CONCURRENT_STREAMS} to at least 100 to avoid
     * unnecessarily limiting parallelism. This default aligns with that recommendation and matches
     * <a href="https://go.googlesource.com/net/+/master/http2/transport.go">Go's net/http</a>
     * default of 100.
     *
     * <p><b>Performance considerations:</b> Lower values create more connections but reduce
     * per-connection lock contention. Higher values use fewer connections but may increase
     * contention under high concurrency.
     *
     * <p><b>Note:</b> This setting only applies to HTTP/2 connections. HTTP/1.1 connections
     * handle one request at a time and are managed by {@link #maxConnectionsPerRoute(int)}.
     *
     * @param streams maximum streams per connection, must be positive
     * @return this builder
     * @throws IllegalArgumentException if streams is not positive
     */
    public HttpConnectionPoolBuilder h2StreamsPerConnection(int streams) {
        if (streams <= 0) {
            throw new IllegalArgumentException("h2StreamsPerConnection must be positive: " + streams);
        }
        this.h2StreamsPerConnection = streams;
        return this;
    }

    /**
     * Set HTTP/2 I/O buffer size (default: 256KB).
     *
     * <p>This controls the size of the buffered input and output streams used for
     * reading and writing HTTP/2 frames. Larger buffers reduce syscall overhead
     * and improve throughput for large payloads.
     *
     * <p><b>Memory impact:</b> Each HTTP/2 connection uses 2× this value (input + output).
     * With 100 connections at 256KB, total buffer memory is ~50MB.
     *
     * @param bufferSize buffer size in bytes, must be at least 16KB
     * @return this builder
     * @throws IllegalArgumentException if bufferSize is less than 16KB
     */
    public HttpConnectionPoolBuilder h2BufferSize(int bufferSize) {
        if (bufferSize < 16 * 1024) {
            throw new IllegalArgumentException("h2BufferSize must be at least 16KB: " + bufferSize);
        }
        this.h2BufferSize = bufferSize;
        return this;
    }

    /**
     * Use a dedicated platform thread for the HTTP/2 reader loop instead of a virtual thread.
     *
     * <p>This is an experimental toggle intended for benchmarking the interaction between
     * the shipped split read/write H2 architecture and JSSE TLS.
     */
    public HttpConnectionPoolBuilder usePlatformReaderForH2(boolean enabled) {
        this.usePlatformReaderForH2 = enabled;
        return this;
    }

    public HttpConnectionPoolBuilder useEpollTransport(boolean enabled) {
        this.useEpollTransport = enabled;
        return this;
    }

    /**
     * Add a listener for HTTP client lifecycle events.
     *
     * <p>Listeners are notified of connection creation, acquisition, release, eviction, and connection setup events.
     * Multiple listeners can be added and are called in order. Listeners are called synchronously, so calls should be
     * fast.
     *
     * @param listener the listener to add
     * @return this builder
     * @throws NullPointerException if listener is null
     * @see HttpClientListener
     */
    public HttpConnectionPoolBuilder addListener(HttpClientListener listener) {
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
     * @see #addListener(HttpClientListener)
     */
    public HttpConnectionPoolBuilder addListenerFirst(HttpClientListener listener) {
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
