/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import software.amazon.smithy.java.http.client.h1.Http1Connection;

/**
 * Connection pool that manages HTTP connections per host (scheme://host:port).
 * Thread-safe.
 */
public final class HostBasedConnectionPool implements ConnectionPool {

    private final int maxConnectionsPerHost;
    private final Duration maxIdleTime;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final SSLContext sslContext;
    private final boolean http2PriorKnowledge;

    // Pool structure: poolKey -> HostPool
    private final ConcurrentHashMap<String, HostPool> pools = new ConcurrentHashMap<>();

    // Track active connections for stats
    private final AtomicInteger totalActive = new AtomicInteger(0);

    // Cleanup thread for idle connections
    private final Thread cleanupThread;
    private volatile boolean closed = false;

    private HostBasedConnectionPool(Builder builder) {
        this.maxConnectionsPerHost = builder.maxConnectionsPerHost;
        this.maxIdleTime = builder.maxIdleTime;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.sslContext = builder.sslContext;
        this.http2PriorKnowledge = builder.http2PriorKnowledge;

        // Start background cleanup thread
        this.cleanupThread = Thread.ofVirtual()
                .name("http-pool-cleanup")
                .start(this::cleanupIdleConnections);
    }

    @Override
    public HttpConnection acquire(URI uri) throws IOException {
        if (closed) {
            throw new IOException("Connection pool is closed");
        }

        String poolKey = getPoolKey(uri);
        HostPool hostPool = pools.computeIfAbsent(poolKey,
                k -> new HostPool(maxConnectionsPerHost));

        // Try to get an existing connection from the pool
        PooledConnection pooled = hostPool.poll();

        if (pooled != null && pooled.connection.isActive()) {
            totalActive.incrementAndGet();
            return pooled.connection;
        }

        // Stale connection, close it
        if (pooled != null) {
            try {
                pooled.connection.close();
            } catch (IOException ignored) {
                // Ignore errors closing stale connection
            }
        }

        // Create new connection
        HttpConnection conn = createConnection(uri);
        totalActive.incrementAndGet();
        return conn;
    }

    @Override
    public void release(HttpConnection connection, URI uri) {
        totalActive.decrementAndGet();

        if (!connection.isActive() || closed) {
            // Don't pool inactive connections
            try {
                connection.close();
            } catch (IOException ignored) {
                // Ignore errors closing
            }
            return;
        }

        String poolKey = getPoolKey(uri);
        HostPool hostPool = pools.get(poolKey);

        if (hostPool != null) {
            boolean added = hostPool.offer(new PooledConnection(connection, Instant.now()));
            if (!added) {
                // Pool is full, close the connection
                try {
                    connection.close();
                } catch (IOException ignored) {
                    // Ignore errors closing
                }
            }
        } else {
            // Pool was removed (shouldn't happen), close connection
            try {
                connection.close();
            } catch (IOException ignored) {
                // Ignore errors closing
            }
        }
    }

    @Override
    public void evict(HttpConnection connection, URI uri) {
        totalActive.decrementAndGet();

        try {
            connection.close();
        } catch (IOException ignored) {
            // Ignore errors closing
        }

        // Remove from pool if present
        String poolKey = getPoolKey(uri);
        HostPool hostPool = pools.get(poolKey);
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

        // Interrupt cleanup thread
        cleanupThread.interrupt();

        // Close all pooled connections
        List<IOException> exceptions = new ArrayList<>();

        for (HostPool pool : pools.values()) {
            pool.closeAll(exceptions);
        }

        pools.clear();

        // If there were errors closing connections, report them
        if (!exceptions.isEmpty()) {
            IOException e = new IOException("Errors closing connections");
            exceptions.forEach(e::addSuppressed);
            throw e;
        }
    }

    @Override
    public Stats stats() {
        Map<String, Integer> perHost = new HashMap<>();
        int totalIdle = 0;

        for (Map.Entry<String, HostPool> entry : pools.entrySet()) {
            int idle = entry.getValue().size();
            perHost.put(entry.getKey(), idle);
            totalIdle += idle;
        }

        int active = totalActive.get();
        int total = active + totalIdle;
        return new Stats(total, active, totalIdle, Collections.unmodifiableMap(perHost));
    }

    /**
     * Get pool key for a URI: scheme://host:port
     */
    private String getPoolKey(URI uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();

        if (port == -1) {
            port = "https".equals(scheme) ? 443 : 80;
        }

        return scheme + "://" + host + ":" + port;
    }

    /**
     * Create a new connection for the given URI.
     */
    private HttpConnection createConnection(URI uri) throws IOException {
        String host = uri.getHost();
        int port = uri.getPort();

        if (port == -1) {
            port = "https".equals(uri.getScheme()) ? 443 : 80;
        }

        // Create socket with timeout
        Socket socket = new Socket();
        try {
            socket.connect(
                    new InetSocketAddress(host, port),
                    (int) connectTimeout.toMillis());
            // Set read timeout
            socket.setSoTimeout((int) readTimeout.toMillis());
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Ignore
            }
            throw e;
        }

        String protocol = "http/1.1";

        // TLS negotiation with ALPN
        if ("https".equals(uri.getScheme())) {
            try {
                SSLSocket sslSocket = (SSLSocket) sslContext.getSocketFactory()
                        .createSocket(socket, host, port, true);

                // Set read timeout on SSL socket
                sslSocket.setSoTimeout((int) readTimeout.toMillis());

                // Configure ALPN and security
                SSLParameters params = sslSocket.getSSLParameters();
                // hostname verification to prevent MITM attacks
                params.setEndpointIdentificationAlgorithm("HTTPS");

                if (http2PriorKnowledge) {
                    params.setApplicationProtocols(new String[] {"h2"});
                } else {
                    // Only offer HTTP/1.1 since h2 is not implemented yet
                    params.setApplicationProtocols(new String[] {"http/1.1"});
                }
                sslSocket.setSSLParameters(params);

                // Perform handshake
                sslSocket.startHandshake();

                // Get negotiated protocol
                protocol = sslSocket.getApplicationProtocol();
                if (protocol == null || protocol.isEmpty()) {
                    protocol = "http/1.1"; // Fallback if ALPN not supported
                }

                socket = sslSocket;

            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // Ignore
                }
                throw e;
            }
        } else if (http2PriorKnowledge) {
            // HTTP/2 over cleartext (h2c) with prior knowledge
            protocol = "h2";
        }

        // Create protocol-specific connection
        try {
            if ("h2".equals(protocol)) {
                throw new RuntimeException("Not yet implemented");
            } else {
                return new Http1Connection(socket);
            }
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Ignore
            }
            throw e;
        }
    }

    /**
     * Background task to clean up idle connections.
     */
    private void cleanupIdleConnections() {
        while (!closed) {
            try {
                Thread.sleep(Duration.ofSeconds(30));

                Instant cutoff = Instant.now().minus(maxIdleTime);

                for (HostPool pool : pools.values()) {
                    pool.removeIdleConnections(cutoff);
                }

            } catch (InterruptedException e) {
                // Shutting down
                break;
            }
        }
    }

    /**
     * Per-host connection pool.
     */
    private static class HostPool {
        private final int maxConnections;
        private final BlockingQueue<PooledConnection> available;
        private final AtomicInteger size = new AtomicInteger(0);

        HostPool(int maxConnections) {
            this.maxConnections = maxConnections;
            this.available = new ArrayBlockingQueue<>(maxConnections);
        }

        /**
         * Get a connection from the pool (non-blocking).
         */
        PooledConnection poll() {
            PooledConnection pc = available.poll();
            if (pc != null) {
                size.decrementAndGet();
            }
            return pc;
        }

        /**
         * Return a connection to the pool.
         */
        boolean offer(PooledConnection connection) {
            if (size.get() >= maxConnections) {
                return false; // Pool is full
            }

            boolean added = available.offer(connection);
            if (added) {
                size.incrementAndGet();
            }
            return added;
        }

        /**
         * Remove a specific connection from the pool.
         */
        void remove(HttpConnection connection) {
            available.removeIf(pc -> pc.connection == connection);
            size.set(available.size());
        }

        /**
         * Get current pool size.
         */
        int size() {
            return size.get();
        }

        /**
         * Remove connections idle since before the cutoff time.
         */
        void removeIdleConnections(Instant cutoff) {
            available.removeIf(pc -> {
                if (pc.idleSince.isBefore(cutoff) || !pc.connection.isActive()) {
                    try {
                        pc.connection.close();
                    } catch (IOException ignored) {
                        // Ignore errors closing
                    }
                    size.decrementAndGet();
                    return true;
                }
                return false;
            });
        }

        /**
         * Close all connections in this pool.
         */
        void closeAll(List<IOException> exceptions) {
            PooledConnection pc;
            while ((pc = available.poll()) != null) {
                try {
                    pc.connection.close();
                } catch (IOException e) {
                    exceptions.add(e);
                }
            }
            size.set(0);
        }
    }

    /**
     * Wrapper for a pooled connection with idle time tracking.
     */
    private static class PooledConnection {
        final HttpConnection connection;
        final Instant idleSince;

        PooledConnection(HttpConnection connection, Instant idleSince) {
            this.connection = connection;
            this.idleSince = idleSince;
        }
    }

    /**
     * Builder for HostBasedConnectionPool.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int maxConnectionsPerHost = 5;
        private Duration maxIdleTime = Duration.ofMinutes(2);
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private SSLContext sslContext;
        private boolean http2PriorKnowledge = false;

        /**
         * Set maximum connections per host (default: 5).
         */
        public Builder maxConnectionsPerHost(int max) {
            if (max <= 0) {
                throw new IllegalArgumentException("maxConnectionsPerHost must be positive: " + max);
            }
            this.maxConnectionsPerHost = max;
            return this;
        }

        /**
         * Set maximum idle time before connections are closed (default: 2 minutes).
         */
        public Builder maxIdleTime(Duration duration) {
            if (duration == null || duration.isNegative() || duration.isZero()) {
                throw new IllegalArgumentException("maxIdleTime must be positive: " + duration);
            }
            this.maxIdleTime = duration;
            return this;
        }

        /**
         * Set connection timeout (default: 10 seconds).
         */
        public Builder connectTimeout(Duration timeout) {
            if (timeout == null || timeout.isNegative()) {
                throw new IllegalArgumentException("connectTimeout must be non-negative: " + timeout);
            }
            this.connectTimeout = timeout;
            return this;
        }

        /**
         * Set read timeout (default: 30 seconds).
         */
        public Builder readTimeout(Duration timeout) {
            if (timeout == null || timeout.isNegative()) {
                throw new IllegalArgumentException("readTimeout must be non-negative: " + timeout);
            }
            this.readTimeout = timeout;
            return this;
        }

        /**
         * Set SSL context for HTTPS connections.
         * If not provided, the default SSL context is used.
         */
        public Builder sslContext(SSLContext context) {
            this.sslContext = context;
            return this;
        }

        /**
         * Enable HTTP/2 prior knowledge (h2c without upgrade).
         * Default: false (negotiate via ALPN for HTTPS, or use HTTP/1.1 for HTTP).
         */
        public Builder http2PriorKnowledge(boolean enable) {
            this.http2PriorKnowledge = enable;
            return this;
        }

        /**
         * Build the connection pool.
         */
        public HostBasedConnectionPool build() {
            // Use default SSL context if not provided
            if (sslContext == null) {
                try {
                    sslContext = SSLContext.getDefault();
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException("Failed to get default SSLContext", e);
                }
            }

            return new HostBasedConnectionPool(this);
        }
    }
}
