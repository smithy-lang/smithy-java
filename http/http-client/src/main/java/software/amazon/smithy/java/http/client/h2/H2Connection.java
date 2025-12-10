/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static software.amazon.smithy.java.http.client.h2.H2Constants.CONNECTION_PREFACE;
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
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.http.client.UnsyncBufferedInputStream;
import software.amazon.smithy.java.http.client.UnsyncBufferedOutputStream;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.http.client.h2.hpack.HeaderField;
import software.amazon.smithy.java.http.client.h2.hpack.HpackDecoder;
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
    private static final byte[] EMPTY_PAYLOAD = new byte[0];
    private static final int SETTINGS_TIMEOUT_MS = 10_000;
    private static final int GRACEFUL_SHUTDOWN_MS = 1000;

    private final Socket socket;
    private final UnsyncBufferedOutputStream socketOut;
    private final Route route;
    private final H2FrameCodec frameCodec;
    private final H2Muxer muxer;
    private final HpackDecoder hpackDecoder;
    private final Thread readerThread;
    private final long readTimeoutMs;
    private final long writeTimeoutMs;

    // Connection settings from peer
    private volatile int remoteMaxFrameSize = DEFAULT_MAX_FRAME_SIZE;
    private volatile int remoteInitialWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;
    private volatile int remoteMaxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
    private volatile int remoteHeaderTableSize = DEFAULT_HEADER_TABLE_SIZE;
    private volatile int remoteMaxHeaderListSize = Integer.MAX_VALUE;

    // Connection receive window (send window is managed by muxer). Only accessed by reader thread.
    private int connectionRecvWindow;
    private final int initialWindowSize;

    // Connection state
    private volatile State state = State.CONNECTED;
    private volatile boolean active = true;
    private volatile boolean goawayReceived = false;
    private volatile int goawayLastStreamId = Integer.MAX_VALUE;
    private volatile Throwable readerError;
    // Track last activity time for idle timeout (nanos)
    private volatile long lastActivityTimeNanos = System.nanoTime();

    /**
     * Create an HTTP/2 connection from a connected socket.
     *
     * @param socket the connected socket
     * @param route the route for this connection
     * @param readTimeout read timeout duration
     * @param writeTimeout write timeout duration
     * @param initialWindowSize initial flow control window size in bytes
     */
    public H2Connection(Socket socket, Route route, Duration readTimeout, Duration writeTimeout, int initialWindowSize)
            throws IOException {
        this.socket = socket;
        var socketIn = new UnsyncBufferedInputStream(socket.getInputStream(), 8192);
        this.socketOut = new UnsyncBufferedOutputStream(socket.getOutputStream(), 8192);
        this.route = route;
        this.readTimeoutMs = readTimeout.toMillis();
        this.writeTimeoutMs = writeTimeout.toMillis();
        this.frameCodec = new H2FrameCodec(socketIn, socketOut, DEFAULT_MAX_FRAME_SIZE);
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
        } catch (IOException e) {
            close();
            throw new IOException("HTTP/2 connection preface failed", e);
        }

        // Start background reader thread
        this.readerThread = Thread.ofVirtual()
                .name("h2-reader-" + route.host())
                .start(this::readerLoop);
    }

    // ==================== ConnectionCallback implementation ====================

    @Override
    public boolean isAcceptingStreams() {
        return state == State.CONNECTED && !goawayReceived;
    }

    @Override
    public int getRemoteMaxHeaderListSize() {
        return remoteMaxHeaderListSize;
    }

    // ==================== Reader Thread ====================

    private void readerLoop() {
        try {
            while (state == State.CONNECTED) {
                H2FrameCodec.FrameHeader header = frameCodec.readFrameHeader();
                if (header == null) {
                    break;
                }

                // Update last activity time on every frame received
                lastActivityTimeNanos = System.nanoTime();

                if (header.type() == FRAME_TYPE_DATA) {
                    handleDataFrame(header);
                } else {
                    H2FrameCodec.Frame frame = readNonDataFrame(header);
                    dispatchFrame(frame);
                }
            }
        } catch (IOException e) {
            if (state == State.CONNECTED) {
                readerError = e;
                active = false;
                LOGGER.debug("Reader thread error for {}: {}", route, e.getMessage());
            }
        } finally {
            if (muxer != null) {
                muxer.shutdownNow();
            }
            muxer.onConnectionClosing(readerError);
            state = State.CLOSED;
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void handleDataFrame(H2FrameCodec.FrameHeader header) throws IOException {
        int streamId = header.streamId();
        int payloadLength = header.payloadLength();
        boolean endStream = header.hasFlag(FLAG_END_STREAM);
        boolean padded = header.hasFlag(FLAG_PADDED);

        if (streamId == 0) {
            throw new H2Exception(ERROR_PROTOCOL_ERROR, "DATA frame must have non-zero stream ID");
        }

        H2Exchange exchange = muxer.getExchange(streamId);

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
                if (!exchange.ensureBufferSpace(dataLength)) {
                    throw new H2Exception(ERROR_FLOW_CONTROL_ERROR,
                            streamId,
                            "Exchange buffer overflow - flow control failure");
                }
                frameCodec.readPayloadInto(exchange.getDataBuffer(), exchange.getWritePos(), dataLength);
                exchange.commitWrite(dataLength, endStream);
                consumeConnectionRecvWindow(dataLength);
            } else if (endStream) {
                exchange.commitWrite(0, true);
            }
        } else {
            if (dataLength > 0) {
                frameCodec.skipBytes(dataLength);
                consumeConnectionRecvWindow(dataLength);
            }
            LOGGER.trace("Ignoring DATA frame for closed stream {}", streamId);
        }

        if (padLength > 0) {
            frameCodec.skipBytes(padLength);
        }
    }

    private H2FrameCodec.Frame readNonDataFrame(H2FrameCodec.FrameHeader header) throws IOException {
        int length = header.payloadLength();
        byte[] payload;
        if (length == 0) {
            payload = EMPTY_PAYLOAD;
        } else {
            payload = new byte[length];
            frameCodec.readPayloadInto(payload, 0, length);
        }
        return new H2FrameCodec.Frame(header.type(), header.flags(), header.streamId(), payload, length);
    }

    private void dispatchFrame(H2FrameCodec.Frame frame) throws IOException {
        int streamId = frame.streamId();

        if (streamId == 0) {
            handleConnectionFrame(frame);
        } else {
            if (frame.type() == FRAME_TYPE_HEADERS && !frame.hasFlag(FLAG_END_HEADERS)) {
                byte[] headerBlock = frameCodec.readHeaderBlock(frame);
                frame = new H2FrameCodec.Frame(
                        FRAME_TYPE_HEADERS,
                        frame.flags() | FLAG_END_HEADERS,
                        streamId,
                        headerBlock,
                        headerBlock.length);
            }

            H2Exchange exchange = muxer.getExchange(streamId);
            if (exchange != null) {
                dispatchStreamFrame(exchange, frame, streamId);
            } else {
                handleFrameForUnknownStream(frame, streamId);
            }
        }
    }

    private void handleFrameForUnknownStream(H2FrameCodec.Frame frame, int streamId) throws IOException {
        if (frame.type() == FRAME_TYPE_DATA) {
            byte[] payload = frame.payload();
            if (payload != null && payload.length > 0) {
                consumeConnectionRecvWindow(payload.length);
            }
            LOGGER.trace("Ignoring DATA frame for closed stream {}", streamId);
        } else if (frame.type() == FRAME_TYPE_HEADERS) {
            byte[] headerBlock = frame.payload();
            if (headerBlock != null && headerBlock.length > 0) {
                decodeHeaders(headerBlock);
            }
            LOGGER.trace("Ignoring HEADERS frame for closed stream {}", streamId);
        }
    }

    private void dispatchStreamFrame(H2Exchange exchange, H2FrameCodec.Frame frame, int streamId)
            throws IOException {
        switch (frame.type()) {
            case FRAME_TYPE_HEADERS -> {
                byte[] headerBlock = frame.payload();
                List<HeaderField> decoded;
                if (headerBlock != null && headerBlock.length > 0) {
                    decoded = decodeHeaders(headerBlock);
                } else {
                    decoded = List.of();
                }
                boolean endStream = frame.hasFlag(FLAG_END_STREAM);
                exchange.deliverHeaders(decoded, endStream);
            }
            case FRAME_TYPE_RST_STREAM -> {
                int errorCode = frame.parseRstStream();
                H2Exception error = new H2Exception(errorCode,
                        streamId,
                        "Stream reset by server: " + H2Constants.errorCodeName(errorCode));
                exchange.signalStreamError(error);
            }
            case FRAME_TYPE_WINDOW_UPDATE -> {
                int increment = frame.parseWindowUpdate();
                exchange.updateStreamSendWindow(increment);
            }
            case FRAME_TYPE_PUSH_PROMISE -> {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "Received PUSH_PROMISE but server push is disabled");
            }
            default -> {
            }
        }
    }

    private void handleConnectionFrame(H2FrameCodec.Frame frame) throws IOException {
        switch (frame.type()) {
            case FRAME_TYPE_SETTINGS -> {
                if (!frame.hasFlag(FLAG_ACK)) {
                    applyRemoteSettings(frame);
                    muxer.queueControlFrame(0,
                            H2Muxer.ControlFrameType.SETTINGS_ACK,
                            null,
                            writeTimeoutMs);
                }
            }
            case FRAME_TYPE_PING -> {
                if (!frame.hasFlag(FLAG_ACK)) {
                    muxer.queueControlFrame(0,
                            H2Muxer.ControlFrameType.PING,
                            frame.payload(),
                            writeTimeoutMs);
                }
            }
            case FRAME_TYPE_GOAWAY -> {
                int[] goaway = frame.parseGoaway();
                handleGoaway(goaway[0], goaway[1]);
            }
            case FRAME_TYPE_WINDOW_UPDATE -> {
                int increment = frame.parseWindowUpdate();
                muxer.releaseConnectionWindow(increment);
            }
            default -> {
            }
        }
    }

    // ==================== Connection Preface ====================

    private void sendConnectionPreface() throws IOException {
        socketOut.write(CONNECTION_PREFACE);
        frameCodec.writeSettings(
                SETTINGS_MAX_CONCURRENT_STREAMS,
                100,
                SETTINGS_INITIAL_WINDOW_SIZE,
                initialWindowSize,
                SETTINGS_MAX_FRAME_SIZE,
                16384,
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

            H2FrameCodec.Frame frame;
            try {
                frame = frameCodec.readFrame();
            } catch (SocketTimeoutException e) {
                throw new H2Exception(ERROR_SETTINGS_TIMEOUT, "Timeout waiting for server SETTINGS frame");
            }

            if (frame == null) {
                throw new IOException("Connection closed before receiving server SETTINGS");
            }
            if (frame.type() != FRAME_TYPE_SETTINGS) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "Expected SETTINGS frame, got " + H2Constants.frameTypeName(frame.type()));
            }
            if (frame.hasFlag(FLAG_ACK)) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR, "First SETTINGS frame must not be ACK");
            }

            applyRemoteSettings(frame);
            frameCodec.writeSettingsAck();
            frameCodec.flush();
        } finally {
            socket.setSoTimeout(originalTimeout);
        }
    }

    private void applyRemoteSettings(H2FrameCodec.Frame frame) throws IOException {
        int[] settings = frame.parseSettings();
        for (int i = 0; i < settings.length; i += 2) {
            int id = settings[i];
            int value = settings[i + 1];

            switch (id) {
                case SETTINGS_HEADER_TABLE_SIZE:
                    remoteHeaderTableSize = value;
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
        if (state != State.CONNECTED) {
            throw new IOException("Connection is not in CONNECTED state: " + state);
        }

        // Update last activity time when creating a new exchange
        lastActivityTimeNanos = System.nanoTime();

        H2Exchange exchange = muxer.newExchange(request, readTimeoutMs, writeTimeoutMs);

        try {
            boolean hasBody = request.body() != null && request.body().contentLength() != 0;
            boolean endStream = !hasBody;

            CompletableFuture<Integer> streamIdFuture = new CompletableFuture<>();
            CompletableFuture<Void> writeComplete = new CompletableFuture<>();

            if (!muxer.submitHeaders(request,
                    exchange,
                    endStream,
                    streamIdFuture,
                    writeComplete,
                    writeTimeoutMs)) {
                muxer.releaseStreamSlot();
                throw new IOException(
                        "Write queue full - connection overloaded (timeout after " + writeTimeoutMs + "ms)");
            }

            int streamId;
            try {
                streamId = streamIdFuture.join();
            } catch (Exception e) {
                muxer.releaseStreamSlot();
                Throwable cause = e.getCause();
                if (cause instanceof IOException ioe) {
                    throw ioe;
                }
                throw new IOException("Encoding failed", cause != null ? cause : e);
            }

            try {
                writeComplete.join();
            } catch (Exception e) {
                muxer.releaseStream(streamId);
                Throwable cause = e.getCause();
                if (cause instanceof IOException ioe) {
                    throw ioe;
                }
                throw new IOException("Failed to write headers", cause != null ? cause : e);
            }

            IOException writeErr = muxer.getWriteError();
            if (writeErr != null) {
                muxer.releaseStream(streamId);
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
        // Fast check: if not active, don't bother with other checks
        if (!active) {
            return false;
        } else if (muxer.getWriteError() != null) {
            // found write errors (updated by writer thread on failure)
            return false;
        } else {
            // Check muxer capacity
            return muxer.canAcceptMoreStreams();
        }
    }

    /**
     * Get the active stream count if this connection can accept more streams, or -1 if not.
     * Combines the availability check with getting the count to avoid redundant atomic reads
     * in the connection acquisition hot path.
     */
    public int getActiveStreamCountIfAccepting() {
        if (!active) {
            return -1;
        } else if (muxer.getWriteError() != null) {
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
     * @return idle time in nanoseconds, or 0 if there are active streams
     */
    public long getIdleTimeNanos() {
        if (getActiveStreamCount() > 0) {
            return 0; // Not idle if there are active streams
        }
        return System.nanoTime() - lastActivityTimeNanos;
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
            state = State.CLOSED;
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
        if (state == State.CLOSED) {
            return;
        }

        active = false;
        State previousState = state;
        state = State.SHUTTING_DOWN;

        if (previousState == State.CONNECTED) {
            try {
                muxer.queueControlFrame(0,
                        H2Muxer.ControlFrameType.GOAWAY,
                        new Object[] {muxer.getLastAllocatedStreamId(), ERROR_NO_ERROR, null},
                        100); // Short timeout for shutdown
            } catch (IOException ignored) {}
        }

        if (muxer != null) {
            muxer.close();
        }

        muxer.closeExchanges(Duration.ofMillis(GRACEFUL_SHUTDOWN_MS));
        state = State.CLOSED;
        socket.close();

        if (readerThread != null) {
            try {
                readerThread.join(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==================== Helper Methods ====================

    // Called only from reader thread - no synchronization needed
    List<HeaderField> decodeHeaders(byte[] headerBlock) throws IOException {
        int maxHeaderListSize = H2Constants.DEFAULT_MAX_HEADER_LIST_SIZE;
        if (headerBlock.length > maxHeaderListSize) {
            throw new H2Exception(ERROR_ENHANCE_YOUR_CALM,
                    "Header block size " + headerBlock.length + " exceeds limit " + maxHeaderListSize);
        }

        List<HeaderField> headers;
        try {
            headers = hpackDecoder.decodeBlock(headerBlock, 0, headerBlock.length);
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
        for (HeaderField field : headers) {
            decodedSize += field.name().length() + field.value().length() + 32;
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
            muxer.queueControlFrame(0,
                    H2Muxer.ControlFrameType.WINDOW_UPDATE,
                    increment,
                    writeTimeoutMs);
        }
    }

    void handleGoaway(int lastStreamId, int errorCode) {
        goawayReceived = true;
        goawayLastStreamId = lastStreamId;
        active = false;

        if (errorCode != ERROR_NO_ERROR) {
            LOGGER.debug("Server sent GOAWAY to {}: {}", route, H2Constants.errorCodeName(errorCode));
        }

        state = State.SHUTTING_DOWN;
        muxer.onGoaway(lastStreamId, errorCode);
    }
}
