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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.http.client.UnsyncBufferedInputStream;
import software.amazon.smithy.java.http.client.UnsyncBufferedOutputStream;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.http.client.h2.hpack.HpackDecoder;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * HTTP/2 connection implementation with full stream multiplexing.
 *
 * <p>This implementation manages an HTTP/2 connection over a single TCP socket
 * with support for multiple concurrent streams. A background reader thread
 * dispatches incoming frames to the appropriate stream handlers.
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
 * via a dedicated writer thread with queue, and frame reads are handled by a
 * dedicated reader thread.
 */
public final class H2Connection implements HttpConnection, H2StreamWriter.StreamManager {
    /**
     * Internal connection state.
     */
    private enum State {
        CONNECTED,
        SHUTTING_DOWN,
        CLOSED
    }

    private static final InternalLogger LOGGER = InternalLogger.getLogger(H2Connection.class);

    private final Socket socket;
    private final UnsyncBufferedOutputStream socketOut;
    private final Route route;
    private final H2FrameCodec frameCodec;

    // Combined encoder/writer - handles both HPACK encoding AND frame writing
    // All socket writes are serialized through this single thread
    private final H2StreamWriter streamWriter;

    // HPACK decoder for responses (only accessed by reader thread - no synchronization needed)
    private final HpackDecoder hpackDecoder;

    // Connection settings (ours and peer's)
    private volatile int remoteMaxFrameSize = DEFAULT_MAX_FRAME_SIZE;
    private volatile int remoteInitialWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;
    private volatile int remoteMaxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
    private volatile int remoteHeaderTableSize = DEFAULT_HEADER_TABLE_SIZE;
    // Server's limit for header list size we send (default: unlimited per RFC 9113)
    private volatile int remoteMaxHeaderListSize = Integer.MAX_VALUE;

    // Flow control - use AtomicInteger for thread-safe updates
    private final AtomicInteger connectionSendWindow = new AtomicInteger(DEFAULT_INITIAL_WINDOW_SIZE);
    private volatile int connectionRecvWindow = DEFAULT_INITIAL_WINDOW_SIZE;

    // Our limit for received header list size (not from server SETTINGS)
    private static final int DEFAULT_MAX_HEADER_LIST_SIZE = 8192;

    // Stream management - multiplexed!
    private final ConcurrentHashMap<Integer, H2Exchange> activeStreams = new ConcurrentHashMap<>();
    private final AtomicInteger activeStreamCount = new AtomicInteger(0);
    private volatile int lastStreamId = 0;

    // Background reader thread
    private final Thread readerThread;
    private volatile Throwable readerError;

    // Connection state
    private volatile State state = State.CONNECTED;
    private volatile boolean active = true;
    private volatile boolean goawayReceived = false;
    private volatile int goawayLastStreamId = Integer.MAX_VALUE;

    // Stream-level timeouts
    private final Duration readTimeout;
    private final Duration writeTimeout;

    /**
     * Create an HTTP/2 connection from a connected socket.
     *
     * <p>The socket must already be connected and TLS handshake completed (if applicable).
     * This constructor sends the connection preface and negotiates settings.
     *
     * @param socket the connected socket
     * @param route the connection route
     * @param readTimeout timeout for waiting on response data
     * @param writeTimeout timeout for waiting on flow control window
     * @throws IOException if connection preface fails
     */
    public H2Connection(Socket socket, Route route, Duration readTimeout, Duration writeTimeout) throws IOException {
        this.socket = socket;
        // Use unsynchronized buffered streams (safe because reader/writer threads have exclusive access)
        var socketIn = new UnsyncBufferedInputStream(socket.getInputStream(), 8192);
        this.socketOut = new UnsyncBufferedOutputStream(socket.getOutputStream(), 8192);
        this.route = route;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.frameCodec = new H2FrameCodec(socketIn, socketOut, DEFAULT_MAX_FRAME_SIZE);

        // Initialize HPACK decoder for responses (only accessed by reader thread)
        this.hpackDecoder = new HpackDecoder(DEFAULT_HEADER_TABLE_SIZE);

        // Perform connection preface BEFORE starting encoder thread
        // (preface writes directly to frameCodec, encoder thread takes over after)
        try {
            sendConnectionPreface();
            receiveServerPreface();
        } catch (IOException e) {
            close();
            throw new IOException("HTTP/2 connection preface failed", e);
        }

        // Create combined encoder/writer AFTER connection preface
        // This thread handles both HPACK encoding AND all frame writes
        this.streamWriter = new H2StreamWriter(
                this,
                frameCodec,
                DEFAULT_HEADER_TABLE_SIZE,
                "h2-writer-" + route.host());

        // Start background reader thread (dispatches incoming frames)
        this.readerThread = Thread.ofVirtual()
                .name("h2-reader-" + route.host())
                .start(this::readerLoop);
    }

    /**
     * Background reader loop - dispatches frames to streams.
     */
    private void readerLoop() {
        try {
            while (state == State.CONNECTED) {
                H2FrameCodec.Frame frame = frameCodec.readFrame();
                if (frame == null) {
                    // EOF - connection closed by peer
                    break;
                }

                dispatchFrame(frame);
            }
        } catch (IOException e) {
            if (state == State.CONNECTED) {
                readerError = e;
                active = false;
                LOGGER.debug("Reader thread error for {}: {}", route, e.getMessage());
            }
        } finally {
            // Stop the encoder immediately on connection failure
            // (don't wait for graceful drain - connection is dead)
            if (streamWriter != null) {
                streamWriter.shutdownNow();
            }

            // Signal all active streams that connection is closing
            for (H2Exchange exchange : activeStreams.values()) {
                exchange.signalConnectionClosed(readerError);
            }
            state = State.CLOSED;

            // Close socket to release resources promptly
            // (don't wait for pool/caller to notice the connection is dead)
            try {
                socket.close();
            } catch (IOException ignored) {
                // Best effort - socket may already be closed
            }
        }
    }

    /**
     * Dispatch a frame to the appropriate handler.
     *
     * <p>Stream-level frames are converted to {@link StreamEvent}s and enqueued
     * to the exchange. HPACK decoding happens here to ensure dynamic table
     * consistency across all streams.
     */
    private void dispatchFrame(H2FrameCodec.Frame frame) throws IOException {
        int streamId = frame.streamId();

        if (streamId == 0) {
            // Connection-level frame
            handleConnectionFrame(frame);
        } else {
            // Handle HEADERS frames that may need CONTINUATION
            if (frame.type() == FRAME_TYPE_HEADERS && !frame.hasFlag(FLAG_END_HEADERS)) {
                // Read complete header block including CONTINUATION frames
                byte[] headerBlock = frameCodec.readHeaderBlock(frame);
                // Create a synthetic frame with complete headers
                frame = new H2FrameCodec.Frame(
                        FRAME_TYPE_HEADERS,
                        frame.flags() | FLAG_END_HEADERS,
                        streamId,
                        headerBlock,
                        headerBlock.length);
            }

            // Stream-level frame
            H2Exchange exchange = activeStreams.get(streamId);
            if (exchange != null) {
                dispatchStreamFrame(exchange, frame, streamId);
            } else {
                // Frame for unknown stream
                handleFrameForUnknownStream(frame, streamId);
            }
        }
    }

    /**
     * Handle frames received for streams not in our active set.
     *
     * <p>Per RFC 9113 Section 5.1, after sending RST_STREAM we must be prepared to receive
     * and ignore additional frames that were in-flight. This commonly occurs when we close
     * a stream before fully reading the response - the server may have already sent DATA.
     *
     * <p>For DATA frames: consume the connection recv window (for flow control) and ignore.
     * For HEADERS frames: decode headers (to maintain HPACK state) and ignore.
     * For other frames (WINDOW_UPDATE, RST_STREAM): silently ignore.
     */
    private void handleFrameForUnknownStream(H2FrameCodec.Frame frame, int streamId) throws IOException {
        if (frame.type() == FRAME_TYPE_DATA) {
            // Consume connection-level flow control window for ignored DATA
            byte[] payload = frame.payload();
            if (payload != null && payload.length > 0) {
                consumeConnectionRecvWindow(payload.length);
            }
            LOGGER.trace("Ignoring DATA frame for closed stream {}", streamId);
        } else if (frame.type() == FRAME_TYPE_HEADERS) {
            // Must decode headers to keep HPACK decoder state in sync
            byte[] headerBlock = frame.payload();
            if (headerBlock != null && headerBlock.length > 0) {
                decodeHeaders(headerBlock);
            }
            LOGGER.trace("Ignoring HEADERS frame for closed stream {}", streamId);
        }
        // Other frame types (WINDOW_UPDATE, RST_STREAM) are silently ignored
    }

    /**
     * Dispatch a stream-level frame as a StreamEvent.
     */
    private void dispatchStreamFrame(H2Exchange exchange, H2FrameCodec.Frame frame, int streamId)
            throws IOException {
        switch (frame.type()) {
            case FRAME_TYPE_HEADERS -> {
                // HPACK decoding MUST happen here in the reader thread to ensure
                // dynamic table updates are processed in frame order across all streams.
                byte[] headerBlock = frame.payload();
                List<HpackDecoder.HeaderField> decoded;
                if (headerBlock != null && headerBlock.length > 0) {
                    decoded = decodeHeaders(headerBlock);
                } else {
                    decoded = List.of();
                }

                boolean endStream = frame.hasFlag(FLAG_END_STREAM);
                exchange.enqueueEvent(new StreamEvent.Headers(decoded, endStream));
            }

            case FRAME_TYPE_DATA -> {
                byte[] payload = frame.payload();
                boolean endStream = frame.hasFlag(FLAG_END_STREAM);

                // Handle flow control: update connection receive window
                if (payload != null && payload.length > 0) {
                    consumeConnectionRecvWindow(payload.length);
                }

                // Create DataChunk event
                StreamEvent.DataChunk chunk;
                if (payload != null && payload.length > 0) {
                    chunk = new StreamEvent.DataChunk(payload, 0, payload.length, endStream);
                } else if (endStream) {
                    chunk = StreamEvent.DataChunk.END;
                } else {
                    chunk = StreamEvent.DataChunk.EMPTY;
                }

                exchange.enqueueEvent(chunk);
            }

            case FRAME_TYPE_RST_STREAM -> {
                // RST_STREAM: signal stream error so consumers fail fast
                int errorCode = frame.parseRstStream();
                H2Exception error = new H2Exception(errorCode,
                        streamId,
                        "Stream reset by server: " + H2Constants.errorCodeName(errorCode));
                exchange.signalStreamError(error);
            }

            case FRAME_TYPE_WINDOW_UPDATE -> {
                // WINDOW_UPDATE affects send window, not response reading
                int increment = frame.parseWindowUpdate();
                exchange.updateStreamSendWindow(increment);
            }

            case FRAME_TYPE_PUSH_PROMISE -> {
                // We disable push, so this is a protocol error
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "Received PUSH_PROMISE but server push is disabled");
            }

            default -> {
                // Unknown frame types are ignored per spec
            }
        }
    }

    /**
     * Handle connection-level frames (stream ID 0).
     */
    private void handleConnectionFrame(H2FrameCodec.Frame frame) throws IOException {
        switch (frame.type()) {
            case FRAME_TYPE_SETTINGS -> {
                if (!frame.hasFlag(FLAG_ACK)) {
                    applyRemoteSettings(frame);
                    // Submit SETTINGS ACK to writer thread
                    streamWriter.submitControlFrame(new H2StreamWriter.WorkItem.WriteSettingsAck());
                }
            }
            case FRAME_TYPE_PING -> {
                if (!frame.hasFlag(FLAG_ACK)) {
                    // Submit PING ACK to writer thread
                    streamWriter.submitControlFrame(new H2StreamWriter.WorkItem.WritePing(frame.payload(), true));
                }
            }
            case FRAME_TYPE_GOAWAY -> {
                int[] goaway = frame.parseGoaway();
                handleGoaway(goaway[0], goaway[1]);
            }
            case FRAME_TYPE_WINDOW_UPDATE -> {
                int increment = frame.parseWindowUpdate();
                int newWindow = connectionSendWindow.addAndGet(increment);
                // Check for overflow per RFC 9113 (wrap-around to negative)
                if (newWindow < 0) {
                    throw new H2Exception(ERROR_FLOW_CONTROL_ERROR,
                            "Connection send window overflow: " + newWindow);
                }
                // Wake up any streams waiting for send window
                for (H2Exchange exchange : activeStreams.values()) {
                    exchange.signalWindowUpdate();
                }
            }
            default -> {
                // Ignore unknown connection-level frames
            }
        }
    }

    /**
     * Send client connection preface.
     * RFC 9113 Section 3.4
     */
    private void sendConnectionPreface() throws IOException {
        // 1. Send magic string
        socketOut.write(CONNECTION_PREFACE);

        // 2. Send SETTINGS frame with our preferences
        frameCodec.writeSettings(
                SETTINGS_MAX_CONCURRENT_STREAMS,
                100, // We can handle 100 concurrent streams
                SETTINGS_INITIAL_WINDOW_SIZE,
                65535, // Default 64KB window
                SETTINGS_MAX_FRAME_SIZE,
                16384, // Default 16KB frames
                SETTINGS_ENABLE_PUSH,
                0 // Disable server push
        );
        frameCodec.flush();
    }

    // Timeout for receiving SETTINGS frame during connection setup (RFC 9113 Section 6.5.3)
    private static final int SETTINGS_TIMEOUT_MS = 10_000; // 10 seconds

    /**
     * Receive and process server preface.
     *
     * <p>RFC 9113 Section 6.5.3 recommends that implementations provide a way to
     * bound the time within which a response to a SETTINGS frame is expected.
     */
    private void receiveServerPreface() throws IOException {
        // Set socket timeout for SETTINGS frame (RFC 9113 Section 6.5.3)
        int originalTimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(SETTINGS_TIMEOUT_MS);

            // Read server's SETTINGS frame
            H2FrameCodec.Frame frame;
            try {
                frame = frameCodec.readFrame();
            } catch (SocketTimeoutException e) {
                throw new H2Exception(ERROR_SETTINGS_TIMEOUT,
                        "Timeout waiting for server SETTINGS frame");
            }

            if (frame == null) {
                throw new IOException("Connection closed before receiving server SETTINGS");
            }

            if (frame.type() != FRAME_TYPE_SETTINGS) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "Expected SETTINGS frame, got " + H2Constants.frameTypeName(frame.type()));
            }

            if (frame.hasFlag(FLAG_ACK)) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        "First SETTINGS frame must not be ACK");
            }

            // Apply server settings
            applyRemoteSettings(frame);

            // Send SETTINGS ACK
            frameCodec.writeSettingsAck();
            frameCodec.flush();
        } finally {
            // Restore original timeout (usually 0 = infinite for the reader loop)
            socket.setSoTimeout(originalTimeout);
        }
    }

    /**
     * Apply settings received from peer.
     */
    private void applyRemoteSettings(H2FrameCodec.Frame frame) throws IOException {
        int[] settings = frame.parseSettings();
        for (int i = 0; i < settings.length; i += 2) {
            int id = settings[i];
            int value = settings[i + 1];

            switch (id) {
                case SETTINGS_HEADER_TABLE_SIZE:
                    remoteHeaderTableSize = value;
                    // Update encoder table size (applied on next encode)
                    streamWriter.setMaxTableSize(value);
                    break;
                case SETTINGS_ENABLE_PUSH:
                    // We disable push, ignore server's preference
                    break;
                case SETTINGS_MAX_CONCURRENT_STREAMS:
                    remoteMaxConcurrentStreams = value;
                    break;
                case SETTINGS_INITIAL_WINDOW_SIZE:
                    // Value is unsigned 32-bit in protocol; negative means > 2^31-1
                    if (value < 0) {
                        throw new H2Exception(ERROR_FLOW_CONTROL_ERROR,
                                "Invalid INITIAL_WINDOW_SIZE: " + (value & 0xFFFFFFFFL));
                    }
                    // Update window for all active streams
                    int delta = value - remoteInitialWindowSize;
                    remoteInitialWindowSize = value;
                    for (H2Exchange exchange : activeStreams.values()) {
                        exchange.adjustSendWindow(delta);
                    }
                    break;
                case SETTINGS_MAX_FRAME_SIZE:
                    if (value < MIN_MAX_FRAME_SIZE || value > MAX_MAX_FRAME_SIZE) {
                        throw new H2Exception(ERROR_PROTOCOL_ERROR,
                                "Invalid MAX_FRAME_SIZE: " + value);
                    }
                    remoteMaxFrameSize = value;
                    break;
                case SETTINGS_MAX_HEADER_LIST_SIZE:
                    // Server's limit for header list size we can send
                    remoteMaxHeaderListSize = value;
                    break;
                default:
                    // Unknown settings are ignored per spec
                    break;
            }
        }
    }

    @Override
    public HttpExchange newExchange(HttpRequest request) throws IOException {
        if (state != State.CONNECTED) {
            throw new IOException("Connection is not in CONNECTED state: " + state);
        }

        if (goawayReceived) {
            // Check if new stream ID would exceed the last allowed stream
            int nextId = streamWriter.getNextStreamId();
            if (nextId > goawayLastStreamId) {
                throw new IOException("Connection received GOAWAY with lastStreamId=" +
                        goawayLastStreamId + ", cannot create stream " + nextId);
            }
        }

        // Fast-fail check (encoder will recheck under serialization)
        if (activeStreamCount.get() >= remoteMaxConcurrentStreams) {
            throw new IOException("Connection at max concurrent streams: " + activeStreamCount.get());
        }

        // Pre-create the exchange (stream ID will be set by encoder)
        H2Exchange exchange = new H2Exchange(this, request, readTimeout, writeTimeout);

        // Determine if request has a body
        boolean hasBody = request.body() != null && request.body().contentLength() != 0;
        boolean endStream = !hasBody;

        CompletableFuture<Integer> streamIdFuture = new CompletableFuture<>();
        CompletableFuture<Void> writeComplete = new CompletableFuture<>();

        // Submit to writer thread (blocks if queue is full, provides backpressure)
        var encodeHeaders = new H2StreamWriter.WorkItem.EncodeHeaders(
                request,
                exchange,
                endStream,
                streamIdFuture,
                writeComplete);

        if (!streamWriter.submitWork(encodeHeaders, writeTimeout.toMillis())) {
            throw new IOException("Write queue full - connection overloaded (timeout after " + writeTimeout + ")");
        }

        // Wait for encoding to complete (stream ID assigned, queued to writer)
        int streamId;
        try {
            streamId = streamIdFuture.join();
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException("Encoding failed", cause != null ? cause : e);
        }

        // Wait for write to complete
        try {
            writeComplete.join();
        } catch (Exception e) {
            // Write failed - cleanup and rethrow
            activeStreams.remove(streamId);
            activeStreamCount.decrementAndGet();
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException("Failed to write headers", cause != null ? cause : e);
        }

        return exchange;
    }

    /**
     * Unregister a stream when it completes.
     */
    @Override
    public void unregisterStream(int streamId) {
        if (activeStreams.remove(streamId) != null) {
            activeStreamCount.decrementAndGet();
        }
    }

    // ==================== StreamManager interface implementation ====================

    @Override
    public boolean tryReserveStream() {
        while (true) {
            int current = activeStreamCount.get();
            if (current >= remoteMaxConcurrentStreams) {
                return false;
            }
            if (activeStreamCount.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    @Override
    public void releaseStreamSlot() {
        activeStreamCount.decrementAndGet();
    }

    @Override
    public void registerStream(int streamId, H2Exchange exchange) {
        activeStreams.put(streamId, exchange);
    }

    @Override
    public void setLastStreamId(int streamId) {
        this.lastStreamId = streamId;
    }

    @Override
    public boolean isAcceptingStreams() {
        return state == State.CONNECTED && !goawayReceived;
    }

    @Override
    public int getRemoteMaxHeaderListSize() {
        return remoteMaxHeaderListSize;
    }

    // ==================== End StreamManager interface ====================

    /**
     * Queue a DATA frame for writing via the encoder/writer thread.
     *
     * <p>Flow control must already be checked by the caller. This method
     * queues the write and blocks until it completes.
     *
     * @param streamId the stream ID
     * @param data the data buffer
     * @param offset offset into the buffer
     * @param length number of bytes to write
     * @param flags frame flags (e.g., FLAG_END_STREAM)
     * @throws IOException if the write fails
     */
    void queueData(int streamId, byte[] data, int offset, int length, int flags) throws IOException {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        if (!streamWriter.submitWork(
                new H2StreamWriter.WorkItem.WriteData(streamId, data, offset, length, flags, completion),
                writeTimeout.toMillis())) {
            throw new IOException("Write queue full - connection overloaded");
        }

        try {
            completion.join();
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException("Failed to write data", cause != null ? cause : e);
        }
    }

    /**
     * Get the current number of active streams.
     */
    public int getActiveStreamCount() {
        return activeStreamCount.get();
    }

    /**
     * Check if connection can accept more streams.
     */
    public boolean canAcceptMoreStreams() {
        return isActive() && activeStreamCount.get() < remoteMaxConcurrentStreams;
    }

    @Override
    public HttpVersion httpVersion() {
        return HttpVersion.HTTP_2;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public boolean validateForReuse() {
        if (!active) {
            return false;
        }

        // Check socket state
        if (socket.isClosed() || socket.isInputShutdown() || socket.isOutputShutdown()) {
            LOGGER.debug("Connection to {} is closed or half-closed", route);
            active = false;
            state = State.CLOSED;
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
            return (protocol != null && !protocol.isEmpty()) ? protocol : "h2";
        }
        return "h2";
    }

    // Timeout for graceful shutdown - allows pending writes to complete
    private static final int GRACEFUL_SHUTDOWN_MS = 1000;

    @Override
    public void close() throws IOException {
        if (state == State.CLOSED) {
            return;
        }

        active = false;

        // Handle both CONNECTED and SHUTTING_DOWN states
        State previousState = state;
        state = State.SHUTTING_DOWN;

        // Send GOAWAY before closing encoder
        if (previousState == State.CONNECTED) {
            streamWriter.submitControlFrame(
                    new H2StreamWriter.WorkItem.WriteGoaway(lastStreamId, ERROR_NO_ERROR, null));
        }

        // Close encoder (gracefully drains pending requests, sends GOAWAY, then stops)
        if (streamWriter != null) {
            streamWriter.close();
        }

        state = State.CLOSED;

        // Close socket - this will unblock the reader thread's blocking read.
        // Thread.interrupt() doesn't work for socket I/O.
        socket.close();

        // Wait briefly for reader thread to notice the close
        if (readerThread != null) {
            try {
                readerThread.join(100);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Decode HPACK header block.
     *
     * <p><b>IMPORTANT:</b> This method MUST be called only from the reader thread,
     * in frame arrival order. HPACK uses a connection-global dynamic table that
     * is updated during decoding. If headers are decoded out of wire order (e.g.,
     * by different stream threads racing), the dynamic table state becomes corrupted
     * and subsequent header blocks will decode incorrectly.
     *
     * <p>Per RFC 9113 Section 4.3, HPACK decoding errors MUST be treated as
     * connection errors of type COMPRESSION_ERROR.
     *
     * @param headerBlock the encoded header block
     * @return decoded header fields
     * @throws IOException if decoding fails
     * @throws H2Exception if header list size exceeds limit or HPACK decoding fails
     */
    List<HpackDecoder.HeaderField> decodeHeaders(byte[] headerBlock) throws IOException {
        // Check encoded size first (quick rejection)
        int maxHeaderListSize = DEFAULT_MAX_HEADER_LIST_SIZE;
        if (headerBlock.length > maxHeaderListSize) {
            throw new H2Exception(ERROR_ENHANCE_YOUR_CALM,
                    "Header block size " + headerBlock.length + " exceeds limit " + maxHeaderListSize);
        }

        List<HpackDecoder.HeaderField> headers;
        // HPACK decoder is stateful (dynamic table), must be synchronized
        synchronized (hpackDecoder) {
            try {
                headers = hpackDecoder.decodeBlock(headerBlock, 0, headerBlock.length);
            } catch (IOException e) {
                // RFC 9113 Section 4.3: HPACK decoding errors are COMPRESSION_ERROR
                active = false;
                LOGGER.debug("HPACK decoding failed for {}: {}", route, e.getMessage());
                throw new H2Exception(ERROR_COMPRESSION_ERROR, "HPACK decoding failed: " + e.getMessage());
            } catch (IndexOutOfBoundsException e) {
                // Dynamic table index out of range - HPACK state mismatch
                // This is a fatal connection error per RFC 9113 Section 4.3
                active = false;
                LOGGER.debug("HPACK dynamic table mismatch for {}: {}", route, e.getMessage());
                throw new H2Exception(ERROR_COMPRESSION_ERROR, "HPACK state mismatch: " + e.getMessage());
            }
        }

        // Check decoded size (name + value + 32 bytes overhead per RFC 7541)
        int decodedSize = 0;
        for (HpackDecoder.HeaderField field : headers) {
            decodedSize += field.name().length() + field.value().length() + 32;
            if (decodedSize > maxHeaderListSize) {
                throw new H2Exception(ERROR_ENHANCE_YOUR_CALM,
                        "Decoded header list size exceeds limit " + maxHeaderListSize);
            }
        }

        return headers;
    }

    /**
     * Get the remote initial window size.
     */
    int getRemoteInitialWindowSize() {
        return remoteInitialWindowSize;
    }

    /**
     * Get the remote max frame size.
     */
    int getRemoteMaxFrameSize() {
        return remoteMaxFrameSize;
    }

    /**
     * Update connection-level send window.
     *
     * @param delta window size change (positive for WINDOW_UPDATE)
     */
    void updateConnectionSendWindow(int delta) {
        connectionSendWindow.addAndGet(delta);
    }

    /**
     * Get current connection send window.
     */
    int getConnectionSendWindow() {
        return connectionSendWindow.get();
    }

    /**
     * Consume bytes from connection send window.
     *
     * @param bytes number of bytes to consume
     */
    void consumeConnectionSendWindow(int bytes) {
        connectionSendWindow.addAndGet(-bytes);
    }

    /**
     * Queue a stream-level WINDOW_UPDATE frame.
     *
     * @param streamId the stream ID
     * @param increment the window size increment
     * @throws IOException if the write queue is full
     */
    void queueWindowUpdate(int streamId, int increment) throws IOException {
        if (!streamWriter.submitControlFrame(
                new H2StreamWriter.WorkItem.WriteWindowUpdate(streamId, increment))) {
            throw new IOException("Write queue full - cannot send WINDOW_UPDATE");
        }
    }

    /**
     * Queue a RST_STREAM frame (fire-and-forget, doesn't wait for completion).
     *
     * @param streamId the stream ID
     * @param errorCode the error code
     * @throws IOException if the write queue is full
     */
    void queueRst(int streamId, int errorCode) throws IOException {
        // Fire-and-forget - no need to wait for completion
        if (!streamWriter.submitControlFrame(
                new H2StreamWriter.WorkItem.WriteRst(streamId, errorCode, new CompletableFuture<>()))) {
            throw new IOException("Write queue full - cannot send RST_STREAM");
        }
    }

    /**
     * Consume bytes from the connection receive window.
     *
     * <p>In HTTP/2, data received counts against both the stream window AND
     * the connection window. This method tracks connection-level consumption
     * and queues WINDOW_UPDATE when the window gets low.
     *
     * @param bytes number of bytes received
     * @throws IOException if queue is full after timeout or thread is interrupted
     */
    void consumeConnectionRecvWindow(int bytes) throws IOException {
        int newWindow;
        int increment = 0;
        synchronized (this) {
            connectionRecvWindow -= bytes;
            newWindow = connectionRecvWindow;

            // Send WINDOW_UPDATE when window gets below half
            if (newWindow < DEFAULT_INITIAL_WINDOW_SIZE / 2) {
                increment = DEFAULT_INITIAL_WINDOW_SIZE - newWindow;
                connectionRecvWindow += increment;
            }
        }

        if (increment > 0) {
            // Queue connection-level WINDOW_UPDATE
            if (!streamWriter.submitWork(
                    new H2StreamWriter.WorkItem.WriteWindowUpdate(0, increment),
                    writeTimeout.toMillis())) {
                throw new IOException("Write queue full - cannot send connection WINDOW_UPDATE");
            }
        }
    }

    /**
     * Handle GOAWAY frame from server.
     *
     * <p>Per RFC 9113 Section 6.8, streams with IDs greater than the last stream ID
     * that was processed by the server should be considered refused and retried.
     */
    void handleGoaway(int lastStreamId, int errorCode) {
        goawayReceived = true;
        goawayLastStreamId = lastStreamId;
        active = false;

        if (errorCode != ERROR_NO_ERROR) {
            LOGGER.debug("Server sent GOAWAY to {}: {}", route, H2Constants.errorCodeName(errorCode));
        }

        state = State.SHUTTING_DOWN;

        // RFC 9113 Section 6.8: Signal streams with ID > lastStreamId that they were refused
        // These streams were initiated but not processed by the server
        H2Exception refusedError = new H2Exception(errorCode,
                "Stream affected by GOAWAY (lastStreamId=" + lastStreamId +
                        ", error=" + H2Constants.errorCodeName(errorCode) + ")");
        for (var entry : activeStreams.entrySet()) {
            int streamId = entry.getKey();
            if (streamId > lastStreamId) {
                H2Exchange exchange = entry.getValue();
                exchange.signalConnectionClosed(refusedError);
            }
        }

        // The encoder will handle failing queued requests when it shuts down
        // Streams > lastStreamId that are already queued will fail when processed
    }
}
