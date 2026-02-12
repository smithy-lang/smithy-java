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
import static software.amazon.smithy.java.http.client.h2.H2StreamState.RS_DONE;
import static software.amazon.smithy.java.http.client.h2.H2StreamState.RS_ERROR;
import static software.amazon.smithy.java.http.client.h2.H2StreamState.RS_READING;
import static software.amazon.smithy.java.http.client.h2.H2StreamState.SS_CLOSED;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.DelegatedClosingInputStream;
import software.amazon.smithy.java.http.client.DelegatedClosingOutputStream;
import software.amazon.smithy.java.http.client.HttpExchange;
import software.amazon.smithy.java.http.client.h2.hpack.HeaderField;

/**
 * HTTP/2 exchange implementation for a single stream with multiplexing support.
 *
 * <p>This class manages the lifecycle of a single HTTP/2 stream (request/response pair).
 * Response data is received from the connection's reader thread into a queue of data chunks.
 * Headers and errors are signaled via condition variables.
 *
 * <h2>Stream Lifecycle</h2>
 * <ol>
 *   <li>Exchange created via {@link H2Muxer#newExchange}, HEADERS sent via {@link H2Muxer#submitHeaders}</li>
 *   <li>{@link #requestBody()} returns output stream for DATA frames</li>
 *   <li>{@link #responseHeaders()}/{@link #responseStatusCode()} read response HEADERS</li>
 *   <li>{@link #responseBody()} returns input stream for response DATA frames</li>
 *   <li>{@link #close()} sends RST_STREAM if needed and unregisters stream</li>
 * </ol>
 *
 * <h2>Data Flow</h2>
 * <p>The reader thread enqueues DATA frame payloads via {@link #enqueueData}. The user
 * thread drains chunks in batches via {@link #drainChunks} (used by H2DataInputStream).
 * Pooled byte[] buffers are returned after consumption. Flow control sends WINDOW_UPDATE
 * after data is consumed via {@link #onDataConsumed}.
 */
public final class H2Exchange implements HttpExchange {

    // Max frames to acquire flow control for in a single batch (64 frames = 1MB at default 16KB frame size)
    private static final int FLOW_CONTROL_BATCH_FRAMES = 64;

    // VarHandle for atomic inWorkQueue CAS
    private static final VarHandle IN_WORK_QUEUE_HANDLE;

    static {
        try {
            IN_WORK_QUEUE_HANDLE = MethodHandles.lookup()
                    .findVarHandle(H2Exchange.class, "inWorkQueue", boolean.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final H2Muxer muxer;
    private final HttpRequest request;
    private volatile int streamId;

    // Stream state machine (encapsulates packed bit-field with CAS operations)
    private final H2StreamState state = new H2StreamState();

    // Pending headers from reader thread (protected by dataLock)
    private List<HeaderField> pendingHeaders;
    private boolean pendingHeadersEndStream;

    // === Data chunk queue ===
    // Queue of DataChunks received from reader thread. Each chunk contains one DATA frame payload.
    // Flow control ensures total queued data never exceeds initial window size.
    private final ArrayDeque<DataChunk> dataQueue = new ArrayDeque<>();

    // Read-side synchronization (state is in packedState)
    private final ReentrantLock dataLock = new ReentrantLock();
    private volatile IOException readError;

    // Lock-free signaling: waiting thread parks itself, producer unparks it without holding lock.
    // This allows stream-switch signaling without double lock acquisition.
    private volatile Thread waitingThread;

    // Stream-level timeouts (tick-based: 1 tick = TIMEOUT_POLL_INTERVAL_MS)
    private final long readTimeoutMs;
    private final long writeTimeoutMs;
    private final int readTimeoutTicks; // Number of ticks before timeout (0 = no timeout)
    private final AtomicLong readSeq = new AtomicLong(); // Activity counter, incremented on read activity
    private volatile int readDeadlineTick; // 0 = no deadline, >0 = deadline tick
    private final AtomicBoolean readTimedOut = new AtomicBoolean(); // At-most-once timeout flag

    // Response headers (status code is in packedState)
    private volatile HttpHeaders responseHeaders;

    // Trailer headers per RFC 9113 Section 8.1
    private volatile HttpHeaders trailerHeaders;

    // Content-Length validation per RFC 9113 Section 8.1.1
    private long expectedContentLength = -1; // -1 means not specified
    private long receivedContentLength = 0;

    // Request state (endStreamSent is in packedState)
    private volatile OutputStream requestOut;
    private volatile HttpHeaders requestTrailers;

    // Response body input stream
    private volatile InputStream responseIn;

    // Close guard
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // Auto-close tracking: exchange closes when both request and response streams are closed
    private static final int BOTH_STREAMS_CLOSED = 2; // request stream + response stream
    private final AtomicInteger closedStreamCount = new AtomicInteger(0);

    // === Flow control ===
    // sendWindow: monitor-based (synchronized + wait/notifyAll), VT blocks when exhausted
    // streamRecvWindow: tracks receive window, accessed under dataLock
    private final FlowControlWindow sendWindow;
    private final int initialWindowSize;
    private int streamRecvWindow;

    // === OUTBOUND PATH (VT → Writer) ===
    // Pending writes queued by VT, drained by writer thread
    // ConcurrentLinkedQueue is lock-free and safe for concurrent producer/consumer access
    final ConcurrentLinkedQueue<PendingWrite> pendingWrites = new ConcurrentLinkedQueue<>();
    // Flag to prevent duplicate additions to connection's work queue
    volatile boolean inWorkQueue;

    // === WRITE COMPLETION SIGNALING ===
    // Lock-free signaling using LockSupport to avoid monitor inflation overhead
    private volatile Thread waitingWriter;
    private volatile boolean writeCompleted;
    private volatile Throwable writeError;

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
     * Set the stream ID. Called by muxer when allocating stream ID.
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
     * Called when headers are encoded and about to be sent.
     * Atomically transitions stream state and optionally marks end stream sent.
     */
    void onHeadersEncoded(boolean endStream) {
        state.onHeadersEncoded(endStream);
    }

    // ==================== WRITE COMPLETION SIGNALING ====================

    /**
     * Block until signaled by the writer thread, then check for errors.
     *
     * <p>Called by the VT that owns this exchange after submitting work to the muxer.
     * Uses lock-free LockSupport signaling to avoid monitor inflation overhead.
     *
     * @throws IOException if a write error occurred
     */
    void awaitWriteCompletion() throws IOException {
        // Fast path: already completed
        if (writeCompleted) {
            writeCompleted = false;
            checkWriteError();
            return;
        }

        // Register as waiting and park until signaled
        waitingWriter = Thread.currentThread();
        try {
            while (!writeCompleted) {
                LockSupport.park();
                if (Thread.interrupted()) {
                    throw new IOException("Interrupted waiting for write completion");
                }
            }
        } finally {
            waitingWriter = null;
        }

        writeCompleted = false;
        checkWriteError();
    }

    private void checkWriteError() throws IOException {
        Throwable error = writeError;
        if (error != null) {
            writeError = null;
            if (error instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException("Write failed", error);
        }
    }

    /**
     * Signal the waiting writer that the write completed successfully.
     *
     * <p>Called by the muxer worker thread after completing a write.
     * Lock-free: uses volatile write + LockSupport.unpark().
     */
    void signalWriteSuccess() {
        writeCompleted = true;
        Thread t = waitingWriter;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    /**
     * Signal the waiting writer that the write failed.
     *
     * <p>Called by the muxer worker thread when a write error occurs.
     * Lock-free: uses volatile writes + LockSupport.unpark().
     *
     * @param error the error that occurred
     */
    void signalWriteFailure(Throwable error) {
        writeError = error;
        writeCompleted = true;
        Thread t = waitingWriter;
        if (t != null) {
            LockSupport.unpark(t);
        }
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
        } finally {
            dataLock.unlock();
        }
        // Signal outside lock - lock-free wakeup
        Thread t = waitingThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    /**
     * Called by connection when it's closing.
     *
     * <p>Signals the user thread that the connection has closed with an error.
     */
    void signalConnectionClosed(Throwable error) {
        // Set error state without updating stream state (unlike normal end-stream)
        state.setErrorState();
        this.readError = (error instanceof IOException ioe) ? ioe : new IOException("Connection closed", error);
        // Signal outside lock - lock-free wakeup
        Thread t = waitingThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    /**
     * Called by reader thread when a per-stream error occurs (e.g., RST_STREAM).
     *
     * <p>This allows read operations to fail fast with a meaningful error
     * instead of timing out.
     */
    void signalStreamError(H2Exception error) {
        // Set error state without updating stream state (unlike normal end-stream)
        state.setErrorState();
        this.readError = new IOException("Stream error", error);
        // Signal outside lock - lock-free wakeup
        Thread t = waitingThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    /**
     * Enqueue a data chunk from the reader thread.
     *
     * <p>This method is called by the reader thread to add a byte[] containing
     * DATA frame payload to the queue.
     *
     * @param data the byte array containing data, or null for end-stream-only signal
     * @param length the number of valid bytes in data
     * @param endStream whether END_STREAM flag was set
     * @param moreDataBuffered true if more data is already buffered in the socket read buffer,
     *                         used to defer signaling when processing a burst of frames
     */
    void enqueueData(byte[] data, int length, boolean endStream, boolean moreDataBuffered) {
        boolean sendWindowUpdate = false;
        int windowIncrement = 0;

        dataLock.lock();
        try {
            if (data != null && length > 0) {
                dataQueue.add(new DataChunk(data, length, endStream));

                // Update stream receive window immediately when data arrives.
                // This allows the server to keep sending without waiting for consumer to read.
                // Buffering is bounded by initialWindowSize - server cannot send more than
                // the window allows, so a slow consumer can buffer at most initialWindowSize bytes.
                streamRecvWindow -= length;
                if (streamRecvWindow < initialWindowSize / H2Constants.WINDOW_UPDATE_THRESHOLD_DIVISOR) {
                    windowIncrement = initialWindowSize - streamRecvWindow;
                    streamRecvWindow += windowIncrement;
                    sendWindowUpdate = true;
                }
            } else if (data != null) {
                // Empty buffer - return to pool immediately
                muxer.returnBuffer(data);
            }

            if (endStream) {
                state.setEndStreamReceivedFlag(); // Just set flag + readState, don't update stream state
                clearReadDeadline(); // No more data expected, clear timeout
            }
        } finally {
            dataLock.unlock();
        }

        // Send WINDOW_UPDATE outside lock to avoid blocking reader thread
        if (sendWindowUpdate) {
            muxer.queueControlFrame(streamId, H2Muxer.ControlFrameType.WINDOW_UPDATE, windowIncrement, writeTimeoutMs);
        }

        // Signal consumer only when necessary to reduce wakeup overhead:
        // - endStream: response complete, consumer must finish
        // - !moreDataBuffered: no more data in socket buffer, signal now before reader blocks
        // When moreDataBuffered=true, defer signaling - H2Connection will call signalDataAvailable()
        // when switching streams or when buffer empties.
        if (endStream || !moreDataBuffered) {
            Thread t = waitingThread;
            if (t != null) {
                LockSupport.unpark(t);
            }
        }
    }

    /**
     * Signal the consumer that data is available.
     *
     * <p>Called by H2Connection only when switching from this stream to a different
     * stream (to flush pending data before processing another stream's frames).
     * This is lock-free and can be called without holding any locks.
     */
    void signalDataAvailable() {
        Thread t = waitingThread;
        if (t != null) {
            LockSupport.unpark(t);
        }
    }

    /**
     * Drain multiple data chunks from the queue into a destination deque.
     *
     * <p>This method is used by H2DataInputStream for batch dequeuing to reduce
     * lock contention. Instead of acquiring the lock once per chunk, the consumer
     * can pull multiple chunks in a single lock acquisition.
     *
     * @param dest the destination array to drain chunks into
     * @param maxChunks maximum number of chunks to drain
     * @return number of chunks drained, or -1 if EOF
     * @throws IOException if an error occurs or the stream is in error state
     */
    int drainChunks(DataChunk[] dest, int maxChunks) throws IOException {
        // If we haven't received headers yet, read them first
        if (!state.isResponseHeadersReceived()) {
            readResponseHeaders();
        }

        dataLock.lock();
        try {
            // Wait for data, EOF, or error
            while (dataQueue.isEmpty() && state.getReadState() == RS_READING) {
                // Check for pending trailers
                if (pendingHeaders != null) {
                    List<HeaderField> fields = pendingHeaders;
                    boolean endStream = pendingHeadersEndStream;
                    pendingHeaders = null;
                    handleHeadersEvent(fields, endStream);
                    if (state.getReadState() == RS_DONE) {
                        break;
                    }
                }

                // Wait for data to arrive using lock-free signaling
                // 1. Register ourselves as the waiting thread
                // 2. Release lock (so producer can add data)
                // 3. Park (will be unparked by producer)
                // 4. Reacquire lock and clear waiting thread
                waitingThread = Thread.currentThread();
                dataLock.unlock();
                try {
                    LockSupport.park();
                    if (Thread.interrupted()) {
                        throw new IOException("Interrupted waiting for data");
                    }
                } finally {
                    dataLock.lock();
                    waitingThread = null;
                }
            }

            // Check for error
            if (state.getReadState() == RS_ERROR) {
                throw readError;
            }

            // Check for EOF (no more data and stream is done)
            if (dataQueue.isEmpty() && state.getReadState() == RS_DONE) {
                // Auto-close stream when user reads to EOF to prevent resource leaks
                // even if they forget to call close() explicitly
                if (state.getStreamState() != SS_CLOSED) {
                    state.setStreamStateClosed();
                    if (streamId > 0) {
                        muxer.releaseStream(streamId);
                    }
                }
                validateContentLength();
                return -1; // EOF
            }

            // Drain up to maxChunks from queue
            int drained = 0;
            while (drained < maxChunks && !dataQueue.isEmpty()) {
                dest[drained++] = dataQueue.poll();
            }

            // Update timeout once per batch (moved from enqueueData for efficiency)
            if (drained > 0) {
                onReadActivity();
            }

            return drained;
        } finally {
            dataLock.unlock();
        }
    }

    /**
     * Called by H2DataInputStream when data is consumed.
     *
     * <p>Updates content length tracking. Note: flow control WINDOW_UPDATE is sent
     * in {@link #enqueueData} when data arrives, not here.
     *
     * @param bytesConsumed number of bytes consumed
     */
    void onDataConsumed(int bytesConsumed) {
        receivedContentLength += bytesConsumed;
    }

    /**
     * Called by muxer when SETTINGS changes initial window size.
     */
    void adjustSendWindow(int delta) {
        sendWindow.adjust(delta);
    }

    @Override
    public HttpRequest request() {
        return request;
    }

    @Override
    public synchronized OutputStream requestBody() {
        if (requestOut == null) {
            // If no request body is expected, then return a no-op stream.
            H2DataOutputStream rawOut = state.isEndStreamSent()
                    ? new H2DataOutputStream(this, muxer, 0)
                    : new H2DataOutputStream(this, muxer, muxer.getRemoteMaxFrameSize());
            requestOut = new DelegatedClosingOutputStream(rawOut, rw -> {
                rw.close(); // Send END_STREAM
                onRequestStreamClosed();
            });
        }
        return requestOut;
    }

    @Override
    public synchronized InputStream responseBody() throws IOException {
        // Ensure we have response headers first
        if (!state.isResponseHeadersReceived()) {
            readResponseHeaders();
        }

        if (responseIn == null) {
            // Optimization: for empty responses, return a null stream to avoid H2DataInputStream allocation.
            // But only do this if:
            // - content-length is explicitly 0, OR
            // - end stream is received AND no data is queued (truly empty response)
            boolean isEmpty = expectedContentLength == 0 || (state.isEndStreamReceived() && dataQueue.isEmpty());
            if (isEmpty) {
                var nio = InputStream.nullInputStream();
                responseIn = new DelegatedClosingInputStream(nio, this::onResponseStreamClosed);
            } else {
                H2DataInputStream dataStream = new H2DataInputStream(this, muxer::returnBuffer);
                responseIn = new DelegatedClosingInputStream(dataStream, this::onResponseStreamClosed);
            }
        }
        return responseIn;
    }

    private void onRequestStreamClosed() throws IOException {
        if (closedStreamCount.incrementAndGet() == BOTH_STREAMS_CLOSED) {
            close();
        }
    }

    private void onResponseStreamClosed(InputStream _ignored) throws IOException {
        if (closedStreamCount.incrementAndGet() == BOTH_STREAMS_CLOSED) {
            close();
        }
    }

    @Override
    public HttpHeaders responseHeaders() throws IOException {
        if (!state.isResponseHeadersReceived()) {
            readResponseHeaders();
        }
        return responseHeaders;
    }

    @Override
    public int responseStatusCode() throws IOException {
        if (!state.isResponseHeadersReceived()) {
            readResponseHeaders();
        }
        return state.getStatusCode();
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
        if (requestOut != null && !state.isEndStreamSent()) {
            try {
                requestOut.close();
            } catch (IOException ignored) {}
        }

        // If response not fully received and stream was started, queue RST_STREAM
        if (!state.isEndStreamReceived() && streamId > 0 && state.getStreamState() != SS_CLOSED) {
            // Best-effort cleanup - CLQ never blocks or fails
            muxer.queueControlFrame(streamId, H2Muxer.ControlFrameType.RST_STREAM, ERROR_CANCEL, 100);
            // Signal end to any waiting consumers
            state.setReadStateDone();
            // Signal outside lock - lock-free wakeup
            Thread t = waitingThread;
            if (t != null) {
                LockSupport.unpark(t);
            }
        }

        // Return all queued buffers to connection pool for reuse
        dataLock.lock();
        try {
            DataChunk chunk;
            while ((chunk = dataQueue.poll()) != null) {
                muxer.returnBuffer(chunk.data());
            }
        } finally {
            dataLock.unlock();
        }

        // Mark stream as closed
        state.setStreamStateClosed();

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
            int rs;
            while (pendingHeaders == null && (rs = state.getReadState()) != RS_ERROR && rs != RS_DONE) {
                // Wait using lock-free signaling
                waitingThread = Thread.currentThread();
                dataLock.unlock();
                try {
                    LockSupport.park(); // Untimed: muxer watchdog handles timeout
                    if (Thread.interrupted()) {
                        throw new IOException("Interrupted waiting for response");
                    }
                } finally {
                    dataLock.lock();
                    waitingThread = null;
                }
            }

            // Check for error
            if (state.getReadState() == RS_ERROR) {
                throw readError;
            }
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

        while (!state.isResponseHeadersReceived()) {
            awaitEvent();

            dataLock.lock();
            try {
                if (pendingHeaders != null) {
                    List<HeaderField> fields = pendingHeaders;
                    boolean endStream = pendingHeadersEndStream;
                    pendingHeaders = null; // Consume the headers

                    // Process headers (can throw)
                    handleHeadersEvent(fields, endStream);
                } else if (state.getReadState() == RS_DONE) {
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
        int ss = state.getStreamState();

        // Allow processing headers if the stream is CLOSED but closed cleanly (RS_DONE)
        // and we haven't processed the initial headers yet.
        // This handles the race where Reader processes HEADERS -> DATA+ES before App processes HEADERS.
        boolean cleanCloseRace = (ss == SS_CLOSED && state.getReadState() == RS_DONE
                && !state.isResponseHeadersReceived());

        // Validate stream state per RFC 9113 Section 5.1
        if (ss == SS_CLOSED && !cleanCloseRace) {
            throw new H2Exception(ERROR_STREAM_CLOSED, streamId, "Received HEADERS on closed stream");
        }

        if (!state.isResponseHeadersReceived()) {
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
            state.markEndStreamReceived(); // Atomically sets flag, read state to DONE, updates stream state
            clearReadDeadline(); // No more data expected
            validateContentLength();
        }
        // Note: setResponseHeadersReceived already transitions WAITING->READING
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
        H2ResponseHeaderProcessor.Result result =
                H2ResponseHeaderProcessor.processResponseHeaders(fields, streamId, isEndStream);

        if (result.isInformational()) {
            // 1xx response - wait for final response
            return;
        }

        // This is the final response (2xx-5xx)
        this.responseHeaders = result.headers();
        this.expectedContentLength = result.contentLength();
        state.setResponseHeadersReceived(result.statusCode());
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
        this.trailerHeaders = H2ResponseHeaderProcessor.processTrailers(fields, streamId);
    }

    /**
     * Validate Content-Length matches actual data received.
     * RFC 9113 Section 8.1.1.
     */
    private void validateContentLength() throws IOException {
        H2ResponseHeaderProcessor.validateContentLength(expectedContentLength, receivedContentLength, streamId);
    }

    /**
     * Update stream send window from WINDOW_UPDATE frame.
     *
     * <p>Called by the connection's reader thread when a stream-level
     * WINDOW_UPDATE is received. This releases capacity to the FlowControlWindow
     * and wakes any blocked threads via notifyAll().
     *
     * @param increment the window size increment
     * @throws H2Exception if the increment causes overflow
     */
    void updateStreamSendWindow(int increment) throws H2Exception {
        // Check for overflow per RFC 9113 before releasing
        int currentWindow = sendWindow.available();
        if ((long) currentWindow + increment > Integer.MAX_VALUE) {
            throw new H2Exception(ERROR_FLOW_CONTROL_ERROR,
                    streamId,
                    "Stream send window overflow");
        }
        sendWindow.release(increment);
    }

    /**
     * Write DATA frames for request body with flow control.
     *
     * <p>Uses batched flow control acquisition to prevent connection window starvation under high concurrency.
     * Acquires up to {@value #FLOW_CONTROL_BATCH_FRAMES} frames worth of window at a time.
     *
     * <p>Flow:
     * <ol>
     *   <li>VT acquires stream and connection flow control in batches</li>
     *   <li>VT copies data to pooled buffers and adds to pendingWrites queue</li>
     *   <li>VT signals writer thread once after all frames are queued</li>
     *   <li>Writer thread drains pendingWrites and writes frames</li>
     * </ol>
     *
     * @throws SocketTimeoutException if write timeout expires waiting for flow control window
     */
    void writeData(byte[] data, int offset, int length, boolean endStream) throws IOException {
        // If trailers are set and this is the last data, don't set END_STREAM on DATA frame
        // - trailers will carry END_STREAM instead
        boolean hasTrailers = requestTrailers != null;
        int maxFrameSize = muxer.getRemoteMaxFrameSize();

        while (length > 0) {
            // Acquire as much stream-level flow control as we can (up to remaining length)
            int batchSize = Math.min(length, maxFrameSize * FLOW_CONTROL_BATCH_FRAMES);
            int streamAcquired;
            try {
                streamAcquired = sendWindow.tryAcquireUpTo(batchSize, writeTimeoutMs);
                if (streamAcquired == 0) {
                    throw new SocketTimeoutException(String.format(
                            "Write timed out after %dms waiting for stream flow control window",
                            writeTimeoutMs));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for stream flow control window", e);
            }

            // Acquire connection-level flow control for what we got from stream
            int connAcquired;
            try {
                connAcquired = muxer.acquireConnectionWindowUpTo(streamAcquired, writeTimeoutMs);
                if (connAcquired == 0) {
                    sendWindow.release(streamAcquired);
                    throw new SocketTimeoutException(String.format(
                            "Write timed out after %dms waiting for connection flow control window",
                            writeTimeoutMs));
                }
                // Release excess stream permits if connection gave us less
                if (connAcquired < streamAcquired) {
                    sendWindow.release(streamAcquired - connAcquired);
                }
            } catch (InterruptedException e) {
                sendWindow.release(streamAcquired);
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for connection flow control window", e);
            } catch (SocketTimeoutException e) {
                sendWindow.release(streamAcquired);
                throw e;
            }

            // Write frames using the acquired window
            int batchRemaining = connAcquired;
            while (batchRemaining > 0 && length > 0) {
                int toSend = Math.min(Math.min(length, maxFrameSize), batchRemaining);
                boolean isLastChunk = (toSend == length);
                int flags = (endStream && isLastChunk && !hasTrailers) ? FLAG_END_STREAM : 0;
                byte[] buf = muxer.borrowBuffer(toSend);
                System.arraycopy(data, offset, buf, 0, toSend);

                pendingWrites.add(new PendingWrite().init(buf, 0, toSend, flags));

                offset += toSend;
                length -= toSend;
                batchRemaining -= toSend;
            }
        }

        // Signal writer thread once after all data is queued
        if (IN_WORK_QUEUE_HANDLE.compareAndSet(this, false, true)) {
            muxer.signalDataReady(this);
        }

        if (endStream) {
            if (hasTrailers) {
                muxer.queueTrailers(streamId, requestTrailers);
            }
            state.markEndStreamSent();
        }
    }

    /**
     * Send END_STREAM without data, or send trailers if set.
     *
     * <p>Uses the same pendingWrites queue as writeData() to ensure proper ordering.
     * This prevents END_STREAM from being sent before pending DATA frames.
     */
    void sendEndStream() {
        if (!state.isEndStreamSent()) {
            if (requestTrailers != null) {
                muxer.queueTrailers(streamId, requestTrailers);
            } else {
                // Use pendingWrites queue (same as writeData) to ensure ordering
                pendingWrites.add(new PendingWrite().init(H2Constants.EMPTY_BYTES, 0, 0, FLAG_END_STREAM));

                // Signal writer thread
                if (IN_WORK_QUEUE_HANDLE.compareAndSet(this, false, true)) {
                    muxer.signalDataReady(this);
                }
            }
            state.markEndStreamSent();
        }
    }

    @Override
    public HttpHeaders responseTrailerHeaders() {
        return trailerHeaders;
    }
}
