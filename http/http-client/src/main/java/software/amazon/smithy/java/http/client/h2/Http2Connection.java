/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static software.amazon.smithy.java.http.client.h2.Http2Constants.CONNECTION_PREFACE;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.DEFAULT_HEADER_TABLE_SIZE;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.DEFAULT_INITIAL_WINDOW_SIZE;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.DEFAULT_MAX_CONCURRENT_STREAMS;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.DEFAULT_MAX_FRAME_SIZE;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.ERROR_COMPRESSION_ERROR;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.ERROR_ENHANCE_YOUR_CALM;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.ERROR_FLOW_CONTROL_ERROR;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.ERROR_NO_ERROR;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.ERROR_PROTOCOL_ERROR;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.ERROR_REFUSED_STREAM;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.ERROR_SETTINGS_TIMEOUT;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.ERROR_STREAM_CLOSED;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.FLAG_ACK;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.FLAG_END_HEADERS;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.FLAG_END_STREAM;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.FRAME_TYPE_DATA;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.FRAME_TYPE_GOAWAY;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.FRAME_TYPE_HEADERS;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.FRAME_TYPE_PING;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.FRAME_TYPE_PUSH_PROMISE;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.FRAME_TYPE_RST_STREAM;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.FRAME_TYPE_SETTINGS;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.FRAME_TYPE_WINDOW_UPDATE;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.MAX_MAX_FRAME_SIZE;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.MIN_MAX_FRAME_SIZE;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.PSEUDO_AUTHORITY;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.PSEUDO_METHOD;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.PSEUDO_PATH;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.PSEUDO_SCHEME;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.SETTINGS_ENABLE_PUSH;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.SETTINGS_HEADER_TABLE_SIZE;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.SETTINGS_INITIAL_WINDOW_SIZE;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.SETTINGS_MAX_CONCURRENT_STREAMS;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.SETTINGS_MAX_FRAME_SIZE;
import static software.amazon.smithy.java.http.client.h2.Http2Constants.SETTINGS_MAX_HEADER_LIST_SIZE;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
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
import software.amazon.smithy.java.http.client.h2.hpack.HpackEncoder;
import software.amazon.smithy.java.io.ByteBufferOutputStream;
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
public final class Http2Connection implements HttpConnection {
    /**
     * Internal connection state.
     */
    private enum State {
        CONNECTED,
        SHUTTING_DOWN,
        CLOSED
    }

    // Headers that must not be sent over HTTP/2 (connection-specific)
    private static final Set<String> CONNECTION_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-connection",
            "transfer-encoding",
            "upgrade",
            "host" // host is replaced by :authority
    );

    // Headers that should not be indexed in HPACK (contain sensitive data)
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "cookie",
            "proxy-authorization",
            "set-cookie");

    /**
     * Write requests for the writer thread queue.
     *
     * <p>All frame-writing operations go through this queue to ensure
     * single-threaded access to the socket and proper ordering. The writer
     * thread is the sole owner of frameCodec.
     */
    private sealed interface WriteRequest {
        // Stream frames (require completion signaling)
        record Headers(
                int streamId,
                byte[] headerBlock,
                boolean endStream,
                CompletableFuture<Void> completion) implements WriteRequest {}

        record Data(
                int streamId,
                byte[] data,
                int offset,
                int length,
                int flags,
                CompletableFuture<Void> completion) implements WriteRequest {}

        record Rst(
                int streamId,
                int errorCode,
                CompletableFuture<Void> completion) implements WriteRequest {}

        // Connection control frames (fire-and-forget, no completion needed)
        record Goaway(int lastStreamId, int errorCode, String debugData) implements WriteRequest {}

        record WindowUpdate(int streamId, int increment) implements WriteRequest {}

        record SettingsAck() implements WriteRequest {}

        record Ping(byte[] payload, boolean ack) implements WriteRequest {}

        /** Marker to shut down the writer thread. */
        record Shutdown() implements WriteRequest {}
    }

    private static final InternalLogger LOGGER = InternalLogger.getLogger(Http2Connection.class);

    private final Socket socket;
    private final UnsyncBufferedOutputStream socketOut;
    private final Route route;
    private final Http2FrameCodec frameCodec;

    // Writer thread and queue - all writes go through here (single owner of frameCodec)
    // Bounded to provide backpressure when connection is overloaded
    private static final int WRITE_QUEUE_CAPACITY = 512;
    private final BlockingQueue<WriteRequest> writeQueue = new LinkedBlockingQueue<>(WRITE_QUEUE_CAPACITY);
    private final Thread writerThread;

    // HPACK codecs (shared per connection, protected by locks)
    private final HpackEncoder hpackEncoder;
    private final HpackDecoder hpackDecoder;
    private final ReentrantLock hpackEncoderLock = new ReentrantLock();

    // Reusable buffer for header encoding (protected by hpackEncoderLock)
    private final ByteBufferOutputStream headerEncodeBuffer = new ByteBufferOutputStream(512);

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
    private final AtomicInteger nextStreamId = new AtomicInteger(1); // Client uses odd IDs
    private final ConcurrentHashMap<Integer, Http2Exchange> activeStreams = new ConcurrentHashMap<>();
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
    public Http2Connection(Socket socket, Route route, Duration readTimeout, Duration writeTimeout) throws IOException {
        this.socket = socket;
        // Use unsynchronized buffered streams (safe because reader/writer threads have exclusive access)
        var socketIn = new UnsyncBufferedInputStream(socket.getInputStream(), 8192);
        this.socketOut = new UnsyncBufferedOutputStream(socket.getOutputStream(), 8192);
        this.route = route;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
        this.frameCodec = new Http2FrameCodec(socketIn, socketOut, DEFAULT_MAX_FRAME_SIZE);

        // Initialize HPACK with default table size
        this.hpackEncoder = new HpackEncoder(DEFAULT_HEADER_TABLE_SIZE);
        this.hpackDecoder = new HpackDecoder(DEFAULT_HEADER_TABLE_SIZE);

        // Perform connection preface
        try {
            sendConnectionPreface();
            receiveServerPreface();
        } catch (IOException e) {
            close();
            throw new IOException("HTTP/2 connection preface failed", e);
        }

        // Start background writer thread (handles all frame writes)
        this.writerThread = Thread.ofVirtual()
                .name("h2-writer-" + route.host())
                .start(this::writerLoop);

        // Start background reader thread (dispatches incoming frames)
        this.readerThread = Thread.ofVirtual()
                .name("h2-reader-" + route.host())
                .start(this::readerLoop);
    }

    /**
     * Background writer loop - processes write requests from the queue.
     *
     * <p>This decouples HPACK encoding from socket I/O. Encoding threads
     * just queue their encoded headers and wait on a future, while this
     * thread handles the actual writes in queue order (preserving HPACK
     * dynamic table consistency).
     *
     * <p>Uses batching: drains all available requests, writes them without
     * flushing, then flushes once. This reduces syscalls under high concurrency.
     */
    private void writerLoop() {
        var batch = new ArrayList<WriteRequest>(64);

        try {
            while (true) {
                // Block for the first request
                WriteRequest req = writeQueue.take();

                if (req instanceof WriteRequest.Shutdown) {
                    break;
                }

                batch.add(req);

                // Drain opportunistically (non-blocking)
                while ((req = writeQueue.poll()) != null) {
                    if (req instanceof WriteRequest.Shutdown) {
                        // Process batch first, then exit
                        processBatch(batch);
                        return;
                    }
                    batch.add(req);
                }

                processBatch(batch);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failAll(batch, new IOException("Writer thread interrupted", e));
            failAllQueued(new IOException("Writer thread interrupted", e));
        } catch (Exception e) {
            failAll(batch, e);
            failAllQueued(e);
        }
    }

    /**
     * Process a batch of write requests with a single flush at the end.
     *
     * <p>The writer thread is the sole owner of frameCodec, so no locking needed.
     */
    private void processBatch(ArrayList<WriteRequest> batch) {
        if (batch.isEmpty()) {
            return;
        }

        try {
            // Write all frames without flushing
            for (WriteRequest req : batch) {
                switch (req) {
                    case WriteRequest.Headers h ->
                        frameCodec
                                .writeHeaders(h.streamId(), h.headerBlock(), 0, h.headerBlock().length, h.endStream());
                    case WriteRequest.Data d ->
                        frameCodec.writeFrame(FRAME_TYPE_DATA,
                                d.flags(),
                                d.streamId(),
                                d.data(),
                                d.offset(),
                                d.length());
                    case WriteRequest.Rst r ->
                        frameCodec.writeRstStream(r.streamId(), r.errorCode());
                    case WriteRequest.Goaway g ->
                        frameCodec.writeGoaway(g.lastStreamId(), g.errorCode(), g.debugData());
                    case WriteRequest.WindowUpdate w ->
                        frameCodec.writeWindowUpdate(w.streamId(), w.increment());
                    case WriteRequest.SettingsAck s ->
                        frameCodec.writeSettingsAck();
                    case WriteRequest.Ping p ->
                        frameCodec.writeFrame(FRAME_TYPE_PING, p.ack() ? FLAG_ACK : 0, 0, p.payload());
                    case WriteRequest.Shutdown s -> {
                        // Handled by caller
                    }
                }
            }

            // Single flush for entire batch
            frameCodec.flush();

            // Complete futures for requests that have them
            for (WriteRequest req : batch) {
                completeRequest(req, null);
            }
        } catch (IOException e) {
            // Fail all pending
            failAll(batch, e);
        } finally {
            batch.clear();
        }
    }

    /**
     * Complete a write request's future (success or failure).
     *
     * <p>Only stream-level requests (Headers, Data, Rst) have completion futures.
     * Control frames are fire-and-forget.
     */
    private void completeRequest(WriteRequest req, IOException error) {
        CompletableFuture<Void> completion = switch (req) {
            case WriteRequest.Headers h -> h.completion();
            case WriteRequest.Data d -> d.completion();
            case WriteRequest.Rst r -> r.completion();
            // Control frames are fire-and-forget
            case WriteRequest.Goaway g -> null;
            case WriteRequest.WindowUpdate w -> null;
            case WriteRequest.SettingsAck s -> null;
            case WriteRequest.Ping p -> null;
            case WriteRequest.Shutdown s -> null;
        };
        if (completion != null) {
            if (error == null) {
                completion.complete(null);
            } else {
                completion.completeExceptionally(error);
            }
        }
    }

    /**
     * Fail all requests in a batch.
     */
    private void failAll(List<WriteRequest> batch, Exception e) {
        for (WriteRequest req : batch) {
            completeRequest(req, e instanceof IOException ioe ? ioe : new IOException(e));
        }
        batch.clear();
    }

    /**
     * Fail all remaining requests in the queue.
     */
    private void failAllQueued(Exception e) {
        IOException ioe = e instanceof IOException io ? io : new IOException(e);
        WriteRequest req;
        while ((req = writeQueue.poll()) != null) {
            completeRequest(req, ioe);
        }
    }

    /**
     * Queue a stream frame (Headers, Data) with blocking backpressure.
     *
     * <p>Blocks up to writeTimeout if queue is full, then fails.
     */
    private void queueWrite(WriteRequest request) throws IOException {
        try {
            if (!writeQueue.offer(request, writeTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IOException("Write queue full - connection overloaded (timeout after " + writeTimeout + ")");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to write", e);
        }
    }

    /**
     * Queue a control frame (WindowUpdate, Ping, Settings, etc.) with fail-fast behavior.
     *
     * <p>If queue is full, throws immediately - connection is overloaded beyond recovery.
     */
    private void queueControlFrame(WriteRequest request) throws IOException {
        if (!writeQueue.offer(request)) {
            throw new IOException("Write queue full - cannot send control frame, connection overloaded");
        }
    }

    /**
     * Background reader loop - dispatches frames to streams.
     */
    private void readerLoop() {
        try {
            while (state == State.CONNECTED) {
                Http2FrameCodec.Frame frame = frameCodec.readFrame();
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
            // Signal all active streams that connection is closing
            for (Http2Exchange exchange : activeStreams.values()) {
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
    private void dispatchFrame(Http2FrameCodec.Frame frame) throws IOException {
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
                frame = new Http2FrameCodec.Frame(
                        FRAME_TYPE_HEADERS,
                        frame.flags() | FLAG_END_HEADERS,
                        streamId,
                        headerBlock,
                        headerBlock.length);
            }

            // Stream-level frame
            Http2Exchange exchange = activeStreams.get(streamId);
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
     * <p>Per RFC 9113, certain frames on unknown streams are protocol errors:
     * <ul>
     *   <li>DATA/HEADERS on a closed or never-opened stream = STREAM_CLOSED error</li>
     *   <li>WINDOW_UPDATE/RST_STREAM on closed stream = silently ignore (benign race)</li>
     * </ul>
     */
    private void handleFrameForUnknownStream(Http2FrameCodec.Frame frame, int streamId) throws IOException {
        switch (frame.type()) {
            case FRAME_TYPE_DATA, FRAME_TYPE_HEADERS -> {
                throw new Http2Exception(ERROR_STREAM_CLOSED,
                        streamId,
                        "Received " + Http2Constants.frameTypeName(frame.type()) + " on unknown stream " + streamId);
            }
            case FRAME_TYPE_RST_STREAM, FRAME_TYPE_WINDOW_UPDATE -> {
                // RST_STREAM and WINDOW_UPDATE on closed streams are benign races - ignore
            }
            default -> {
                // Other frame types on unknown streams are silently ignored per spec
            }
        }
    }

    /**
     * Dispatch a stream-level frame as a StreamEvent.
     */
    private void dispatchStreamFrame(Http2Exchange exchange, Http2FrameCodec.Frame frame, int streamId)
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
                Http2Exception error = new Http2Exception(errorCode,
                        streamId,
                        "Stream reset by server: " + Http2Constants.errorCodeName(errorCode));
                exchange.signalStreamError(error);
            }

            case FRAME_TYPE_WINDOW_UPDATE -> {
                // WINDOW_UPDATE affects send window, not response reading
                int increment = frame.parseWindowUpdate();
                exchange.updateStreamSendWindow(increment);
            }

            case FRAME_TYPE_PUSH_PROMISE -> {
                // We disable push, so this is a protocol error
                throw new Http2Exception(ERROR_PROTOCOL_ERROR,
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
    private void handleConnectionFrame(Http2FrameCodec.Frame frame) throws IOException {
        switch (frame.type()) {
            case FRAME_TYPE_SETTINGS -> {
                if (!frame.hasFlag(FLAG_ACK)) {
                    applyRemoteSettings(frame);
                    // Queue SETTINGS ACK - use blocking since we're on reader thread
                    queueWrite(new WriteRequest.SettingsAck());
                }
            }
            case FRAME_TYPE_PING -> {
                if (!frame.hasFlag(FLAG_ACK)) {
                    // Queue PING ACK - use blocking since we're on reader thread
                    queueWrite(new WriteRequest.Ping(frame.payload(), true));
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
                    throw new Http2Exception(ERROR_FLOW_CONTROL_ERROR,
                            "Connection send window overflow: " + newWindow);
                }
                // Wake up any streams waiting for send window
                for (Http2Exchange exchange : activeStreams.values()) {
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
            Http2FrameCodec.Frame frame;
            try {
                frame = frameCodec.readFrame();
            } catch (SocketTimeoutException e) {
                throw new Http2Exception(ERROR_SETTINGS_TIMEOUT,
                        "Timeout waiting for server SETTINGS frame");
            }

            if (frame == null) {
                throw new IOException("Connection closed before receiving server SETTINGS");
            }

            if (frame.type() != FRAME_TYPE_SETTINGS) {
                throw new Http2Exception(ERROR_PROTOCOL_ERROR,
                        "Expected SETTINGS frame, got " + Http2Constants.frameTypeName(frame.type()));
            }

            if (frame.hasFlag(FLAG_ACK)) {
                throw new Http2Exception(ERROR_PROTOCOL_ERROR,
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
    private void applyRemoteSettings(Http2FrameCodec.Frame frame) throws IOException {
        int[] settings = frame.parseSettings();
        for (int i = 0; i < settings.length; i += 2) {
            int id = settings[i];
            int value = settings[i + 1];

            switch (id) {
                case SETTINGS_HEADER_TABLE_SIZE:
                    remoteHeaderTableSize = value;
                    hpackEncoderLock.lock();
                    try {
                        hpackEncoder.setMaxTableSize(value);
                    } finally {
                        hpackEncoderLock.unlock();
                    }
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
                        throw new Http2Exception(ERROR_FLOW_CONTROL_ERROR,
                                "Invalid INITIAL_WINDOW_SIZE: " + (value & 0xFFFFFFFFL));
                    }
                    // Update window for all active streams
                    int delta = value - remoteInitialWindowSize;
                    remoteInitialWindowSize = value;
                    for (Http2Exchange exchange : activeStreams.values()) {
                        exchange.adjustSendWindow(delta);
                    }
                    break;
                case SETTINGS_MAX_FRAME_SIZE:
                    if (value < MIN_MAX_FRAME_SIZE || value > MAX_MAX_FRAME_SIZE) {
                        throw new Http2Exception(ERROR_PROTOCOL_ERROR,
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
            int nextId = nextStreamId.get();
            if (nextId > goawayLastStreamId) {
                throw new IOException("Connection received GOAWAY with lastStreamId=" +
                        goawayLastStreamId + ", cannot create stream " + nextId);
            }
        }

        // Check stream count first (outside lock for fast-fail)
        if (activeStreamCount.get() >= remoteMaxConcurrentStreams) {
            throw new IOException("Connection at max concurrent streams: " + activeStreamCount.get());
        }

        // Pre-create the exchange outside the lock to minimize critical section.
        Http2Exchange exchange = new Http2Exchange(this, request, readTimeout, writeTimeout);

        // Determine if request has a body
        boolean hasBody = request.body() != null && request.body().contentLength() != 0;
        boolean endStream = !hasBody;

        int streamId = -1;
        CompletableFuture<Void> writeCompletion = new CompletableFuture<>();

        // CRITICAL: Stream ID allocation and HPACK encoding must be atomic.
        // The write queue preserves encode order == write order (required for HPACK).
        // Actual I/O happens on the writer thread, not under the HPACK lock.
        hpackEncoderLock.lock();
        try {
            // Atomic check-and-increment under lock
            int currentCount = activeStreamCount.get();
            if (currentCount >= remoteMaxConcurrentStreams) {
                throw new IOException("Connection at max concurrent streams: " + currentCount);
            }
            activeStreamCount.incrementAndGet();

            // Allocate stream ID (guaranteed to be in encode order)
            streamId = nextStreamId.getAndAdd(2); // Client streams are odd

            // Check for stream ID overflow (max is 2^31 - 1)
            if (streamId < 0) {
                activeStreamCount.decrementAndGet();
                throw new Http2Exception(ERROR_REFUSED_STREAM,
                        "Stream ID space exhausted (exceeded 2^31 - 1)");
            }

            lastStreamId = streamId;
            exchange.setStreamId(streamId);

            // Register BEFORE encoding (response could arrive immediately after write)
            activeStreams.put(streamId, exchange);

            // Update exchange state for the headers we're about to send
            exchange.onHeadersEncoded(endStream);

            // Encode headers under HPACK lock - returns a view of internal buffer
            ByteBuffer encoded = encodeHeadersUnsafe(request);
            // Copy to our own array since buffer is reused
            byte[] headerBlock = new byte[encoded.remaining()];
            encoded.get(headerBlock);

            // Queue the write - writer thread will process in order
            queueWrite(new WriteRequest.Headers(streamId, headerBlock, endStream, writeCompletion));
        } catch (IOException e) {
            // Failed to encode - unregister and rethrow
            if (streamId > 0) {
                activeStreams.remove(streamId);
                activeStreamCount.decrementAndGet();
            }
            throw e;
        } finally {
            hpackEncoderLock.unlock();
        }

        // Wait for the write to complete (outside all locks)
        try {
            writeCompletion.join();
        } catch (Exception e) {
            // Write failed - unregister and rethrow
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
    void unregisterStream(int streamId) {
        if (activeStreams.remove(streamId) != null) {
            activeStreamCount.decrementAndGet();
        }
    }

    /**
     * Queue a DATA frame for writing via the writer thread.
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
        queueWrite(new WriteRequest.Data(streamId, data, offset, length, flags, completion));

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

        // Queue GOAWAY and Shutdown - writer thread will process them
        if (previousState == State.CONNECTED) {
            writeQueue.offer(new WriteRequest.Goaway(lastStreamId, ERROR_NO_ERROR, null));
        }
        writeQueue.offer(new WriteRequest.Shutdown());

        // Wait for writer thread to finish gracefully (sends GOAWAY, drains queue)
        // This gives pending writes a chance to complete before we close the socket.
        if (writerThread != null) {
            try {
                writerThread.join(GRACEFUL_SHUTDOWN_MS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
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
     * Encode headers without lock - caller must hold hpackEncoderLock.
     *
     * <p>Returns a ByteBuffer wrapping the reusable encoding buffer. The returned
     * buffer is only valid until the next call to this method.
     *
     * @throws IOException if header list size exceeds server's SETTINGS_MAX_HEADER_LIST_SIZE
     */
    private ByteBuffer encodeHeadersUnsafe(HttpRequest request) throws IOException {
        headerEncodeBuffer.reset();

        // Track uncompressed header list size per RFC 9113 Section 10.5.1
        // Size = sum of (name.length + value.length + 32) for each field
        long headerListSize = 0;

        String method = request.method();
        boolean isConnect = "CONNECT".equalsIgnoreCase(method);

        // Cache authority and path - avoid recomputing
        String authority = getAuthority(request);
        String scheme = isConnect ? null : request.uri().getScheme();
        String path = isConnect ? null : getPath(request);

        // Encode pseudo-headers first (required order)
        // RFC 9113 Section 8.5: For CONNECT, only :method and :authority are sent
        hpackEncoder.encodeHeader(headerEncodeBuffer, PSEUDO_METHOD, method, false);
        headerListSize += PSEUDO_METHOD.length() + method.length() + 32;

        if (!isConnect) {
            hpackEncoder.encodeHeader(headerEncodeBuffer, PSEUDO_SCHEME, scheme, false);
            headerListSize += PSEUDO_SCHEME.length() + (scheme != null ? scheme.length() : 0) + 32;
        }

        hpackEncoder.encodeHeader(headerEncodeBuffer, PSEUDO_AUTHORITY, authority, false);
        headerListSize += PSEUDO_AUTHORITY.length() + authority.length() + 32;

        if (!isConnect) {
            hpackEncoder.encodeHeader(headerEncodeBuffer, PSEUDO_PATH, path, false);
            headerListSize += PSEUDO_PATH.length() + path.length() + 32;
        }

        // Encode regular headers
        for (var entry : request.headers()) {
            String name = entry.getKey();
            // Skip connection-specific headers
            if (isConnectionHeader(name)) {
                continue;
            }
            boolean isTe = "te".equals(name);
            boolean sensitive = isSensitiveHeader(name);
            for (String value : entry.getValue()) {
                // RFC 9113 Section 8.2.2: TE header, if present, MUST only contain "trailers"
                if (isTe && !"trailers".equalsIgnoreCase(value)) {
                    continue;
                }
                hpackEncoder.encodeHeader(headerEncodeBuffer, name, value, sensitive);
                headerListSize += name.length() + value.length() + 32;
            }
        }

        // Check size limit after encoding (RFC 9113 Section 10.5.1)
        int maxSize = remoteMaxHeaderListSize;
        if (maxSize != Integer.MAX_VALUE && headerListSize > maxSize) {
            throw new IOException("Request header list size (" + headerListSize +
                    " bytes) exceeds server's SETTINGS_MAX_HEADER_LIST_SIZE (" + maxSize + " bytes)");
        }

        return headerEncodeBuffer.toByteBuffer();
    }

    private String getAuthority(HttpRequest request) {
        String host = request.uri().getHost();
        int port = request.uri().getPort();
        String scheme = request.uri().getScheme();
        // Only omit port if it's the default for the scheme
        if (port == -1) {
            return host;
        } else if (port == 443 && "https".equalsIgnoreCase(scheme)) {
            return host;
        } else if (port == 80 && "http".equalsIgnoreCase(scheme)) {
            return host;
        }
        return host + ":" + port;
    }

    private String getPath(HttpRequest request) {
        String path = request.uri().getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = request.uri().getRawQuery();
        if (query != null && !query.isEmpty()) {
            path = path + "?" + query;
        }
        return path;
    }

    private static boolean isConnectionHeader(String name) {
        return CONNECTION_HEADERS.contains(name);
    }

    private static boolean isSensitiveHeader(String name) {
        return SENSITIVE_HEADERS.contains(name);
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
     * @throws Http2Exception if header list size exceeds limit or HPACK decoding fails
     */
    List<HpackDecoder.HeaderField> decodeHeaders(byte[] headerBlock) throws IOException {
        // Check encoded size first (quick rejection)
        int maxHeaderListSize = DEFAULT_MAX_HEADER_LIST_SIZE;
        if (headerBlock.length > maxHeaderListSize) {
            throw new Http2Exception(ERROR_ENHANCE_YOUR_CALM,
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
                throw new Http2Exception(ERROR_COMPRESSION_ERROR, "HPACK decoding failed: " + e.getMessage());
            } catch (IndexOutOfBoundsException e) {
                // Dynamic table index out of range - HPACK state mismatch
                // This is a fatal connection error per RFC 9113 Section 4.3
                active = false;
                LOGGER.debug("HPACK dynamic table mismatch for {}: {}", route, e.getMessage());
                throw new Http2Exception(ERROR_COMPRESSION_ERROR, "HPACK state mismatch: " + e.getMessage());
            }
        }

        // Check decoded size (name + value + 32 bytes overhead per RFC 7541)
        int decodedSize = 0;
        for (HpackDecoder.HeaderField field : headers) {
            decodedSize += field.name().length() + field.value().length() + 32;
            if (decodedSize > maxHeaderListSize) {
                throw new Http2Exception(ERROR_ENHANCE_YOUR_CALM,
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
        queueControlFrame(new WriteRequest.WindowUpdate(streamId, increment));
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
        queueControlFrame(new WriteRequest.Rst(streamId, errorCode, new CompletableFuture<>()));
    }

    /**
     * Consume bytes from the connection receive window.
     *
     * <p>In HTTP/2, data received counts against both the stream window AND
     * the connection window. This method tracks connection-level consumption
     * and queues WINDOW_UPDATE when the window gets low.
     *
     * <p>Called from the reader thread, so uses blocking queue offer to avoid
     * killing the reader on transient queue pressure.
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
            // Queue connection-level WINDOW_UPDATE - use blocking offer since
            // we're on the reader thread and can safely wait for queue space
            queueWrite(new WriteRequest.WindowUpdate(0, increment));
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
            LOGGER.debug("Server sent GOAWAY to {}: {}", route, Http2Constants.errorCodeName(errorCode));
        }

        state = State.SHUTTING_DOWN;

        // RFC 9113 Section 6.8: Signal streams with ID > lastStreamId that they were refused
        // These streams were initiated but not processed by the server
        // Include actual error code for debugging (e.g., PROTOCOL_ERROR, COMPRESSION_ERROR)
        Http2Exception refusedError = new Http2Exception(errorCode,
                "Stream affected by GOAWAY (lastStreamId=" + lastStreamId +
                        ", error=" + Http2Constants.errorCodeName(errorCode) + ")");
        for (var entry : activeStreams.entrySet()) {
            int streamId = entry.getKey();
            if (streamId > lastStreamId) {
                Http2Exchange exchange = entry.getValue();
                exchange.signalConnectionClosed(refusedError);
            }
        }

        // Also fail any queued writes for streams > lastStreamId
        // These haven't been written yet but the server won't accept them
        IOException goawayError = new IOException("Stream refused by GOAWAY (lastStreamId=" + lastStreamId + ")");
        List<WriteRequest> remaining = new ArrayList<>();
        writeQueue.drainTo(remaining);
        for (WriteRequest req : remaining) {
            int streamId = switch (req) {
                case WriteRequest.Headers h -> h.streamId();
                case WriteRequest.Data d -> d.streamId();
                case WriteRequest.Rst r -> r.streamId();
                default -> 0;
            };
            if (streamId > lastStreamId) {
                completeRequest(req, goawayError);
            } else {
                // Re-queue writes for streams that are still valid
                writeQueue.add(req);
            }
        }
    }
}
