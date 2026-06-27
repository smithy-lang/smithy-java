/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.io.IOException;
import java.net.Socket;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import software.amazon.smithy.java.http.client.HttpClientListener;
import software.amazon.smithy.java.http.client.RequestOptions;
import software.amazon.smithy.java.http.client.dns.DnsResolver;

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
 * Route route1 = Route.from(SmithyUri.of("https://api.example.com/users"));
 * Route route2 = Route.from(SmithyUri.of("https://api.example.com/posts"));
 * // route1.equals(route2) == true, so connections are shared
 * }</pre>
 *
 * <h2>Per-Route Connection Limits</h2>
 * <p>You can set different connection limits for different hosts:
 *
 * <p>Connection limits are configured through {@link ConnectionConfig}. Most callers should set them on
 * {@link software.amazon.smithy.java.http.client.HttpClient.Builder}, which creates the config and default pool.
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
 * HttpConnection conn = pool.acquire(route, exchangeId);
 * }</pre>
 *
 * <h2>Pool Exhaustion and Backpressure</h2>
 * <p>When route capacity, stream capacity, or {@code maxTotalConnections} is exhausted,
 * {@link #acquire} blocks for up to {@code acquireTimeout} (default: 30 seconds)
 * waiting for capacity to become available. This behavior is consistent for both HTTP/1.1
 * and HTTP/2 connections.
 *
 * <p>The blocking wait is on the global physical-connection semaphore, so callers
 * unblock when an open connection is closed and releases capacity. With virtual
 * threads, this blocking is cheap and provides natural backpressure under load.
 *
 * <p>Configure via {@link software.amazon.smithy.java.http.client.HttpClient.Builder#acquireTimeout(Duration)}:
 * <ul>
 *   <li>Default (30s): Good backpressure for load spikes, requests queue briefly</li>
 *   <li>{@link Duration#ZERO}: Fail-fast behavior, immediate failure when exhausted</li>
 *   <li>Longer timeout: More tolerance for sustained high load</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Create pool directly when implementing a custom client/pool factory.
 * ConnectionConfig config = ConnectionConfig.builder()
 *     .maxConnectionsPerRoute(20)
 *     .maxTotalConnections(200)
 *     .maxIdleTime(Duration.ofMinutes(2))
 *     .sslContext(customSSLContext)
 *     .httpVersionPolicy(HttpVersionPolicy.AUTOMATIC)
 *     .build();
 * HttpConnectionPool pool = new HttpConnectionPool(config);
 *
 * // Acquire connection
 * Route route = Route.from(SmithyUri.of("https://api.example.com/users"));
 * HttpConnection conn = pool.acquire(route, exchangeId);
 *
 * try {
 *     // Use connection
 *     HttpExchange exchange = conn.newExchange(request);
 *     // ...
 * } finally {
 *     // Return to pool for reuse
 *     pool.release(conn);
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

    private final int maxConnectionsPerRoute;
    private final int maxTotalConnections;
    private final long acquireTimeoutMs; // Timeout for acquiring a connection when pool is exhausted
    private final long maxIdleTimeNanos; // Max idle time before closing connections
    private final HttpVersionPolicy versionPolicy;
    private final HttpConnectionFactory connectionFactory;

    // HTTP/1.1 connection manager (handles pooling)
    private final H1ConnectionManager h1Manager;

    // HTTP/2 connection manager (handles multiplexing)
    private final H2ConnectionManager h2Manager;

    // Semaphore to limit total open physical connections - better contention than AtomicInteger CAS loop.
    // H1 idle sockets hold permits while pooled; H2 sockets hold permits until closed.
    private final Semaphore connectionPermits;

    // Cleanup thread
    private final Thread cleanupThread;
    private volatile boolean closed = false;

    // Shared single-thread watchdog enforcing read deadlines for the SSLEngineTransport channel path.
    // A blocking SocketChannel.read ignores SO_TIMEOUT, so instead of opening an epoll Selector per
    // read (epoll_create1/eventfd/close churn) the read parks the VT and this timer closes the channel
    // if the deadline passes. One wheel for the whole pool, giving O(1) arm/cancel per read.
    private final HashedWheelTimer readTimer;

    // Listeners for client lifecycle events
    private final List<HttpClientListener> listeners;
    private final boolean hasListeners;

    public HttpConnectionPool(ConnectionConfig config) {
        this.maxConnectionsPerRoute = config.maxConnectionsPerRoute();
        this.maxTotalConnections = config.maxTotalConnections();
        // Cached to avoid Duration.toNanos() in hot path
        this.maxIdleTimeNanos = config.maxIdleTime().toNanos();
        this.acquireTimeoutMs = config.acquireTimeout().toMillis();
        this.versionPolicy = config.versionPolicy();
        DnsResolver dnsResolver = config.dnsResolver() != null ? config.dnsResolver() : DnsResolver.roundRobin();

        this.readTimer = new HashedWheelTimer(
                new DefaultThreadFactory("smithy-http-read-timeout", true),
                100,
                TimeUnit.MILLISECONDS);

        this.listeners = config.listeners();
        this.hasListeners = !listeners.isEmpty();

        TlsProvider tls = resolveTls(config);

        // Use the epoll backend only when the native library is available AND the resolved TLS provider
        // supports it AND the user has not supplied a custom socket factory. The epoll path hands the
        // provider a null-socket context whose byte channel is consumable only by engine-based providers
        // (via SslEngineTransports); a provider that does its own socket I/O (supportsEpoll() == false)
        // must get the NIO socket path so socket() is non-null. Note: this also routes cleartext
        // connections on such a client through NIO, which is acceptable, since a custom TLS provider is
        // configured for secure traffic. A custom socketFactory likewise forces the NIO path: the epoll
        // connector creates its own channels and would otherwise silently bypass the user's factory.
        EpollConnector epollConnector = tls.supportsEpoll() && config.socketFactory() == null
                ? EpollConnector.createIfAvailable(
                        config.socketReceiveBufferSize(),
                        config.socketSendBufferSize(),
                        readTimer)
                : null;

        this.connectionFactory = new HttpConnectionFactory(
                config.connectTimeout(),
                config.tlsNegotiationTimeout(),
                config.readTimeout(),
                config.writeTimeout(),
                config.sslContext(),
                config.sslParameters(),
                tls,
                config.versionPolicy(),
                dnsResolver,
                listeners,
                !listeners.isEmpty(),
                resolveSocketFactory(config),
                readTimer,
                epollConnector,
                config.h2InitialWindowSize(),
                config.h2MaxFrameSize(),
                config.h2BufferSize(),
                config.tlsReadBufferSize(),
                config.tlsWriteBufferSize());

        this.h1Manager = new H1ConnectionManager(this.maxIdleTimeNanos);
        this.connectionPermits = new Semaphore(config.maxTotalConnections(), false);
        this.h2Manager = new H2ConnectionManager(config.h2StreamsPerConnection(),
                this.acquireTimeoutMs,
                listeners,
                this::onNewH2Connection);
        this.cleanupThread = Thread.ofVirtual().name("http-pool-cleanup").start(this::cleanupIdleConnections);
    }

    @Override
    public HttpConnection acquire(Route route, long exchangeId, RequestOptions options) throws IOException {
        if (closed) {
            throw new IllegalStateException("Connection pool is closed");
        } else if ((route.isSecure() && versionPolicy != HttpVersionPolicy.ENFORCE_HTTP_1_1)
                || (!route.isSecure() && versionPolicy.usesH2cForCleartext())) {
            return h2Manager.acquire(route, maxConnectionsPerRoute, exchangeId, options);
        } else {
            return acquireH1(route, exchangeId, options);
        }
    }

    // Per-request acquire timeout override falls back to the pool default.
    private long acquireTimeoutMs(RequestOptions options) {
        return options.acquireTimeout() != null ? options.acquireTimeout().toMillis() : acquireTimeoutMs;
    }

    private HttpConnection acquireH1(Route route, long exchangeId, RequestOptions options) throws IOException {
        int maxConns = maxConnectionsPerRoute;
        long timeoutMs = acquireTimeoutMs(options);

        h1Manager.acquireActive(route, maxConns, timeoutMs);

        try {
            // Idle H1 connections already hold a global connection permit.
            HttpConnection pooled = h1Manager.tryAcquire(route, maxConns, this::releaseIdleH1Permit);
            if (pooled != null) {
                notifyAcquire(pooled, true);
                return pooled;
            }

            // No pooled connection, so acquire global capacity for a new physical socket.
            acquirePermit(timeoutMs);

            // Re-check the pool: a connection may have been released while we waited. If we reuse one,
            // give back the just-acquired permit since the idle socket already owns one.
            pooled = h1Manager.tryAcquire(route, maxConns, this::releaseIdleH1Permit);
            if (pooled != null) {
                connectionPermits.release();
                notifyAcquire(pooled, true);
                return pooled;
            }

            return createH1Connection(route, exchangeId, options);
        } catch (IOException | RuntimeException e) {
            h1Manager.releaseActive(route);
            throw e;
        }
    }

    private HttpConnection createH1Connection(Route route, long exchangeId, RequestOptions options) throws IOException {
        HttpConnection conn = null;
        boolean success = false;
        try {
            conn = connectionFactory.create(route, exchangeId, options);
            notifyConnected(conn);
            notifyAcquire(conn, false);
            success = true;
            return conn;
        } catch (Exception e) {
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException(e);
        } finally {
            if (!success) {
                connectionPermits.release();
                if (conn != null) {
                    closeConnection(conn);
                }
            }
        }
    }

    // Called by H2ConnectionManager when a new connection is needed.
    private MultiplexedHttpConnection onNewH2Connection(Route route, long exchangeId, RequestOptions options)
            throws IOException {
        // Dead-connection cleanup is left to the background thread; doing it here caused lock contention.
        acquirePermit(acquireTimeoutMs(options));

        HttpConnection conn = null;
        boolean success = false;
        try {
            conn = connectionFactory.create(route, exchangeId, options);
            notifyConnected(conn);
            if (conn instanceof MultiplexedHttpConnection h2conn) {
                success = true;
                return h2conn;
            }
            // ALPN negotiated HTTP/1.1 instead of H2 - shouldn't happen with H2C_PRIOR_KNOWLEDGE
            throw new IOException("Expected H2 connection but got " + conn.httpVersion());
        } catch (Exception e) {
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException(e);
        } finally {
            if (!success) {
                connectionPermits.release();
                if (conn != null) {
                    closeConnection(conn);
                }
            }
        }
    }

    /**
     * Acquire a connection permit, blocking up to acquireTimeout.
     */
    private void acquirePermit(long timeoutMs) throws IOException {
        try {
            if (!connectionPermits.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IOException("Connection pool exhausted: " + maxTotalConnections +
                        " connections in use (timed out after " + timeoutMs + "ms)");
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
        if (connection instanceof MultiplexedHttpConnection h2conn) {
            if (!connection.isActive() || closed) {
                h2Manager.unregister(route, h2conn);
                closeAndReleasePermit(connection, CloseReason.UNEXPECTED_CLOSE);
            }
            return;
        }

        if (!h1Manager.release(route, connection, closed)) {
            closeAndReleasePermit(connection, CloseReason.POOL_FULL);
        }
    }

    @Override
    public void evict(HttpConnection connection, boolean isError) {
        Objects.requireNonNull(connection, "connection cannot be null");
        Route route = connection.route();

        if (connection instanceof MultiplexedHttpConnection h2conn) {
            h2Manager.unregister(route, h2conn);
        } else {
            h1Manager.remove(route, connection);
            h1Manager.releaseActive(route);
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
        readTimer.stop();

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
        h1Manager.closeAll(exceptions, conn -> {
            notifyClosed(conn, CloseReason.POOL_SHUTDOWN);
            connectionPermits.release();
        });

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
        readTimer.stop();

        // Wait for connections to be closed (permits represent physical connections, not streams).
        // For HTTP/2, permits are released when the connection closes, not when streams finish.
        Instant deadline = Instant.now().plus(gracePeriod);
        while (connectionPermits.availablePermits() < maxTotalConnections && Instant.now().isBefore(deadline)) {
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
     * Close a connection, ignoring any IOException.
     *
     * @param connection the connection to close
     */
    private void closeConnection(HttpConnection connection) {
        try {
            connection.close();
        } catch (IOException ignored) {
            // best effort
        }
    }

    /**
     * Close a connection, notify listeners, and release its permit.
     */
    private void closeAndReleasePermit(HttpConnection connection, CloseReason reason) {
        try {
            closeConnection(connection);
            notifyClosed(connection, reason);
        } finally {
            // Release the permit unconditionally: a listener (or close) failure must never leak a
            // permit, or the pool slowly exhausts and every acquire eventually blocks.
            connectionPermits.release();
        }
    }

    private void notifyConnected(HttpConnection connection) {
        if (hasListeners) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onConnectionCreated(connection);
                } catch (Throwable e) {
                    ListenerSupport.listenerFailed("onConnectionCreated", e);
                }
            }
        }
    }

    private void notifyAcquire(HttpConnection connection, boolean reused) {
        if (hasListeners) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onConnectionAcquired(connection, reused);
                } catch (Throwable e) {
                    ListenerSupport.listenerFailed("onConnectionAcquired", e);
                }
            }
        }
    }

    private void notifyReturn(HttpConnection connection) {
        if (hasListeners) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onConnectionReturned(connection);
                } catch (Throwable e) {
                    ListenerSupport.listenerFailed("onConnectionReturned", e);
                }
            }
        }
    }

    private void notifyClosed(HttpConnection connection, CloseReason reason) {
        if (hasListeners) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onConnectionClosed(connection, reason);
                } catch (Throwable e) {
                    ListenerSupport.listenerFailed("onConnectionClosed", e);
                }
            }
        }
    }

    private void releaseIdleH1Permit(HttpConnection connection, CloseReason reason) {
        try {
            notifyClosed(connection, reason);
        } finally {
            connectionPermits.release();
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
     *   <li>Have no active streams and have been idle longer than {@code maxIdleTime}</li>
     * </ul>
     *
     * <p>Runs on a virtual thread, so blocking is cheap.
     */
    private void cleanupIdleConnections() {
        while (!closed) {
            try {
                Thread.sleep(Duration.ofSeconds(30));
                h1Manager.cleanupIdle(this::releaseIdleH1Permit);
                h2Manager.cleanupAllDead(this::closeAndReleasePermit);
                h2Manager.cleanupIdle(maxIdleTimeNanos, this::closeAndReleasePermit);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    // Resolve once per pool: an explicit provider from the config wins; otherwise an opt-in provider
    // selected by the smithy-java.tls-provider system property; otherwise the built-in JDK provider
    // configured from the convenience sslContext/sslParameters. The JDK provider picks its own
    // SSLSocket-vs-SSLEngine strategy per connection.
    private static TlsProvider resolveTls(ConnectionConfig config) {
        if (config.tlsProvider() != null) {
            return config.tlsProvider();
        }
        var discovered = TlsProvider.fromSystemProperty();
        if (discovered != null) {
            return discovered;
        }
        return JdkTlsProvider.builder()
                .sslContext(config.sslContext())
                .sslParameters(config.sslParameters())
                .build();
    }

    private static HttpSocketFactory resolveSocketFactory(ConnectionConfig config) {
        // A user-supplied factory is honored verbatim.
        if (config.socketFactory() != null) {
            return config.socketFactory();
        }
        Integer recv = config.socketReceiveBufferSize();
        Integer send = config.socketSendBufferSize();
        if (recv == null && send == null) {
            return HttpSocketFactory.DEFAULT;
        }
        return (route, endpoints) -> {
            Socket socket = SocketChannel.open().socket();
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            if (send != null && send != -1) {
                socket.setSendBufferSize(send);
            }
            if (recv != null && recv != -1) {
                socket.setReceiveBufferSize(recv);
            }
            return socket;
        };
    }
}
