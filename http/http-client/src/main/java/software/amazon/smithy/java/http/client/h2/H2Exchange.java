/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static software.amazon.smithy.java.http.client.h2.H2Constants.DEFAULT_INITIAL_WINDOW_SIZE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_CANCEL;
import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_FLOW_CONTROL_ERROR;
import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_PROTOCOL_ERROR;
import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_STREAM_CLOSED;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_END_STREAM;
import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_STATUS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;
import software.amazon.smithy.java.http.client.DelegatedClosingInputStream;
import software.amazon.smithy.java.http.client.DelegatedClosingOutputStream;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.http.client.h2.hpack.HpackDecoder;

/**
 * HTTP/2 exchange implementation for a single stream with multiplexing support.
 *
 * <p>This class manages the lifecycle of a single HTTP/2 stream (request/response pair).
 * Events (headers, data, errors) are received from the connection's reader thread via a
 * single {@link StreamEvent} queue.
 *
 * <h2>Stream Lifecycle</h2>
 * <ol>
 *   <li>Constructor sends HEADERS frame</li>
 *   <li>{@link #requestBody()} returns output stream for DATA frames</li>
 *   <li>{@link #responseHeaders()}/{@link #responseStatusCode()} read response HEADERS</li>
 *   <li>{@link #responseBody()} returns input stream for response DATA frames</li>
 *   <li>{@link #close()} sends RST_STREAM if needed and unregisters stream</li>
 * </ol>
 */
public final class H2Exchange implements HttpExchange {

    /**
     * Stream states per RFC 9113 Section 5.1.
     */
    enum StreamState {
        IDLE, // Initial state
        OPEN, // After sending HEADERS without END_STREAM
        HALF_CLOSED_LOCAL, // We sent END_STREAM, can still receive
        HALF_CLOSED_REMOTE, // They sent END_STREAM, can still send
        CLOSED // Both directions closed
    }

    // Request pseudo-headers (only allowed in requests, not responses)
    private static final Set<String> REQUEST_PSEUDO_HEADERS = Set.of(
            ":method",
            ":scheme",
            ":authority",
            ":path");

    // Shared empty array to avoid allocation
    private static final byte[] EMPTY_DATA = new byte[0];

    private final H2Connection connection;
    private final HttpRequest request;
    private volatile int streamId;

    // Unified event queue - all stream events flow through here
    // Producer: connection's reader thread
    // Consumer: exchange methods (readResponseHeaders, readDataChunk)
    private final BlockingQueue<StreamEvent> eventQueue = new LinkedBlockingQueue<>();

    // Stream state machine per RFC 9113 Section 5.1
    private volatile StreamState streamState = StreamState.IDLE;

    // Stream-level timeouts
    private final long readTimeoutMs;
    private final long writeTimeoutMs;

    // Response state
    private volatile int statusCode = -1;
    private volatile HttpHeaders responseHeaders;
    private volatile boolean responseHeadersReceived = false;
    private volatile boolean endStreamReceived = false;

    // Informational responses (1xx) per RFC 9113 Section 8.1.1
    private final List<InformationalResponse> informationalResponses = new ArrayList<>();

    // Trailer headers per RFC 9113 Section 8.1
    private volatile HttpHeaders trailerHeaders;

    // Content-Length validation per RFC 9113 Section 8.1.1
    private long expectedContentLength = -1; // -1 means not specified
    private long receivedContentLength = 0;

    // Request state
    private volatile boolean endStreamSent = false;
    private volatile OutputStream requestOut;
    private volatile HttpHeaders requestTrailers;

    // Response body input stream
    private volatile InputStream responseIn;

    // Close guard
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Auto-close tracking: exchange closes when both streams are closed (count reaches 2)
    private final AtomicInteger closedStreamCount = new AtomicInteger(0);

    // Flow control with signaling
    // streamSendWindow is protected by flowControlLock (accessed by writer and reader threads)
    // streamRecvWindow is only accessed by application thread in readDataChunk() (single-threaded)
    private final ReentrantLock flowControlLock = new ReentrantLock();
    private final Condition windowAvailable = flowControlLock.newCondition();
    private int streamSendWindow;
    private int streamRecvWindow;

    /**
     * Create a new HTTP/2 exchange without a stream ID.
     *
     * <p>The stream ID will be assigned later via {@link #setStreamId} when
     * the connection allocates it under lock. This allows exchange construction
     * to happen outside the critical section.
     *
     * @param connection the HTTP/2 connection
     * @param request the HTTP request
     * @param readTimeout timeout for waiting on response data
     * @param writeTimeout timeout for waiting on flow control window
     */
    H2Exchange(H2Connection connection, HttpRequest request, Duration readTimeout, Duration writeTimeout) {
        this.connection = connection;
        this.request = request;
        this.streamId = -1; // Will be set later
        this.readTimeoutMs = readTimeout.toMillis();
        this.writeTimeoutMs = writeTimeout.toMillis();
        this.streamSendWindow = connection.getRemoteInitialWindowSize();
        this.streamRecvWindow = DEFAULT_INITIAL_WINDOW_SIZE;
    }

    /**
     * Set the stream ID. Called by connection when allocating under lock.
     */
    void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    /**
     * Called by connection after headers are encoded but before they're written.
     *
     * <p>Updates the exchange state to reflect that headers have been queued for sending.
     * The actual I/O happens outside the HPACK lock for better concurrency.
     *
     * @param endStream true if the HEADERS frame will have END_STREAM flag
     */
    void onHeadersEncoded(boolean endStream) {
        if (endStream) {
            endStreamSent = true;
            streamState = StreamState.HALF_CLOSED_LOCAL;
        } else {
            streamState = StreamState.OPEN;
        }
    }

    /**
     * Called by connection's reader thread to deliver an event.
     *
     * <p>All stream events (headers, data, errors) flow through this single method.
     * The reader thread is the only producer; exchange methods are the only consumers.
     */
    void enqueueEvent(StreamEvent event) {
        eventQueue.add(event);
    }

    /**
     * Called by connection when it's closing.
     *
     * <p>Note: We set endStreamReceived but NOT streamState here. Setting streamState
     * would race with pending events in the queue - handleHeadersEvent checks
     * streamState and would throw a protocol error for legitimate pending events.
     * The error event will be processed after pending events.
     */
    void signalConnectionClosed(Throwable error) {
        this.endStreamReceived = true;
        IOException cause = (error instanceof IOException ioe) ? ioe : new IOException("Connection closed", error);
        eventQueue.add(new StreamEvent.ConnectionError(cause));
    }

    /**
     * Called by reader thread when a per-stream error occurs (e.g., RST_STREAM).
     *
     * <p>This allows readResponseHeaders() and readDataChunk() to fail fast with
     * a meaningful error instead of timing out.
     *
     * <p>Note: We set endStreamReceived but NOT streamState to avoid racing with
     * pending events in the queue.
     */
    void signalStreamError(H2Exception error) {
        this.endStreamReceived = true;
        eventQueue.add(new StreamEvent.StreamError(error));
    }

    /**
     * Called by connection when WINDOW_UPDATE is received.
     */
    void signalWindowUpdate() {
        flowControlLock.lock();
        try {
            windowAvailable.signalAll();
        } finally {
            flowControlLock.unlock();
        }
    }

    /**
     * Called by connection when SETTINGS changes initial window size.
     */
    void adjustSendWindow(int delta) {
        flowControlLock.lock();
        try {
            streamSendWindow += delta;
            if (streamSendWindow > 0) {
                windowAvailable.signalAll();
            }
        } finally {
            flowControlLock.unlock();
        }
    }

    @Override
    public HttpRequest request() {
        return request;
    }

    @Override
    public OutputStream requestBody() {
        if (requestOut == null) {
            // If no request body is expected, then return a no-op stream.
            H2DataOutputStream rawOut = endStreamSent
                    ? new H2DataOutputStream(this, 0)
                    : new H2DataOutputStream(this, connection.getRemoteMaxFrameSize());
            requestOut = new DelegatedClosingOutputStream(rawOut, rw -> {
                rw.close(); // Send END_STREAM
                onRequestStreamClosed();
            });
        }
        return requestOut;
    }

    @Override
    public InputStream responseBody() throws IOException {
        // Ensure we have response headers first
        if (!responseHeadersReceived) {
            readResponseHeaders();
        }

        if (responseIn == null) {
            responseIn = new DelegatedClosingInputStream(new H2DataInputStream(this), this::onResponseStreamClosed);
        }
        return responseIn;
    }

    private void onRequestStreamClosed() throws IOException {
        if (closedStreamCount.incrementAndGet() == 2) {
            close();
        }
    }

    private void onResponseStreamClosed(InputStream _ignored) throws IOException {
        if (closedStreamCount.incrementAndGet() == 2) {
            close();
        }
    }

    @Override
    public HttpHeaders responseHeaders() throws IOException {
        if (!responseHeadersReceived) {
            readResponseHeaders();
        }
        return responseHeaders;
    }

    @Override
    public int responseStatusCode() throws IOException {
        if (!responseHeadersReceived) {
            readResponseHeaders();
        }
        return statusCode;
    }

    @Override
    public HttpVersion responseVersion() {
        return HttpVersion.HTTP_2;
    }

    @Override
    public boolean supportsBidirectionalStreaming() {
        return true;
    }

    @Override
    public void setRequestTrailers(HttpHeaders trailers) {
        this.requestTrailers = trailers;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        // Close request output if not already closed
        if (requestOut != null && !endStreamSent) {
            try {
                requestOut.close();
            } catch (IOException ignored) {}
        }

        // If response not fully received and stream was started, queue RST_STREAM
        if (!endStreamReceived && streamId > 0 && streamState != StreamState.CLOSED) {
            try {
                connection.queueRst(streamId, ERROR_CANCEL);
            } catch (IOException ignored) {
                // Best-effort cleanup. If queue is full, stream is closing anyway.
            }
            // Signal end to any waiting consumers only if stream didn't end naturally
            eventQueue.add(StreamEvent.DataChunk.END);
        }

        // Mark stream as closed
        streamState = StreamState.CLOSED;

        // Unregister from connection (only if stream was registered)
        if (streamId > 0) {
            connection.unregisterStream(streamId);
        }
    }

    /**
     * Poll for the next event from the event queue.
     *
     * <p>This is a simple blocking poll with timeout. Error events are handled
     * inline by callers to avoid double pattern matching on the hot path.
     *
     * @return the next event (may be an error event)
     * @throws SocketTimeoutException if read timeout expires
     * @throws IOException if interrupted
     */
    private StreamEvent pollEvent() throws IOException {
        try {
            StreamEvent event = eventQueue.poll(readTimeoutMs, TimeUnit.MILLISECONDS);
            if (event == null) {
                throw new SocketTimeoutException("Read timed out after " + readTimeoutMs + "ms waiting for response");
            }
            return event;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for stream event", e);
        }
    }

    /**
     * Read and parse response headers from the event queue.
     *
     * <p>Headers are decoded by the connection's reader thread to ensure
     * HPACK dynamic table consistency across all streams.
     */
    private void readResponseHeaders() throws IOException {
        while (!responseHeadersReceived) {
            switch (pollEvent()) {
                case StreamEvent.Headers h -> handleHeadersEvent(h);
                case StreamEvent.DataChunk chunk -> throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        streamId,
                        "Received DATA before response headers");
                case StreamEvent.StreamError se -> throw new IOException(
                        "Stream error before response headers",
                        se.cause());
                case StreamEvent.ConnectionError ce -> throw new IOException(
                        "Connection error before response headers",
                        ce.cause());
            }
        }
    }

    /**
     * Handle a headers event during response reading.
     */
    private void handleHeadersEvent(StreamEvent.Headers event) throws IOException {
        // Validate stream state per RFC 9113 Section 5.1
        if (streamState == StreamState.CLOSED) {
            throw new H2Exception(ERROR_STREAM_CLOSED, streamId, "Received HEADERS on closed stream");
        }

        List<HpackDecoder.HeaderField> fields = event.fields();
        boolean isEndStream = event.endStream();

        if (!responseHeadersReceived) {
            // This is either informational (1xx) or final response headers
            if (fields.isEmpty()) {
                throw new IOException("Empty HEADERS frame received");
            }
            processResponseHeaders(fields, isEndStream);
        } else {
            // We already have final response headers - this must be trailers
            if (!isEndStream) {
                // RFC 9113 Section 8.1: Trailers MUST have END_STREAM.
                throw new H2Exception(ERROR_PROTOCOL_ERROR, streamId, "Trailer HEADERS frame missing END_STREAM");
            }
            if (!fields.isEmpty()) {
                processTrailers(fields);
            }
        }

        if (isEndStream) {
            endStreamReceived = true;
            updateStreamStateOnEndStream();
            validateContentLength();
        }
    }

    /**
     * Update stream state when END_STREAM is received.
     */
    private void updateStreamStateOnEndStream() {
        if (streamState == StreamState.OPEN) {
            streamState = StreamState.HALF_CLOSED_REMOTE;
        } else if (streamState == StreamState.HALF_CLOSED_LOCAL) {
            streamState = StreamState.CLOSED;
        }
    }

    /**
     * Process response headers with full RFC 9113 validation.
     *
     * <p>Headers are already decoded by the reader thread to maintain HPACK state.
     *
     * @param fields the decoded header fields
     * @param isEndStream whether this HEADERS frame has END_STREAM flag
     */
    private void processResponseHeaders(List<HpackDecoder.HeaderField> fields, boolean isEndStream) throws IOException {
        ModifiableHttpHeaders headers = HttpHeaders.ofModifiable();
        int parsedStatusCode = -1;
        boolean seenRegularHeader = false;
        long contentLength = -1;

        for (HpackDecoder.HeaderField field : fields) {
            String name = field.name();
            String value = field.value();

            if (name.startsWith(":")) {
                // Pseudo-header validation per RFC 9113 Section 8.3
                // RFC 9113 Section 8.3: All pseudo-headers MUST appear before regular headers
                if (seenRegularHeader) {
                    throw new H2Exception(ERROR_PROTOCOL_ERROR,
                            streamId,
                            "Pseudo-header '" + name + "' appears after regular header (RFC 9113 Section 8.3)");
                }

                if (name.equals(PSEUDO_STATUS)) {
                    // RFC 9113 Section 8.3.2: Response MUST have exactly one :status
                    if (parsedStatusCode != -1) {
                        throw new H2Exception(ERROR_PROTOCOL_ERROR,
                                streamId,
                                "Multiple :status pseudo-headers in response");
                    }
                    try {
                        parsedStatusCode = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        throw new IOException("Invalid :status value: " + value);
                    }
                } else if (REQUEST_PSEUDO_HEADERS.contains(name)) {
                    // RFC 9113 Section 8.3: Request pseudo-headers are NOT allowed in responses
                    throw new H2Exception(ERROR_PROTOCOL_ERROR,
                            streamId,
                            "Request pseudo-header '" + name + "' not allowed in response (RFC 9113 Section 8.3)");
                } else {
                    // Unknown pseudo-header - RFC 9113 says endpoints MUST treat as malformed
                    throw new H2Exception(ERROR_PROTOCOL_ERROR,
                            streamId,
                            "Unknown pseudo-header '" + name + "' in response");
                }
            } else {
                // Regular header
                seenRegularHeader = true;

                // Track Content-Length for validation per RFC 9113 Section 8.1.1
                if ("content-length".equals(name)) {
                    try {
                        long parsedLength = Long.parseLong(value);
                        if (contentLength != -1 && contentLength != parsedLength) {
                            throw new H2Exception(ERROR_PROTOCOL_ERROR,
                                    streamId,
                                    "Multiple different Content-Length values");
                        }
                        contentLength = parsedLength;
                    } catch (NumberFormatException e) {
                        throw new H2Exception(ERROR_PROTOCOL_ERROR,
                                streamId,
                                "Invalid Content-Length value: " + value);
                    }
                }

                headers.addHeader(name, value);
            }
        }

        if (parsedStatusCode == -1) {
            throw new IOException("Response missing :status pseudo-header");
        }

        // Check if this is an informational (1xx) response
        if (parsedStatusCode >= 100 && parsedStatusCode < 200) {
            // RFC 9113 Section 8.1.1: Informational responses are interim, continue waiting
            // 1xx responses MUST NOT have END_STREAM (except 101 which is not allowed in HTTP/2)
            if (isEndStream) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        streamId,
                        "Informational response (1xx) must not have END_STREAM");
            }
            informationalResponses.add(new InformationalResponse(parsedStatusCode, headers));
            // Don't mark responseHeadersReceived - wait for final response
            return;
        }

        // This is the final response (2xx-5xx)
        this.statusCode = parsedStatusCode;
        this.responseHeaders = headers;
        this.expectedContentLength = contentLength;
        this.responseHeadersReceived = true;
    }

    /**
     * Process trailer headers per RFC 9113 Section 8.1.
     *
     * <p>Trailers are HEADERS sent after DATA with END_STREAM. They MUST NOT
     * contain pseudo-headers.
     *
     * <p>Headers are already decoded by the reader thread to maintain HPACK state.
     *
     * @param fields the pre-decoded header fields
     */
    private void processTrailers(List<HpackDecoder.HeaderField> fields) throws IOException {
        ModifiableHttpHeaders trailers = HttpHeaders.ofModifiable();
        for (HpackDecoder.HeaderField field : fields) {
            String name = field.name();
            // RFC 9113 Section 8.1: Trailers MUST NOT contain pseudo-headers
            if (name.startsWith(":")) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        streamId,
                        "Trailer contains pseudo-header '" + name + "' (RFC 9113 Section 8.1)");
            }
            trailers.addHeader(name, field.value());
        }

        this.trailerHeaders = trailers;
    }

    /**
     * Validate Content-Length matches actual data received.
     * RFC 9113 Section 8.1.1.
     */
    private void validateContentLength() throws IOException {
        if (expectedContentLength >= 0 && receivedContentLength != expectedContentLength) {
            throw new H2Exception(ERROR_PROTOCOL_ERROR,
                    streamId,
                    "Content-Length mismatch: expected " + expectedContentLength +
                            " bytes, received " + receivedContentLength + " bytes (RFC 9113 Section 8.1.1)");
        }
    }

    /**
     * Update stream send window from WINDOW_UPDATE frame.
     *
     * <p>Called by the connection's reader thread when a stream-level
     * WINDOW_UPDATE is received. This is separate from the event queue
     * because WINDOW_UPDATE affects the request send path, not response reading.
     *
     * @param increment the window size increment
     * @throws H2Exception if the increment causes overflow
     */
    void updateStreamSendWindow(int increment) throws H2Exception {
        flowControlLock.lock();
        try {
            streamSendWindow += increment;
            // Check for overflow per RFC 9113 (wrap-around to negative)
            if (streamSendWindow < 0) {
                throw new H2Exception(ERROR_FLOW_CONTROL_ERROR,
                        streamId,
                        "Stream send window overflow: " + streamSendWindow);
            }
            windowAvailable.signalAll();
        } finally {
            flowControlLock.unlock();
        }
    }

    /**
     * Write DATA frame for request body with flow control.
     *
     * @throws SocketTimeoutException if write timeout expires waiting for flow control window
     */
    void writeData(byte[] data, int offset, int length, boolean endStream) throws IOException {
        // If trailers are set and this is the last data, don't set END_STREAM on DATA frame
        // - trailers will carry END_STREAM instead
        boolean hasTrailers = requestTrailers != null;

        while (length > 0) {
            int toSend;

            flowControlLock.lock();
            try {
                // Wait for flow control window - need BOTH stream AND connection windows to be positive
                while (streamSendWindow <= 0 || connection.getConnectionSendWindow() <= 0) {
                    try {
                        if (!windowAvailable.await(writeTimeoutMs, TimeUnit.MILLISECONDS)) {
                            throw new SocketTimeoutException(
                                    "Write timed out after " + writeTimeoutMs + "ms waiting for flow control window");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted waiting for flow control window", e);
                    }
                }

                int available = Math.min(streamSendWindow, connection.getConnectionSendWindow());
                available = Math.min(available, connection.getRemoteMaxFrameSize());
                toSend = Math.min(available, length);

                streamSendWindow -= toSend;
            } finally {
                flowControlLock.unlock();
            }

            boolean isLastChunk = (toSend == length);
            // Only set END_STREAM on DATA if this is the last chunk AND no trailers
            int flags = (endStream && isLastChunk && !hasTrailers) ? FLAG_END_STREAM : 0;

            // Queue the write - writer thread handles I/O with batching
            connection.queueData(streamId, data, offset, toSend, flags);
            connection.consumeConnectionSendWindow(toSend);

            offset += toSend;
            length -= toSend;
        }

        if (endStream) {
            if (hasTrailers) {
                // Send trailers with END_STREAM
                connection.queueTrailers(streamId, requestTrailers);
            }
            endStreamSent = true;
            // Update stream state
            if (streamState == StreamState.OPEN) {
                streamState = StreamState.HALF_CLOSED_LOCAL;
            } else if (streamState == StreamState.HALF_CLOSED_REMOTE) {
                streamState = StreamState.CLOSED;
            }
        }
    }

    /**
     * Send END_STREAM without data, or send trailers if set.
     */
    void sendEndStream() throws IOException {
        if (!endStreamSent) {
            if (requestTrailers != null) {
                connection.queueTrailers(streamId, requestTrailers);
            } else {
                connection.queueData(streamId, EMPTY_DATA, 0, 0, FLAG_END_STREAM);
            }
            endStreamSent = true;
            // Update stream state
            if (streamState == StreamState.OPEN) {
                streamState = StreamState.HALF_CLOSED_LOCAL;
            } else if (streamState == StreamState.HALF_CLOSED_REMOTE) {
                streamState = StreamState.CLOSED;
            }
        }
    }

    /**
     * Read next data chunk from response.
     *
     * <p>Data chunks arrive via the unified event queue from the connection's
     * reader thread. This method also handles trailers (HEADERS with END_STREAM
     * after DATA) and stream-level flow control.
     */
    StreamEvent.DataChunk readDataChunk() throws IOException {
        // If we haven't received headers yet, read them first
        if (!responseHeadersReceived) {
            readResponseHeaders();
        }

        // If we already know stream has ended, return immediately
        if (endStreamReceived) {
            return StreamEvent.DataChunk.END;
        }

        while (true) {
            StreamEvent event = pollEvent();

            switch (event) {
                case StreamEvent.DataChunk chunk -> {
                    // Track received content length for validation
                    if (chunk.data() != null && chunk.length() > 0) {
                        receivedContentLength += chunk.length();

                        // Update stream-level flow control (only if stream is not ending)
                        // Connection-level flow control is handled by the connection
                        if (!chunk.endStream()) {
                            updateStreamRecvWindow(chunk.length());
                        }
                    }

                    if (chunk.endStream()) {
                        endStreamReceived = true;
                        updateStreamStateOnEndStream();
                        validateContentLength();
                    }

                    return chunk;
                }

                case StreamEvent.Headers h -> {
                    // Headers after data = trailers (must have END_STREAM)
                    if (!h.endStream()) {
                        throw new H2Exception(ERROR_PROTOCOL_ERROR,
                                streamId,
                                "Trailer HEADERS frame must have END_STREAM flag");
                    }
                    if (!h.fields().isEmpty()) {
                        processTrailers(h.fields());
                    }
                    endStreamReceived = true;
                    updateStreamStateOnEndStream();
                    validateContentLength();
                    return StreamEvent.DataChunk.END;
                }

                case StreamEvent.StreamError se -> throw new IOException(
                        "Stream error while reading data",
                        se.cause());

                case StreamEvent.ConnectionError ce -> throw new IOException(
                        "Connection error while reading data",
                        ce.cause());
            }
        }
    }

    /**
     * Update stream receive window after consuming data.
     *
     * <p>Sends WINDOW_UPDATE when the window gets below half of the initial size.
     *
     * @throws IOException if the write queue is full
     */
    private void updateStreamRecvWindow(int bytesConsumed) throws IOException {
        streamRecvWindow -= bytesConsumed;
        if (streamRecvWindow < DEFAULT_INITIAL_WINDOW_SIZE / 2) {
            int increment = DEFAULT_INITIAL_WINDOW_SIZE - streamRecvWindow;
            // Queue stream-level WINDOW_UPDATE - writer thread will send it
            connection.queueWindowUpdate(streamId, increment);
            streamRecvWindow += increment;
        }
    }

    /**
     * Get informational (1xx) responses received before the final response.
     *
     * <p>Per RFC 9113 Section 8.1.1, a server may send one or more informational
     * responses (status codes 100-199) before the final response. Common examples
     * include 100 Continue and 103 Early Hints.
     *
     * @return list of informational responses (may be empty, never null)
     */
    public List<InformationalResponse> informationalResponses() {
        return List.copyOf(informationalResponses);
    }

    @Override
    public HttpHeaders responseTrailerHeaders() {
        return trailerHeaders;
    }

    /**
     * Informational response (1xx) received before the final response.
     *
     * @param statusCode the informational status code (100-199)
     * @param headers the headers from this informational response
     */
    public record InformationalResponse(int statusCode, HttpHeaders headers) {}
}
