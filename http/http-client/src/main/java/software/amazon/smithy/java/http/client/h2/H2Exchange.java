/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.api.ModifiableHttpHeaders;
import software.amazon.smithy.java.http.client.DelegatedClosingInputStream;
import software.amazon.smithy.java.http.client.DelegatedClosingOutputStream;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.http.client.h2.hpack.HeaderField;

/**
 * HTTP/2 exchange implementation for a single stream with multiplexing support.
 *
 * <p>This class manages the lifecycle of a single HTTP/2 stream (request/response pair).
 * Response data is received from the connection's reader thread directly into a buffer,
 * avoiding per-frame allocations. Headers and errors are signaled via condition variables.
 *
 * <h2>Stream Lifecycle</h2>
 * <ol>
 *   <li>Constructor sends HEADERS frame</li>
 *   <li>{@link #requestBody()} returns output stream for DATA frames</li>
 *   <li>{@link #responseHeaders()}/{@link #responseStatusCode()} read response HEADERS</li>
 *   <li>{@link #responseBody()} returns input stream for response DATA frames</li>
 *   <li>{@link #close()} sends RST_STREAM if needed and unregisters stream</li>
 * </ol>
 *
 * <h2>Zero-Allocation Read Path</h2>
 * <p>The reader thread writes DATA frame payloads directly into {@code dataBuffer}.
 * The user thread reads directly from this buffer. Flow control ensures the buffer
 * never overflows: we only send WINDOW_UPDATE after user reads consume data.
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

    /**
     * Read-side state machine for response processing.
     */
    enum ReadState {
        WAITING_HEADERS, // Initial state, waiting for response HEADERS
        READING_DATA, // Got headers, now streaming DATA
        DONE, // END_STREAM received
        ERROR // Error occurred
    }

    // Request pseudo-headers (only allowed in requests, not responses)
    private static final Set<String> REQUEST_PSEUDO_HEADERS = Set.of(
            ":method",
            ":scheme",
            ":authority",
            ":path");

    // Shared empty array to avoid allocation
    private static final byte[] EMPTY_DATA = new byte[0];

    private final H2Muxer muxer;
    private final HttpRequest request;
    private volatile int streamId;

    // Pending headers from reader thread (protected by dataLock)
    private List<HeaderField> pendingHeaders;
    private boolean pendingHeadersEndStream;

    // === Data buffer for zero-allocation read path ===
    // Lazily allocated when first DATA frame arrives. Size = initial flow control window.
    // Flow control ensures server can't send more than this before we send WINDOW_UPDATE.
    private byte[] dataBuffer;

    // Buffer positions - protected by dataLock
    // writePos: where reader thread writes next (reader-thread owned, but read by user under lock)
    // readPos: where user reads next (user-thread owned)
    private int writePos = 0;
    private int readPos = 0;

    // Read-side state machine and synchronization
    private final ReentrantLock dataLock = new ReentrantLock();
    private final Condition dataAvailable = dataLock.newCondition();
    private volatile ReadState readState = ReadState.WAITING_HEADERS;
    private volatile IOException readError;

    // Stream state machine per RFC 9113 Section 5.1
    private volatile StreamState streamState = StreamState.IDLE;

    // Stream-level timeouts (tick-based: 1 tick = TIMEOUT_POLL_INTERVAL_MS)
    private final long readTimeoutMs;
    private final long writeTimeoutMs;
    private final int readTimeoutTicks; // Number of ticks before timeout (0 = no timeout)
    private final AtomicLong readSeq = new AtomicLong(); // Activity counter, incremented on read activity
    private volatile int readDeadlineTick; // 0 = no deadline, >0 = deadline tick
    private final AtomicBoolean readTimedOut = new AtomicBoolean(); // At-most-once timeout flag
    private boolean waitingForData; // guarded by dataLock - true when VT is blocked waiting for data

    // Response state
    private volatile int statusCode = -1;
    private volatile HttpHeaders responseHeaders;
    private volatile boolean responseHeadersReceived = false;
    private volatile boolean endStreamReceived = false;

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

    // Flow control
    // sendWindow: Semaphore-based, VT blocks naturally when exhausted (no lock needed)
    // streamRecvWindow: only accessed by application thread in readDataChunk() (single-threaded)
    private final FlowControlWindow sendWindow;
    private final int initialWindowSize;
    private int streamRecvWindow;

    // === OUTBOUND PATH (VT → Writer) ===
    // Pending writes queued by VT, drained by writer thread
    // ConcurrentLinkedQueue is lock-free and safe for concurrent producer/consumer access
    final ConcurrentLinkedQueue<PendingWrite> pendingWrites = new ConcurrentLinkedQueue<>();
    // Flag to prevent duplicate additions to connection's work queue
    volatile boolean inWorkQueue;

    /**
     * Create a new HTTP/2 exchange without a stream ID.
     *
     * <p>The stream ID will be assigned later via {@link #setStreamId} when
     * the muxer allocates it. This allows exchange construction to happen
     * outside the critical section.
     *
     * @param muxer the muxer managing this stream
     * @param request the HTTP request
     * @param readTimeoutMs timeout in milliseconds for waiting on response data
     * @param writeTimeoutMs timeout in milliseconds for waiting on flow control window
     * @param initialWindowSize initial flow control window size for this stream
     */
    H2Exchange(H2Muxer muxer, HttpRequest request, long readTimeoutMs, long writeTimeoutMs, int initialWindowSize) {
        this.muxer = muxer;
        this.request = request;
        this.streamId = -1; // Will be set later
        this.readTimeoutMs = readTimeoutMs;
        this.writeTimeoutMs = writeTimeoutMs;
        // Convert timeout to ticks: ceil(readTimeoutMs / pollIntervalMs)
        this.readTimeoutTicks = readTimeoutMs <= 0
                ? 0
                : Math.max(1, (int) Math.ceil((double) readTimeoutMs / H2Muxer.TIMEOUT_POLL_INTERVAL_MS));
        this.sendWindow = new FlowControlWindow(muxer.getRemoteInitialWindowSize());
        this.initialWindowSize = initialWindowSize;
        this.streamRecvWindow = initialWindowSize;
    }

    /**
     * Set the stream ID. Called by connection when allocating under lock.
     */
    void setStreamId(int streamId) {
        this.streamId = streamId;
    }

    /**
     * Get the stream ID.
     */
    int getStreamId() {
        return streamId;
    }

    /**
     * Get read timeout in milliseconds.
     */
    long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    /**
     * Get read deadline tick (0 = no deadline).
     */
    int getReadDeadlineTick() {
        return readDeadlineTick;
    }

    /**
     * Get read activity sequence number.
     */
    long getReadSeq() {
        return readSeq.get();
    }

    /**
     * Attempt to mark this exchange as timed out. Returns true if successful (first caller wins).
     * Used by timeout sweep to ensure at-most-once timeout per exchange.
     */
    boolean markReadTimedOut() {
        return readTimedOut.compareAndSet(false, true);
    }

    /**
     * Record read activity: bump sequence and reset deadline.
     * Called when headers or data arrive.
     *
     * <p>Uses tick-based timeout: instead of calling System.nanoTime() (expensive),
     * we read the current tick from the muxer (cheap volatile read) and compute
     * the deadline as currentTick + timeoutTicks.
     */
    private void onReadActivity() {
        if (readTimeoutTicks > 0) {
            readSeq.incrementAndGet();
            readDeadlineTick = muxer.currentTimeoutTick() + readTimeoutTicks;
        }
    }

    /**
     * Clear read deadline (no timeout).
     */
    private void clearReadDeadline() {
        readDeadlineTick = 0;
    }

    /**
     * Get the muxer for this exchange.
     */
    H2Muxer getMuxer() {
        return muxer;
    }

    /**
     * Borrow a buffer from the muxer's pool.
     *
     * @param minSize minimum size needed
     * @return a buffer of at least minSize bytes
     */
    byte[] borrowBuffer(int minSize) {
        return muxer.borrowBuffer(minSize);
    }

    /**
     * Return a buffer to the muxer's pool.
     *
     * <p>Called by the writer thread after consuming a PendingWrite.
     *
     * @param buffer the buffer to return
     */
    void returnBuffer(byte[] buffer) {
        muxer.returnBuffer(buffer);
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
     * Called by connection's reader thread to deliver response headers.
     *
     * <p>Headers are decoded by the reader thread to ensure HPACK state consistency.
     * This method signals the user thread that headers are available.
     *
     * @param fields the decoded header fields
     * @param endStream whether END_STREAM flag was set
     */
    void deliverHeaders(List<HeaderField> fields, boolean endStream) {
        dataLock.lock();
        try {
            pendingHeaders = fields;
            pendingHeadersEndStream = endStream;
            if (waitingForData) {
                dataAvailable.signalAll();
            }
        } finally {
            dataLock.unlock();
        }
    }

    /**
     * Called by connection when it's closing.
     *
     * <p>Signals the user thread that the connection has closed with an error.
     */
    void signalConnectionClosed(Throwable error) {
        dataLock.lock();
        try {
            this.endStreamReceived = true;
            this.readError = (error instanceof IOException ioe) ? ioe : new IOException("Connection closed", error);
            this.readState = ReadState.ERROR;
            dataAvailable.signalAll();
        } finally {
            dataLock.unlock();
        }
    }

    /**
     * Called by reader thread when a per-stream error occurs (e.g., RST_STREAM).
     *
     * <p>This allows read operations to fail fast with a meaningful error
     * instead of timing out.
     */
    void signalStreamError(H2Exception error) {
        dataLock.lock();
        try {
            this.endStreamReceived = true;
            this.readError = new IOException("Stream error", error);
            this.readState = ReadState.ERROR;
            dataAvailable.signalAll();
        } finally {
            dataLock.unlock();
        }
    }

    /**
     * Get the data buffer for direct writes by the reader thread.
     *
     * <p>Must be called while holding dataLock (via ensureBufferSpace).
     */
    byte[] getDataBuffer() {
        return dataBuffer;
    }

    /**
     * Get the current write position for direct writes by the reader thread.
     *
     * <p>Must be called while holding dataLock (via ensureBufferSpace).
     */
    int getWritePos() {
        return writePos;
    }

    /**
     * Called by reader thread after writing data to advance the write position.
     *
     * <p>Note: We do NOT call updateStreamStateOnEndStream() here. The stream
     * state transition happens when the user finishes reading (returns -1),
     * not when data arrives. This avoids race conditions where the stream
     * transitions to CLOSED before the user processes pending headers.
     *
     * @param bytesWritten number of bytes written
     * @param endStream whether END_STREAM flag was set
     */
    void commitWrite(int bytesWritten, boolean endStream) {
        dataLock.lock();
        try {
            boolean wasEmpty = (writePos == readPos);

            writePos += bytesWritten;
            if (bytesWritten > 0) {
                onReadActivity(); // Extend timeout when data arrives
            }
            if (endStream) {
                this.endStreamReceived = true;
                this.readState = ReadState.DONE;
                clearReadDeadline(); // No more data expected, clear timeout
            }

            // Only wake the reader if it's actually blocked waiting
            if ((wasEmpty || endStream) && waitingForData) {
                dataAvailable.signalAll();
            }
        } finally {
            dataLock.unlock();
        }
    }

    // Initial buffer size - small to avoid waste on tiny responses
    private static final int INITIAL_BUFFER_SIZE = 1024;

    /**
     * Called by reader thread to ensure buffer has space, allocating or compacting as needed.
     *
     * <p>Buffer is borrowed from the connection's pool when first needed, and returned
     * when the exchange is closed. This reduces GC pressure for repeated requests.
     *
     * @param requiredSpace the amount of space needed
     * @return true if space is available, false if buffer is genuinely full
     */
    boolean ensureBufferSpace(int requiredSpace) {
        dataLock.lock();
        try {
            // Lazy allocation on first DATA frame - borrow from connection pool
            if (dataBuffer == null) {
                int size = Math.max(INITIAL_BUFFER_SIZE, requiredSpace);
                size = Math.min(size, initialWindowSize);
                dataBuffer = muxer.borrowBuffer(size);
                return true;
            }

            if (writePos + requiredSpace <= dataBuffer.length) {
                // Already have space
                return true;
            }

            // Try compaction first
            if (readPos > 0) {
                int remaining = writePos - readPos;
                if (remaining > 0) {
                    System.arraycopy(dataBuffer, readPos, dataBuffer, 0, remaining);
                }
                writePos = remaining;
                readPos = 0;

                if (writePos + requiredSpace <= dataBuffer.length) {
                    return true;
                }
            }

            // Need to grow buffer - get a larger one from pool. Grow by 4x to reduce resizing frequency.
            int newSize = dataBuffer.length * 4;
            while (newSize < writePos + requiredSpace) {
                newSize *= 2;
            }
            newSize = Math.min(newSize, initialWindowSize);

            if (writePos + requiredSpace > newSize) {
                return false;
            }

            byte[] oldBuffer = dataBuffer;
            dataBuffer = muxer.borrowBuffer(newSize);
            if (writePos > 0) {
                System.arraycopy(oldBuffer, 0, dataBuffer, 0, writePos);
            }
            // Return old buffer to pool
            muxer.returnBuffer(oldBuffer);
            return true;
        } finally {
            dataLock.unlock();
        }
    }

    /**
     * Called by connection when SETTINGS changes initial window size.
     */
    void adjustSendWindow(int delta) {
        sendWindow.adjust(delta);
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
                    : new H2DataOutputStream(this, muxer.getRemoteMaxFrameSize());
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
                // Short timeout for cleanup
                muxer.queueControlFrame(streamId, H2Muxer.ControlFrameType.RST_STREAM, ERROR_CANCEL, 100);
            } catch (IOException ignored) {
                // Best-effort cleanup. If queue is full, stream is closing anyway.
            }
            // Signal end to any waiting consumers
            dataLock.lock();
            try {
                readState = ReadState.DONE;
                dataAvailable.signalAll();
            } finally {
                dataLock.unlock();
            }
        }

        // Return buffer to connection pool for reuse
        dataLock.lock();
        try {
            if (dataBuffer != null) {
                muxer.returnBuffer(dataBuffer);
                dataBuffer = null;
            }
        } finally {
            dataLock.unlock();
        }

        // Mark stream as closed
        streamState = StreamState.CLOSED;

        // Unregister from connection (only if stream was registered)
        if (streamId > 0) {
            muxer.releaseStream(streamId);
        }
    }

    /**
     * Wait for the next event from the reader thread.
     *
     * <p>Used for waiting on headers and errors. Data is read directly from
     * the buffer, not via this method.
     *
     * @throws SocketTimeoutException if read timeout expires
     * @throws IOException if interrupted or error occurred
     */
    private void awaitEvent() throws IOException {
        dataLock.lock();
        try {
            // Wait for headers, error, or data (which also signals)
            while (pendingHeaders == null && readState != ReadState.ERROR && readState != ReadState.DONE) {
                waitingForData = true;
                try {
                    dataAvailable.await(); // Untimed: muxer watchdog handles timeout
                } finally {
                    waitingForData = false;
                }
            }

            // Check for error
            if (readState == ReadState.ERROR) {
                throw readError;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted waiting for response", e);
        } finally {
            dataLock.unlock();
        }
    }

    /**
     * Read and parse response headers.
     *
     * <p>Headers are decoded by the connection's reader thread to ensure
     * HPACK dynamic table consistency across all streams.
     */
    private void readResponseHeaders() throws IOException {
        onReadActivity(); // Start timeout when beginning to read response

        while (!responseHeadersReceived) {
            awaitEvent();

            dataLock.lock();
            try {
                if (pendingHeaders != null) {
                    List<HeaderField> fields = pendingHeaders;
                    boolean endStream = pendingHeadersEndStream;
                    pendingHeaders = null; // Consume the headers

                    // Process headers (can throw)
                    handleHeadersEvent(fields, endStream);
                } else if (readState == ReadState.DONE) {
                    throw new IOException("Stream ended before response headers received");
                }
            } finally {
                dataLock.unlock();
            }
        }
    }

    /**
     * Handle a headers event during response reading.
     *
     * @param fields the decoded header fields
     * @param isEndStream whether END_STREAM flag was set
     */
    private void handleHeadersEvent(List<HeaderField> fields, boolean isEndStream) throws IOException {
        // Validate stream state per RFC 9113 Section 5.1
        if (streamState == StreamState.CLOSED) {
            throw new H2Exception(ERROR_STREAM_CLOSED, streamId, "Received HEADERS on closed stream");
        }

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
            readState = ReadState.DONE;
            clearReadDeadline(); // No more data expected
            updateStreamStateOnEndStream();
            validateContentLength();
        } else if (responseHeadersReceived && readState != ReadState.DONE) {
            // Got final headers, transition to READING_DATA
            // (but don't overwrite DONE if DATA+END_STREAM already arrived)
            readState = ReadState.READING_DATA;
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
    private void processResponseHeaders(List<HeaderField> fields, boolean isEndStream) throws IOException {
        ModifiableHttpHeaders headers = HttpHeaders.ofModifiable();
        int parsedStatusCode = -1;
        boolean seenRegularHeader = false;
        long contentLength = -1;

        for (HeaderField field : fields) {
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

        // Check if this is an informational (1xx) response - skip and wait for final response
        if (parsedStatusCode >= 100 && parsedStatusCode < 200) {
            // RFC 9113 Section 8.1.1: 1xx responses MUST NOT have END_STREAM
            if (isEndStream) {
                throw new H2Exception(ERROR_PROTOCOL_ERROR,
                        streamId,
                        "Informational response (1xx) must not have END_STREAM");
            }
            // Don't store 1xx responses - just wait for final response
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
    private void processTrailers(List<HeaderField> fields) throws IOException {
        ModifiableHttpHeaders trailers = HttpHeaders.ofModifiable();
        for (HeaderField field : fields) {
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
     * WINDOW_UPDATE is received. This releases permits to the FlowControlWindow,
     * which will automatically wake any blocked threads.
     *
     * @param increment the window size increment
     * @throws H2Exception if the increment causes overflow
     */
    void updateStreamSendWindow(int increment) throws H2Exception {
        // Check for overflow per RFC 9113 before releasing
        // Note: with Semaphore, we can't easily detect overflow, so we check available + increment
        int currentWindow = sendWindow.available();
        if ((long) currentWindow + increment > Integer.MAX_VALUE) {
            throw new H2Exception(ERROR_FLOW_CONTROL_ERROR,
                    streamId,
                    "Stream send window overflow");
        }
        sendWindow.release(increment);
    }

    /**
     * Write DATA frame for request body with flow control.
     *
     * <p>Uses the SPSC (single-producer, single-consumer) pattern:
     * <ol>
     *   <li>VT acquires flow control (blocks naturally via Semaphore)</li>
     *   <li>VT copies data to pooled buffer and adds to pendingWrites queue</li>
     *   <li>VT signals writer thread via dataWorkQueue</li>
     *   <li>Writer thread drains pendingWrites and writes frames</li>
     * </ol>
     *
     * <p>This eliminates contention on the shared ArrayBlockingQueue by using
     * per-exchange queues for data.
     *
     * @throws SocketTimeoutException if write timeout expires waiting for flow control window
     */
    void writeData(byte[] data, int offset, int length, boolean endStream) throws IOException {
        // If trailers are set and this is the last data, don't set END_STREAM on DATA frame
        // - trailers will carry END_STREAM instead
        boolean hasTrailers = requestTrailers != null;

        while (length > 0) {
            // Determine how much we can send based on frame size limit
            int maxFrameSize = muxer.getRemoteMaxFrameSize();
            int toSend = Math.min(length, maxFrameSize);

            // Acquire stream-level flow control (blocks naturally via Semaphore)
            try {
                if (!sendWindow.tryAcquire(toSend, writeTimeoutMs, TimeUnit.MILLISECONDS)) {
                    throw new SocketTimeoutException(
                            "Write timed out after " + writeTimeoutMs + "ms waiting for stream flow control window");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for stream flow control window", e);
            }

            // Acquire connection-level flow control
            try {
                muxer.acquireConnectionWindow(toSend, writeTimeoutMs);
            } catch (InterruptedException e) {
                // Release stream permits we acquired
                sendWindow.release(toSend);
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for connection flow control window", e);
            } catch (SocketTimeoutException e) {
                // Release stream permits we acquired
                sendWindow.release(toSend);
                throw e;
            }

            boolean isLastChunk = (toSend == length);
            // Only set END_STREAM on DATA if this is the last chunk AND no trailers
            int flags = (endStream && isLastChunk && !hasTrailers) ? FLAG_END_STREAM : 0;

            // Copy data to pooled buffer (caller may reuse their buffer)
            byte[] buf = muxer.borrowBuffer(toSend);
            System.arraycopy(data, offset, buf, 0, toSend);

            // Add to pendingWrites queue (lock-free concurrent queue)
            PendingWrite pw = new PendingWrite();
            pw.init(buf, 0, toSend, isLastChunk && endStream && !hasTrailers, flags);
            pendingWrites.add(pw);

            // Signal writer thread if not already in work queue
            if (!inWorkQueue) {
                inWorkQueue = true;
                muxer.signalDataReady(this);
            }

            offset += toSend;
            length -= toSend;
        }

        if (endStream) {
            if (hasTrailers) {
                // Send trailers with END_STREAM
                muxer.queueTrailers(streamId, requestTrailers);
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
                muxer.queueTrailers(streamId, requestTrailers);
            } else {
                muxer.queueData(streamId, EMPTY_DATA, 0, 0, FLAG_END_STREAM);
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
     * Read data from the response buffer.
     *
     * <p>This is the primary read method for H2DataInputStream. Data is read
     * directly from the exchange's buffer, which is filled by the reader thread.
     *
     * @param buf the destination buffer
     * @param off offset in the destination buffer
     * @param len maximum number of bytes to read
     * @return number of bytes read, or -1 if end of stream
     * @throws IOException if an error occurs
     */
    int readFromBuffer(byte[] buf, int off, int len) throws IOException {
        // If we haven't received headers yet, read them first
        if (!responseHeadersReceived) {
            readResponseHeaders();
        }

        int bytesRead;
        dataLock.lock();
        try {
            // Wait for data, EOF, or error
            while (readPos == writePos && readState == ReadState.READING_DATA) {
                // Check for pending trailers
                if (pendingHeaders != null) {
                    List<HeaderField> fields = pendingHeaders;
                    boolean endStream = pendingHeadersEndStream;
                    pendingHeaders = null;
                    handleHeadersEvent(fields, endStream);
                    if (readState == ReadState.DONE) {
                        break;
                    }
                }

                // Wait for data to arrive
                waitingForData = true;
                try {
                    dataAvailable.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for data", e);
                } finally {
                    waitingForData = false;
                }
            }

            // Check for error
            if (readState == ReadState.ERROR) {
                throw readError;
            }

            // Check for EOF (no more data and stream is done)
            if (readPos == writePos && readState == ReadState.DONE) {
                updateStreamStateOnEndStream();
                validateContentLength();
                return -1;
            }

            // Copy from buffer to user's array
            int available = writePos - readPos;
            int toCopy = Math.min(available, len);
            System.arraycopy(dataBuffer, readPos, buf, off, toCopy);
            readPos += toCopy;
            bytesRead = toCopy;

            // Track received content length for validation
            receivedContentLength += toCopy;

        } finally {
            dataLock.unlock();
        }

        // Update stream-level flow control outside the lock
        // This sends WINDOW_UPDATE after user reads consume data
        if (bytesRead > 0 && readState != ReadState.DONE) {
            updateStreamRecvWindow(bytesRead);
        }

        return bytesRead;
    }

    /**
     * Get available bytes in buffer without blocking.
     *
     * @return number of bytes available for immediate read
     */
    int availableInBuffer() {
        dataLock.lock();
        try {
            return dataBuffer == null ? 0 : writePos - readPos;
        } finally {
            dataLock.unlock();
        }
    }

    /**
     * Update stream receive window after consuming data.
     *
     * <p>Sends WINDOW_UPDATE when the window drops below the threshold defined by
     * {@link H2Constants#WINDOW_UPDATE_THRESHOLD_DIVISOR}.
     *
     * @throws IOException if the write queue is full
     */
    private void updateStreamRecvWindow(int bytesConsumed) throws IOException {
        streamRecvWindow -= bytesConsumed;
        if (streamRecvWindow < initialWindowSize / H2Constants.WINDOW_UPDATE_THRESHOLD_DIVISOR) {
            int increment = initialWindowSize - streamRecvWindow;
            // Queue stream-level WINDOW_UPDATE - writer thread will send it
            muxer.queueControlFrame(streamId, H2Muxer.ControlFrameType.WINDOW_UPDATE, increment, writeTimeoutMs);
            streamRecvWindow += increment;
        }
    }

    @Override
    public HttpHeaders responseTrailerHeaders() {
        return trailerHeaders;
    }
}
