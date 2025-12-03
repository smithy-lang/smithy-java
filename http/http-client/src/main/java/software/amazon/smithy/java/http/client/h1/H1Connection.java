/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.http.client.ProxyConfiguration;
import software.amazon.smithy.java.http.client.UnsyncBufferedInputStream;
import software.amazon.smithy.java.http.client.UnsyncBufferedOutputStream;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * HTTP/1.1 connection implementation.
 *
 * <p>Manages a single TCP socket for HTTP/1.1 communication. HTTP/1.1 allows only one request/response exchange at
 * a time (no multiplexing like HTTP/2).
 *
 * <h2>Connection Reuse</h2>
 * <p>Supports HTTP/1.1 persistent connections (keep-alive). After each exchange, the connection can be returned to
 * the pool for reuse if:
 * <ul>
 *   <li>The server sent "Connection: keep-alive" (or didn't send "Connection: close")</li>
 *   <li>The response body was fully read</li>
 *   <li>No errors occurred during the exchange</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe for {@link #newExchange(HttpRequest)} - only one exchange can be active at a time.
 * Concurrent calls to {@code newExchange()} will fail with an exception if another exchange is already active.
 *
 * <h2>Proxy Support</h2>
 * <p>If created through an HTTP proxy with CONNECT tunnel (for HTTPS), the underlying socket is already connected
 * through the tunnel. All proxy handshaking happens during connection establishment, not in this class.
 */
public final class H1Connection implements HttpConnection {
    /**
     * The buffer used for parsing response start-line and each header line. This means that the path and query
     * string can't exceed 8KB, and no one header can exceed 8KB (though we do impose a limit of 512 headers to guard
     * against malformed responses). This per/line limit should be more than enough for well-formed response parsing.
     */
    static final int RESPONSE_LINE_BUFFER_SIZE = 8192;

    private static final InternalLogger LOGGER = InternalLogger.getLogger(H1Connection.class);

    private final Socket socket;
    private final UnsyncBufferedInputStream socketIn;
    private final UnsyncBufferedOutputStream socketOut;
    private final Route route;
    private final byte[] lineBuffer; // Reused across exchanges for header parsing

    // HTTP/1.1: only one exchange at a time
    private final AtomicBoolean inUse = new AtomicBoolean(false);
    private volatile boolean keepAlive = true;
    private volatile boolean active = true;

    /**
     * Create an HTTP/1.1 connection from a connected socket with timeout.
     *
     * <p>The socket must already be connected (and if using HTTPS, TLS handshake
     * must be complete).
     *
     * @param socket the connected socket
     * @param route Connection route
     * @param readTimeout timeout for read operations (applied via SO_TIMEOUT)
     * @throws IOException if socket streams cannot be obtained
     */
    public H1Connection(Socket socket, Route route, Duration readTimeout) throws IOException {
        this.socket = socket;
        this.socketIn = new UnsyncBufferedInputStream(socket.getInputStream(), 8192);
        this.socketOut = new UnsyncBufferedOutputStream(socket.getOutputStream(), 8192);
        this.route = route;
        this.lineBuffer = new byte[RESPONSE_LINE_BUFFER_SIZE];

        // Set socket read timeout - throws SocketTimeoutException on timeout
        if (readTimeout != null && !readTimeout.isZero()) {
            socket.setSoTimeout((int) readTimeout.toMillis());
        }
    }

    @Override
    public HttpExchange newExchange(HttpRequest request) throws IOException {
        if (!active) {
            throw new IOException("Connection is closed");
        } else if (!inUse.compareAndSet(false, true)) {
            throw new IOException("Connection already in use (concurrent exchange attempted)");
        }

        try {
            return new H1Exchange(this, request, route, lineBuffer);
        } catch (IOException e) {
            // Failed to create exchange, release
            releaseExchange();
            throw e;
        }
    }

    @Override
    public HttpVersion httpVersion() {
        return HttpVersion.HTTP_1_1;
    }

    @Override
    public boolean isActive() {
        // Fast path: just check volatile flags (no syscalls)
        // Full socket health check happens in validateForReuse() when connection
        // is retrieved from pool after being idle
        return active && keepAlive;
    }

    @Override
    public boolean validateForReuse() {
        if (!active || !keepAlive) {
            return false;
        }

        // Check socket state (syscalls, but only when validating for reuse)
        if (socket.isClosed() || socket.isInputShutdown() || socket.isOutputShutdown()) {
            LOGGER.debug("Connection to {} is closed or half-closed", route);
            markInactive();
            return false;
        }

        // Check if server closed connection while idle (sent FIN)
        try {
            if (socketIn.available() > 0) {
                LOGGER.debug("Unexpected data available on idle connection to {}", route);
                markInactive();
                return false;
            }
        } catch (IOException e) {
            LOGGER.debug("IOException checking socket state for {}: {}", route, e.getMessage());
            markInactive();
            return false;
        }

        return true;
    }

    @Override
    public Route route() {
        return route;
    }

    @Override
    public SSLSession sslSession() {
        if (socket instanceof SSLSocket sslSocket) {
            return sslSocket.getSession();
        }
        return null;
    }

    @Override
    public String negotiatedProtocol() {
        if (socket instanceof SSLSocket sslSocket) {
            String protocol = sslSocket.getApplicationProtocol();
            return (protocol != null && !protocol.isEmpty()) ? protocol : null;
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        active = false;
        socket.close();
    }

    /**
     * Set socket read timeout.
     *
     * @param timeoutMs timeout in milliseconds
     * @throws IOException if setting timeout fails
     */
    void setSocketTimeout(int timeoutMs) throws IOException {
        socket.setSoTimeout(timeoutMs);
    }

    /**
     * Get current socket read timeout.
     *
     * @return timeout in milliseconds
     * @throws IOException if getting timeout fails
     */
    int getSocketTimeout() throws IOException {
        return socket.getSoTimeout();
    }

    /**
     * Release the exchange, allowing the connection to be reused.
     *
     * <p>Called by {@link H1Exchange} when the exchange completes.
     */
    void releaseExchange() {
        inUse.set(false);
    }

    /**
     * Set whether this connection supports keep-alive.
     *
     * <p>Called by {@link H1Exchange} after parsing response headers.
     * If the server sends "Connection: close", keep-alive is disabled and
     * the connection will not be reused.
     *
     * @param keepAlive true if connection can be reused
     */
    void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    /**
     * Check if this connection supports keep-alive.
     *
     * @return true if connection can be reused after current exchange
     */
    boolean isKeepAlive() {
        return keepAlive;
    }

    /**
     * Get the input stream for reading responses.
     *
     * @return socket input stream
     */
    UnsyncBufferedInputStream getInputStream() {
        return socketIn;
    }

    /**
     * Get the output stream for writing requests.
     *
     * @return socket output stream
     */
    UnsyncBufferedOutputStream getOutputStream() {
        return socketOut;
    }

    /**
     * Mark this connection as inactive due to an error.
     *
     * <p>Called by {@link H1Exchange} when errors occur during I/O.
     */
    void markInactive() {
        LOGGER.debug("Marking connection inactive to {}", route);
        this.active = false;
    }

    /**
     * Establish an HTTP CONNECT tunnel through a proxy.
     *
     * <p>This is a static factory method that performs the proxy handshake
     * and returns a connected socket ready for use. The CONNECT tunnel is
     * only needed for HTTPS through an HTTP proxy.
     *
     * <p>For HTTP through a proxy, no tunnel is needed - requests are sent
     * with absolute URIs directly to the proxy.
     *
     * <h3>CONNECT Protocol</h3>
     * <pre>
     * Client → Proxy:
     *   CONNECT api.example.com:443 HTTP/1.1
     *   Host: api.example.com:443
     *   Proxy-Authorization: Basic dXNlcjpwYXNz  (if auth required)
     *
     * Proxy → Client:
     *   HTTP/1.1 200 Connection Established
     * </pre>
     *
     * <p>After receiving 200, the socket is connected through the tunnel
     * and TLS handshake can proceed as if connecting directly.
     *
     * @param proxySocket socket connected to proxy server
     * @param targetHost target host for CONNECT request
     * @param targetPort target port for CONNECT request
     * @param proxy proxy configuration (for authentication)
     * @throws IOException if CONNECT tunnel establishment fails
     */
    public static void establishConnectTunnel(
            Socket proxySocket,
            String targetHost,
            int targetPort,
            ProxyConfiguration proxy
    ) throws IOException {

        try {
            OutputStream out = proxySocket.getOutputStream();
            InputStream in = proxySocket.getInputStream();

            // Build CONNECT request
            StringBuilder request = new StringBuilder();
            request.append("CONNECT ")
                    .append(targetHost)
                    .append(":")
                    .append(targetPort)
                    .append(" HTTP/1.1\r\n");
            request.append("Host: ")
                    .append(targetHost)
                    .append(":")
                    .append(targetPort)
                    .append("\r\n");

            // Add proxy authentication if configured
            if (proxy.requiresAuth()) {
                String credentials = proxy.username() + ":" + proxy.password();
                String encoded = Base64.getEncoder()
                        .encodeToString(
                                credentials.getBytes(StandardCharsets.UTF_8));
                request.append("Proxy-Authorization: Basic ").append(encoded).append("\r\n");
            }

            // Keep proxy connection alive for reuse
            request.append("Proxy-Connection: Keep-Alive\r\n");
            request.append("\r\n");

            // Send CONNECT request
            out.write(request.toString().getBytes(StandardCharsets.US_ASCII));
            out.flush();

            // Read response status line
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.US_ASCII));
            String statusLine = reader.readLine();

            if (statusLine == null) {
                throw new IOException("Proxy closed connection during CONNECT handshake");
            }

            // Parse status line: "HTTP/1.1 200 Connection Established"
            String[] parts = statusLine.split("\\s+", 3);
            if (parts.length < 2) {
                throw new IOException("Invalid proxy response: " + statusLine);
            }

            int statusCode;
            try {
                statusCode = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid status code in proxy response: " + statusLine);
            }

            // Read and discard response headers until empty line
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                // Skip header lines
            }

            // Check status code
            if (statusCode == 200) {
                // Tunnel established successfully
                return;
            } else if (statusCode == 407) {
                // Proxy authentication required
                throw new IOException("Proxy authentication required (407). Check proxy credentials.");
            } else if (statusCode >= 400 && statusCode < 500) {
                // Client error
                throw new IOException(
                        "Proxy rejected CONNECT request: " + statusCode + " " +
                                (parts.length > 2 ? parts[2] : ""));
            } else if (statusCode >= 500) {
                // Server error
                throw new IOException(
                        "Proxy server error during CONNECT: " + statusCode + " " +
                                (parts.length > 2 ? parts[2] : ""));
            } else {
                // Unexpected status code
                throw new IOException("Unexpected proxy response: " + statusLine);
            }

        } catch (IOException e) {
            // Close socket on any error
            try {
                proxySocket.close();
            } catch (IOException ignored) {}
            throw new IOException(
                    "Failed to establish CONNECT tunnel to " + targetHost + ":" + targetPort +
                            " through proxy",
                    e);
        }
    }
}
