/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h1;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLSession;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.http.client.UnsyncBufferedInputStream;
import software.amazon.smithy.java.http.client.UnsyncBufferedOutputStream;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.http.client.connection.Transport;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * HTTP/1.1 connection implementation.
 *
 * <p>Manages a single TCP connection for HTTP/1.1 communication. HTTP/1.1 allows only one request/response exchange
 * at a time (no multiplexing like HTTP/2).
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
 */
public final class H1Connection implements HttpConnection {
    /**
     * Buffer used for parsing the HTTP/1.x status line and each header line.
     * This bounds any single response line to 8KB (status line or header line).
     */
    static final int RESPONSE_LINE_BUFFER_SIZE = 8192;

    private static final InternalLogger LOGGER = InternalLogger.getLogger(H1Connection.class);

    private final Transport transport;
    private final UnsyncBufferedInputStream socketIn;
    private final UnsyncBufferedOutputStream socketOut;
    private final Route route;
    private final byte[] lineBuffer; // Reused across exchanges for header parsing

    // HTTP/1.1: only one exchange at a time
    private final AtomicBoolean inUse = new AtomicBoolean(false);
    private volatile boolean keepAlive = true;
    private volatile boolean active = true;

    /**
     * Create an HTTP/1.1 connection from a transport.
     *
     * @param transport the connected transport (TLS handshake must be complete if secure)
     * @param route Connection route
     * @param readTimeout timeout for read operations
     * @throws IOException if streams cannot be obtained
     */
    public H1Connection(Transport transport, Route route, Duration readTimeout) throws IOException {
        this.transport = transport;
        this.socketIn = new UnsyncBufferedInputStream(transport.inputStream(), 16384);
        this.socketOut = new UnsyncBufferedOutputStream(transport.outputStream(), 8192);
        this.route = route;
        this.lineBuffer = new byte[RESPONSE_LINE_BUFFER_SIZE];

        if (readTimeout != null && !readTimeout.isZero()) {
            transport.setReadTimeout((int) readTimeout.toMillis());
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
        return active && keepAlive;
    }

    @Override
    public boolean validateForReuse() {
        if (!active || !keepAlive) {
            return false;
        }

        if (!transport.isOpen()) {
            LOGGER.debug("Connection to {} is closed", route);
            markInactive();
            return false;
        }

        try {
            if (socketIn.available() > 0) {
                LOGGER.debug("Unexpected data available on idle connection to {}", route);
                markInactive();
                return false;
            }
        } catch (IOException e) {
            LOGGER.debug("IOException checking connection state for {}: {}", route, e.getMessage());
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
        return transport.sslSession();
    }

    @Override
    public String negotiatedProtocol() {
        return transport.negotiatedProtocol();
    }

    @Override
    public void close() throws IOException {
        active = false;
        transport.close();
    }

    void releaseExchange() {
        inUse.set(false);
    }

    void setSocketTimeout(int timeoutMs) throws IOException {
        transport.setReadTimeout(timeoutMs);
    }

    int getSocketTimeout() throws IOException {
        return transport.getReadTimeout();
    }

    void setKeepAlive(boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    boolean isKeepAlive() {
        return keepAlive;
    }

    UnsyncBufferedInputStream getInputStream() {
        return socketIn;
    }

    UnsyncBufferedOutputStream getOutputStream() {
        return socketOut;
    }

    void markInactive() {
        if (active) {
            LOGGER.debug("Marking connection inactive to {}", route);
            this.active = false;
        }
    }
}
