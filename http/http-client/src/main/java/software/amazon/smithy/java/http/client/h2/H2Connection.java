/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static software.amazon.smithy.java.http.client.h2.H2Constants.DEFAULT_HEADER_TABLE_SIZE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.DEFAULT_INITIAL_WINDOW_SIZE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.DEFAULT_MAX_CONCURRENT_STREAMS;
import static software.amazon.smithy.java.http.client.h2.H2Constants.DEFAULT_MAX_FRAME_SIZE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_COMPRESSION_ERROR;
import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_ENHANCE_YOUR_CALM;
import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_FLOW_CONTROL_ERROR;
import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_NO_ERROR;
import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_PROTOCOL_ERROR;
import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_SETTINGS_TIMEOUT;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_ACK;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_END_HEADERS;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_END_STREAM;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_PADDED;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_DATA;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_GOAWAY;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_HEADERS;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_PING;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_PUSH_PROMISE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_RST_STREAM;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_SETTINGS;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_WINDOW_UPDATE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.MAX_MAX_FRAME_SIZE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.MIN_MAX_FRAME_SIZE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.SETTINGS_ENABLE_PUSH;
import static software.amazon.smithy.java.http.client.h2.H2Constants.SETTINGS_HEADER_TABLE_SIZE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.SETTINGS_INITIAL_WINDOW_SIZE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.SETTINGS_MAX_CONCURRENT_STREAMS;
import static software.amazon.smithy.java.http.client.h2.H2Constants.SETTINGS_MAX_FRAME_SIZE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.SETTINGS_MAX_HEADER_LIST_SIZE;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.http.client.UnsyncBufferedInputStream;
import software.amazon.smithy.java.http.client.UnsyncBufferedOutputStream;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.http.hpack.HpackDecoder;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * HTTP/2 connection implementation with full stream multiplexing.
 *
 * <p>This implementation manages an HTTP/2 connection over a single TCP socket
 * with support for multiple concurrent streams. A background reader thread
 * dispatches incoming frames to the multiplexer.
 *
 * <h2>Connection Lifecycle</h2>
 * <ol>
 *   <li>Constructor sends connection preface and SETTINGS</li>
 *   <li>Waits for server SETTINGS and sends ACK</li>
 *   <li>Starts background reader thread for frame dispatch</li>
 *   <li>{@link #newExchange} creates exchanges for requests</li>
 *   <li>{@link #close} sends GOAWAY and closes socket</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. Multiple virtual threads can create
 * concurrent exchanges on the same connection. Frame writes are serialized
 * via the muxer's writer thread, and frame reads are handled by a
 * dedicated reader thread.
 */
public final class H2Connection implements HttpConnection, H2Muxer.ConnectionCallback {
    private enum State {
        CONNECTED,
        SHUTTING_DOWN,
        CLOSED
    }

    private static final InternalLogger LOGGER = InternalLogger.getLogger(H2Connection.class);
    private static final int SETTINGS_TIMEOUT_MS = 10_000;
    private static final int GRACEFUL_SHUTDOWN_MS = 1000;

    private final Socket socket;
    private final Route route;
    private final H2FrameCodec frameCodec;
    private final H2Muxer muxer;
    private final HpackDecoder hpackDecoder;
    private final Thread readerThread;
    private final long readTimeoutMs;
    private final long writeTimeoutMs;
    private final int maxFrameSize;

    // Connection settings from peer
    private volatile int remoteMaxFrameSize = DEFAULT_MAX_FRAME_SIZE;
    private volatile int remoteInitialWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;
    private volatile int remoteMaxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
    private volatile int remoteMaxHeaderListSize = Integer.MAX_VALUE;

    // Connection receive window (send window is managed by muxer). Only accessed by reader thread.
    private int connectionRecvWindow;
    private final int initialWindowSize;

    // Connection state (AtomicReference for safe concurrent close)
    private final AtomicReference<State> state = new AtomicReference<>(State.CONNECTED);
    private volatile boolean active = true;
    private volatile boolean goawayReceived = false;
    private volatile Throwable readerError;
    // Track last activity tick for idle timeout (tick = TIMEOUT_POLL_INTERVAL_MS, ~100ms resolution)
    private volatile int lastActivityTick;

    /**
     * Create an HTTP/2 connection from a connected socket.
     *
     * @param socket the connected socket
     * @param route the route for this connection
     * @param readTimeout read timeout duration
     * @param writeTimeout write timeout duration
     * @param initialWindowSize initial flow control window size in bytes
     * @param maxFrameSize maximum frame size to advertise to server
     * @param bufferSize I/O buffer size in bytes
     */
    public H2Connection(
            Socket socket,
            Route route,
            Duration readTimeout,
            Duration writeTimeout,
            int initialWindowSize,
            int maxFrameSize,
            int bufferSize
    ) throws IOException {
        this.socket = socket;
        this.maxFrameSize = maxFrameSize;
        var socketIn = new UnsyncBufferedInputStream(socket.getInputStream(), bufferSize);
        var socketOut = new UnsyncBufferedOutputStream(socket.getOutputStream(), bufferSize);
        this.route = route;
        this.readTimeoutMs = readTimeout.toMillis();
        this.writeTimeoutMs = writeTimeout.toMillis();
        this.frameCodec = new H2FrameCodec(socketIn, socketOut, maxFrameSize);
        this.hpackDecoder = new HpackDecoder(DEFAULT_HEADER_TABLE_SIZE);
        this.initialWindowSize = initialWindowSize;
        this.connectionRecvWindow = initialWindowSize;

        // Create muxer before connection preface (applyRemoteSettings needs it)
        this.muxer = new H2Muxer(this,
                frameCodec,
                DEFAULT_HEADER_TABLE_SIZE,
                "h2-writer-" + route.host(),
                initialWindowSize);

        // Perform connection preface
        try {
            sendConnectionPreface();
            receiveServerPreface();
            // Try to receive initial connection WINDOW_UPDATE (server often sends this right after SETTINGS)
            receiveInitialWindowUpdate();
        } catch (IOException e) {
            close();
            throw new IOException("HTTP/2 connection preface failed", e);
        }

        // Start background reader thread
        this.readerThread = Thread.ofVirtual().name("h2-reader-" + route.host()).start(this::readerLoop);
    }

    // ==================== ConnectionCallback implementation ====================

    @Override
    public boolean isAcceptingStreams() {
        return state.get() == State.CONNECTED && !goawayReceived;
    }

    @Override
    public int getRemoteMaxHeaderListSize() {
        return remoteMaxHeaderListSize;
    }

    // ==================== Reader Thread ====================

    // Track last stream for batched signaling (stream-switch detection).
    // With lock-free signaling, flushing the previous stream is cheap (just LockSupport.unpark).
    private H2Exchange lastDataExchange;

    private void readerLoop() {
        try {
            while (state.get() == State.CONNECTED) {
                int type = frameCodec.nextFrame();
                if (type < 0) {
                    break; // EOF
                }

                // Update last activity tick on every frame received (cheap volatile write vs syscall)
                lastActivityTick = muxer.currentTimeoutTick();

                if (type == FRAME_TYPE_DATA) {
                    handleDataFrame();
                } else {
                    // Non-DATA frame: flush any pending data stream before processing
                    if (lastDataExchange != null) {
                        lastDataExchange.signalDataAvailable();
                        lastDataExchange = null;
                    }
                    handleNonDataFrame();
                }
            }
        } catch (IOException e) {
            if (state.get() == State.CONNECTED) {
                readerError = e;
                active = false;
                LOGGER.debug("Reader thread error for {}: {}", route, e.getMessage());
            }
        } finally {
            // Flush any pending stream before shutdown
            if (lastDataExchange != null) {
                lastDataExchange.signalDataAvailable();
                lastDataExchange = null;
            }
            muxer.shutdownNow();
            muxer.onConnectionClosing(readerError);
            state.set(State.CLOSED);
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void handleDataFrame() throws IOException {
        int streamId = frameCodec.frameStreamId();
        int payloadLength = frameCodec.framePayloadLength();
        boolean endStream = frameCodec.hasFrameFlag(FLAG_END_STREAM);
        boolean padded = frameCodec.hasFrameFlag(FLAG_PADDED);

        if (streamId == 0) {
            throw new H2Exception(ERROR_PROTOCOL_ERROR, "DATA frame must have non-zero stream ID");
        }

        H2Exchange exchange = muxer.getExchange(streamId);

        // Stream switch detection: flush the previous stream if we're switching (lock-free)
        if (lastDataExchange != null && lastDataExchange != exchange) {
            lastDataExchange.signalDataAvailable();
        }

        int padLength = 0;
        int dataLength = payloadLength;
        if (padded) {
            if (payloadLength < 1) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR, "Padded DATA frame too short");
            }
            padLength = frameCodec.readByte();
            dataLength = payloadLength - 1 - padLength;
            if (dataLength < 0) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR, "Pad length " + padLength + " exceeds payload");
            }
        }

        if (exchange != null) {
            if (dataLength > 0) {
                // Borrow byte[] from pool and read payload into it
                byte[] buffer = muxer.borrowBuffer(dataLength);
                frameCodec.readPayloadInto(buffer, 0, dataLength);
                // Check if more data is buffered - used for adaptive signaling to reduce wakeups
                boolean moreDataBuffered = frameCodec.hasBufferedData();
                exchange.enqueueData(buffer, dataLength, endStream, moreDataBuffered);
                consumeConnectionRecvWindow(dataLength);
                // Track for stream-switch detection; clear if buffer empty (we just signaled)
                lastDataExchange = moreDataBuffered ? exchange : null;
            } else if (endStream) {
                exchange.enqueueData(null, 0, true, false);
                lastDataExchange = null;
            }
        } else {
            if (dataLength > 0) {
                frameCodec.skipBytes(dataLength);
                consumeConnectionRecvWindow(dataLength);
            }
            LOGGER.trace("Ignoring DATA frame for closed stream {}", streamId);
            // Clear tracker if buffer empty (even for unknown streams)
            if (!frameCodec.hasBufferedData()) {
                lastDataExchange = null;
            }
        }

        if (padLength > 0) {
            frameCodec.skipBytes(padLength);
        }
    }

    private void handleNonDataFrame() throws IOException {
        int type = frameCodec.frameType();
        int streamId = frameCodec.frameStreamId();
        int length = frameCodec.framePayloadLength();

        // Fast path: handle small control frames (WINDOW_UPDATE, RST_STREAM) without buffer allocation.
        // These frames are always exactly 4 bytes and very common during flow control.
        if (type == FRAME_TYPE_WINDOW_UPDATE) {
            int increment = frameCodec.readAndParseWindowUpdate();
            if (streamId == 0) {
                muxer.releaseConnectionWindow(increment);
            } else {
                H2Exchange exchange = muxer.getExchange(streamId);
                if (exchange != null) {
                    exchange.updateStreamSendWindow(increment);
                }
                // Ignore WINDOW_UPDATE for unknown streams (closed streams, etc.)
            }
            return;
        }

        if (type == FRAME_TYPE_RST_STREAM && streamId != 0) {
            int errorCode = frameCodec.readAndParseRstStream();
            H2Exchange exchange = muxer.getExchange(streamId);
            if (exchange != null) {
                H2Exception error = new H2Exception(errorCode,
                        streamId,
                        "Stream reset by server: " + H2Constants.errorCodeName(errorCode));
                exchange.signalStreamError(error);
            }
            // Ignore RST_STREAM for unknown streams
            return;
        }

        // Standard path: read payload into pooled buffer
        byte[] payload;
        if (length == 0) {
            payload = H2Constants.EMPTY_BYTES;
        } else {
            payload = muxer.borrowBuffer(length);
            frameCodec.readPayloadInto(payload, 0, length);
        }

        try {
            if (streamId == 0) {
                handleConnectionFrame(type, payload, length);
            } else {
                // Handle HEADERS with CONTINUATION frames
                byte[] headerPayload = payload;
                int headerLength = length;
                if (type == FRAME_TYPE_HEADERS && !frameCodec.hasFrameFlag(FLAG_END_HEADERS)) {
                    headerPayload = frameCodec.readHeaderBlock(streamId, payload, length);
                    headerLength = frameCodec.headerBlockSize();
                    // Return original payload, headerPayload is a view into frameCodec's buffer
                    if (payload != H2Constants.EMPTY_BYTES) {
                        muxer.returnBuffer(payload);
                    }
                    payload = null; // Mark as already returned
                }

                H2Exchange exchange = muxer.getExchange(streamId);
                if (exchange != null) {
                    dispatchStreamFrame(exchange, type, streamId, headerPayload, headerLength);
                } else {
                    handleFrameForUnknownStream(type, streamId, headerPayload, headerLength);
                }
            }
        } finally {
            // Return pooled buffer (only if not already returned)
            if (payload != null && payload != H2Constants.EMPTY_BYTES) {
                muxer.returnBuffer(payload);
            }
        }
    }

    private void handleFrameForUnknownStream(int type, int streamId, byte[] payload, int length) throws IOException {
        // Note: DATA frames for unknown streams are handled in handleDataFrame(), not here.
        if (type == FRAME_TYPE_HEADERS) {
            // Must decode headers to maintain HPACK state, even for unknown streams
            if (payload != null && length > 0) {
                decodeHeaders(payload, length);
            }
            LOGGER.trace("Ignoring HEADERS frame for unknown stream {}", streamId);
        }
    }

    private void dispatchStreamFrame(H2Exchange exchange, int type, int streamId, byte[] payload, int length)
            throws IOException {
        // Note: WINDOW_UPDATE and RST_STREAM are handled in handleNonDataFrame fast path
        switch (type) {
            case FRAME_TYPE_HEADERS -> {
                List<String> decoded;
                if (payload != null && length > 0) {
                    decoded = decodeHeaders(payload, length);
                } else {
                    decoded = List.of();
                }
                boolean endStream = frameCodec.hasFrameFlag(FLAG_END_STREAM);
                exchange.deliverHeaders(decoded, endStream);
            }
            case FRAME_TYPE_PUSH_PROMISE -> {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "Received PUSH_PROMISE but server push is disabled");
            }
            default -> {
            }
        }
    }

    private void handleConnectionFrame(int type, byte[] payload, int length) throws IOException {
        // Note: WINDOW_UPDATE is handled in handleNonDataFrame fast path
        switch (type) {
            case FRAME_TYPE_SETTINGS -> {
                if (!frameCodec.hasFrameFlag(FLAG_ACK)) {
                    int[] settings = frameCodec.parseSettings(payload, length);
                    applyRemoteSettings(settings);
                    muxer.queueControlFrame(0,
                            H2Muxer.ControlFrameType.SETTINGS_ACK,
                            null,
                            writeTimeoutMs);
                }
            }
            case FRAME_TYPE_PING -> {
                if (!frameCodec.hasFrameFlag(FLAG_ACK)) {
                    // Copy payload for async write (payload may be pooled buffer)
                    byte[] pingData = new byte[8];
                    System.arraycopy(payload, 0, pingData, 0, 8);
                    muxer.queueControlFrame(0,
                            H2Muxer.ControlFrameType.PING,
                            pingData,
                            writeTimeoutMs);
                }
            }
            case FRAME_TYPE_GOAWAY -> {
                int[] goaway = frameCodec.parseGoaway(payload, length);
                handleGoaway(goaway[0], goaway[1]);
            }
            default -> {
            }
        }
    }

    // ==================== Connection Preface ====================

    private void sendConnectionPreface() throws IOException {
        frameCodec.writeConnectionPreface();
        frameCodec.writeSettings(
                SETTINGS_MAX_CONCURRENT_STREAMS,
                100,
                SETTINGS_INITIAL_WINDOW_SIZE,
                initialWindowSize,
                SETTINGS_MAX_FRAME_SIZE,
                maxFrameSize,
                SETTINGS_ENABLE_PUSH,
                0);
        frameCodec.flush();

        // If using a larger window than the RFC default, send a connection-level WINDOW_UPDATE
        // to expand the connection receive window immediately
        if (initialWindowSize > DEFAULT_INITIAL_WINDOW_SIZE) {
            int increment = initialWindowSize - DEFAULT_INITIAL_WINDOW_SIZE;
            frameCodec.writeWindowUpdate(0, increment);
            frameCodec.flush();
        }
    }

    private void receiveServerPreface() throws IOException {
        int originalTimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(SETTINGS_TIMEOUT_MS);

            int type;
            try {
                type = frameCodec.nextFrame();
            } catch (SocketTimeoutException e) {
                throw new H2Exception(ERROR_SETTINGS_TIMEOUT, "Timeout waiting for server SETTINGS frame");
            }

            if (type < 0) {
                throw new IOException("Connection closed before receiving server SETTINGS");
            }
            if (type != FRAME_TYPE_SETTINGS) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "Expected SETTINGS frame, got " + H2Constants.frameTypeName(type));
            }
            if (frameCodec.hasFrameFlag(FLAG_ACK)) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR, "First SETTINGS frame must not be ACK");
            }

            // Read and parse settings payload
            int length = frameCodec.framePayloadLength();
            byte[] payload;
            if (length == 0) {
                payload = H2Constants.EMPTY_BYTES;
            } else {
                payload = new byte[length];
                frameCodec.readPayloadInto(payload, 0, length);
            }
            int[] settings = frameCodec.parseSettings(payload, length);
            applyRemoteSettings(settings);

            frameCodec.writeSettingsAck();
            frameCodec.flush();
        } finally {
            socket.setSoTimeout(originalTimeout);
        }
    }

    /**
     * Try to receive the initial connection-level WINDOW_UPDATE that servers typically send
     * right after SETTINGS to expand the connection flow control window.
     * Uses a short timeout - if no frame arrives quickly, we proceed anyway.
     *
     * <p>At this point in the handshake, the only valid frames the server can send are:
     * <ul>
     *   <li>WINDOW_UPDATE - what we're looking for, apply it</li>
     *   <li>SETTINGS ACK - acknowledgment of our SETTINGS, safe to ignore</li>
     * </ul>
     * Any other frame type is a protocol error.
     */
    private void receiveInitialWindowUpdate() throws IOException {
        int originalTimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(50); // Short timeout - don't block long if server doesn't send one
            int type = frameCodec.nextFrame();
            switch (type) {
                case -1, FRAME_TYPE_SETTINGS:
                    // EOF or SETTINGS ACK, ignore, we don't wait for it
                    break;
                case FRAME_TYPE_WINDOW_UPDATE:
                    if (frameCodec.frameStreamId() == 0) {
                        int increment = frameCodec.readAndParseWindowUpdate();
                        muxer.releaseConnectionWindow(increment);
                    }
                    break;
                default:
                    throw new H2Exception(
                            ERROR_PROTOCOL_ERROR,
                            "Unexpected frame during handshake: " + H2Constants.frameTypeName(type));
            }
        } catch (SocketTimeoutException e) {
            // No initial WINDOW_UPDATE - that's fine, proceed with default window
        } finally {
            socket.setSoTimeout(originalTimeout);
        }
    }

    private void applyRemoteSettings(int[] settings) throws IOException {
        for (int i = 0; i < settings.length; i += 2) {
            int id = settings[i];
            int value = settings[i + 1];

            switch (id) {
                case SETTINGS_HEADER_TABLE_SIZE:
                    muxer.setMaxTableSize(value);
                    break;
                case SETTINGS_ENABLE_PUSH:
                    break;
                case SETTINGS_MAX_CONCURRENT_STREAMS:
                    remoteMaxConcurrentStreams = value;
                    muxer.onSettingsReceived(value, remoteInitialWindowSize, remoteMaxFrameSize);
                    break;
                case SETTINGS_INITIAL_WINDOW_SIZE:
                    if (value < 0) {
                        throw new H2Exception(ERROR_FLOW_CONTROL_ERROR,
                                "Invalid INITIAL_WINDOW_SIZE: " + (value & 0xFFFFFFFFL));
                    }
                    remoteInitialWindowSize = value;
                    muxer.onSettingsReceived(remoteMaxConcurrentStreams, value, remoteMaxFrameSize);
                    break;
                case SETTINGS_MAX_FRAME_SIZE:
                    if (value < MIN_MAX_FRAME_SIZE || value > MAX_MAX_FRAME_SIZE) {
                        throw new H2Exception(ERROR_PROTOCOL_ERROR, "Invalid MAX_FRAME_SIZE: " + value);
                    }
                    remoteMaxFrameSize = value;
                    muxer.onSettingsReceived(remoteMaxConcurrentStreams, remoteInitialWindowSize, value);
                    break;
                case SETTINGS_MAX_HEADER_LIST_SIZE:
                    remoteMaxHeaderListSize = value;
                    break;
                default:
                    break;
            }
        }
    }

    // ==================== Exchange Creation ====================

    @Override
    public HttpExchange newExchange(HttpRequest request) throws IOException {
        if (state.get() != State.CONNECTED) {
            throw new IOException("Connection is not in CONNECTED state: " + state.get());
        }

        // Update last activity tick when creating a new exchange
        lastActivityTick = muxer.currentTimeoutTick();

        H2Exchange exchange = muxer.newExchange(request, readTimeoutMs, writeTimeoutMs);

        try {
            boolean hasBody = request.body() != null && request.body().contentLength() != 0;
            boolean endStream = !hasBody;

            if (!muxer.submitHeaders(request, exchange, endStream, writeTimeoutMs)) {
                muxer.releaseStreamSlot();
                throw new IOException("Connection not accepting new streams");
            }

            // Block until headers are encoded and written
            // Stream ID is set on the exchange by the muxer before signaling
            exchange.awaitWriteCompletion();

            IOException writeErr = muxer.getWriteError();
            if (writeErr != null) {
                int streamId = exchange.getStreamId();
                if (streamId > 0) {
                    muxer.releaseStream(streamId);
                }
                throw writeErr;
            }

            return exchange;

        } catch (IOException e) {
            int streamId = exchange.getStreamId();
            if (streamId > 0) {
                muxer.releaseStream(streamId);
            }
            throw e;
        }
    }

    // ==================== Connection State ====================

    public int getActiveStreamCount() {
        return muxer.getActiveStreamCount();
    }

    /**
     * Check if this connection can accept more streams.
     *
     * <p>This is the primary check used in the connection acquisition hot path. It combines active state, write error,
     * and muxer capacity checks to minimize redundant checks.
     */
    public boolean canAcceptMoreStreams() {
        return active && muxer.getWriteError() == null && muxer.canAcceptMoreStreams();
    }

    /**
     * Get the active stream count if this connection can accept more streams, or -1 if not.
     * Combines the availability check with getting the count to avoid redundant atomic reads
     * in the connection acquisition hot path.
     */
    public int getActiveStreamCountIfAccepting() {
        if (!active || muxer.getWriteError() != null) {
            return -1;
        }
        return muxer.getActiveStreamCountIfAccepting();
    }

    /**
     * Get the time in nanoseconds since the last activity on this connection.
     *
     * <p>If there are active streams, returns 0 (not idle).
     * Otherwise, returns the time since the last frame was received or exchange was created.
     *
     * <p>Note: Resolution is ~100ms (tick-based) to avoid System.nanoTime() syscalls on hot path.
     *
     * @return idle time in nanoseconds, or 0 if there are active streams
     */
    public long getIdleTimeNanos() {
        if (getActiveStreamCount() > 0) {
            return 0; // Not idle if there are active streams
        }
        int idleTicks = muxer.currentTimeoutTick() - lastActivityTick;
        // Convert ticks to nanoseconds: ticks * ms_per_tick * nanos_per_ms
        return (long) idleTicks * H2Muxer.TIMEOUT_POLL_INTERVAL_MS * 1_000_000L;
    }

    @Override
    public HttpVersion httpVersion() {
        return HttpVersion.HTTP_2;
    }

    @Override
    public boolean isActive() {
        return active && muxer.getWriteError() == null;
    }

    @Override
    public boolean validateForReuse() {
        // Fast path: 'active' is set to false by reader thread on socket issues,
        // so if it's false, no need for expensive socket checks.
        if (!active) {
            return false;
        }

        // Check for write errors
        IOException writeErr = muxer.getWriteError();
        if (writeErr != null) {
            LOGGER.debug("Connection to {} has write error", route);
            active = false;
            state.set(State.CLOSED);
            return false;
        }

        // Socket checks skipped here - reader thread sets active=false on socket issues.
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
            return (protocol != null && !protocol.isEmpty()) ? protocol : "h2";
        }
        return "h2";
    }

    @Override
    public void close() throws IOException {
        // Check if it's already shutting down or closed
        if (!state.compareAndSet(State.CONNECTED, State.SHUTTING_DOWN)) {
            return;
        }

        active = false;

        // Queue the control frame to shutdown, but use a short timeout
        var payload = new Object[] {muxer.getLastAllocatedStreamId(), ERROR_NO_ERROR, null};
        muxer.queueControlFrame(0, H2Muxer.ControlFrameType.GOAWAY, payload, 100);

        muxer.close();
        muxer.closeExchanges(Duration.ofMillis(GRACEFUL_SHUTDOWN_MS));
        state.set(State.CLOSED);
        socket.close();

        try {
            readerThread.join(100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // Called only from reader thread - no synchronization needed
    List<String> decodeHeaders(byte[] headerBlock, int length) throws IOException {
        int maxHeaderListSize = H2Constants.DEFAULT_MAX_HEADER_LIST_SIZE;
        if (length > maxHeaderListSize) {
            throw new H2Exception(ERROR_ENHANCE_YOUR_CALM,
                    "Header block size " + length + " exceeds limit " + maxHeaderListSize);
        }

        List<String> headers;
        try {
            headers = hpackDecoder.decode(headerBlock, 0, length);
        } catch (IOException e) {
            active = false;
            LOGGER.debug("HPACK decoding failed for {}: {}", route, e.getMessage());
            throw new H2Exception(ERROR_COMPRESSION_ERROR, "HPACK decoding failed: " + e.getMessage());
        } catch (IndexOutOfBoundsException e) {
            active = false;
            LOGGER.debug("HPACK dynamic table mismatch for {}: {}", route, e.getMessage());
            throw new H2Exception(ERROR_COMPRESSION_ERROR, "HPACK state mismatch: " + e.getMessage());
        }

        int decodedSize = 0;
        for (int i = 0; i < headers.size(); i += 2) {
            decodedSize += headers.get(i).length() + headers.get(i + 1).length() + 32;
            if (decodedSize > maxHeaderListSize) {
                throw new H2Exception(ERROR_ENHANCE_YOUR_CALM,
                        "Decoded header list size exceeds limit " + maxHeaderListSize);
            }
        }

        return headers;
    }

    // Called only from reader thread - no synchronization needed
    void consumeConnectionRecvWindow(int bytes) throws IOException {
        connectionRecvWindow -= bytes;
        // Send WINDOW_UPDATE when window drops below threshold to reduce control frame overhead
        // while still leaving enough buffer to avoid server stalls
        if (connectionRecvWindow < initialWindowSize / H2Constants.WINDOW_UPDATE_THRESHOLD_DIVISOR) {
            int increment = initialWindowSize - connectionRecvWindow;
            connectionRecvWindow += increment;
            muxer.queueControlFrame(0, H2Muxer.ControlFrameType.WINDOW_UPDATE, increment, writeTimeoutMs);
        }
    }

    void handleGoaway(int lastStreamId, int errorCode) {
        goawayReceived = true;
        active = false;

        if (errorCode != ERROR_NO_ERROR) {
            LOGGER.debug("Server sent GOAWAY to {}: {}", route, H2Constants.errorCodeName(errorCode));
        }

        state.set(State.SHUTTING_DOWN);
        muxer.onGoaway(lastStreamId, errorCode);
    }
}
