/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static software.amazon.smithy.java.http.client.h2.H2Constants.DEFAULT_INITIAL_WINDOW_SIZE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.DEFAULT_MAX_CONCURRENT_STREAMS;
import static software.amazon.smithy.java.http.client.h2.H2Constants.DEFAULT_MAX_FRAME_SIZE;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_ACK;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_DATA;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_PING;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.client.ByteAllocator;
import software.amazon.smithy.java.http.client.h2.hpack.HpackEncoder;
import software.amazon.smithy.java.io.ByteBufferOutputStream;

/**
 * HTTP/2 stream multiplexer that coordinates concurrent streams over a single connection.
 *
 * <p>This class manages:
 * <ul>
 *   <li>Stream registry and lifecycle</li>
 *   <li>Connection and stream flow control</li>
 *   <li>HPACK encoding and frame writing (via dedicated writer thread)</li>
 *   <li>Work queue processing with batching</li>
 * </ul>
 *
 * <h2>Threading Model</h2>
 * <ul>
 *   <li>Reader thread calls {@code on*} methods to deliver inbound frames</li>
 *   <li>User VTs call {@code newExchange}, queue writes via exchanges</li>
 *   <li>Writer thread processes queued work: encodes headers, writes frames</li>
 * </ul>
 */
final class H2Muxer implements AutoCloseable {

    /**
     * Callback interface for connection-level operations.
     */
    interface ConnectionCallback {
        boolean isAcceptingStreams();

        int getRemoteMaxHeaderListSize();
    }

    enum ControlFrameType {
        RST_STREAM,
        WINDOW_UPDATE,
        SETTINGS_ACK,
        PING,
        GOAWAY
    }

    // The resolution of the tick-based timeout system, used to check for read timeouts.
    static final int TIMEOUT_POLL_INTERVAL_MS = 100;

    // Reusable singleton work items
    private static final H2MuxerWorkItem.CheckDataQueue CHECK_DATA_QUEUE = H2MuxerWorkItem.CheckDataQueue.INSTANCE;
    private static final H2MuxerWorkItem.Shutdown SHUTDOWN = H2MuxerWorkItem.Shutdown.INSTANCE;
    private static final H2MuxerWorkItem.WriteSettingsAck SETTINGS_ACK = H2MuxerWorkItem.WriteSettingsAck.INSTANCE;

    // Static method reference to avoid allocation in hot timeout check path
    private static final BiConsumer<H2Exchange, Integer> TIMEOUT_CHECKER = H2Muxer::checkExchangeTimeout;

    // === STREAM REGISTRY ===
    private final StreamRegistry streams = new StreamRegistry();
    private final AtomicInteger activeStreamCount = new AtomicInteger(0);
    private final AtomicInteger nextStreamId = new AtomicInteger(1);
    private volatile int lastAllocatedStreamId = 0;

    // === SETTINGS FROM PEER ===
    private volatile int remoteMaxConcurrentStreams = DEFAULT_MAX_CONCURRENT_STREAMS;
    private volatile int remoteInitialWindowSize = DEFAULT_INITIAL_WINDOW_SIZE;
    private volatile int remoteMaxFrameSize = DEFAULT_MAX_FRAME_SIZE;

    // === CONNECTION FLOW CONTROL ===
    private final FlowControlWindow connectionSendWindow;

    // === STATE ===
    private volatile boolean accepting = true;
    private volatile boolean running = true;
    private volatile boolean goawayReceived = false;
    private volatile int goawayLastStreamId = Integer.MAX_VALUE;
    private volatile IOException writeError;

    // Tick-based timeout: incremented every TIMEOUT_POLL_INTERVAL_MS by watchdog
    private volatile int timeoutTick;

    // === DEPENDENCIES ===
    private final ConnectionCallback connectionCallback;
    private final H2FrameCodec frameCodec;
    private final ByteAllocator allocator;
    private final int initialWindowSize;

    // === WORK QUEUES ===
    // CLQ + LockSupport for lock-free work submission without DelayScheduler overhead
    private final ConcurrentLinkedQueue<H2MuxerWorkItem> workQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<H2Exchange> dataWorkQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean dataWorkPending = new AtomicBoolean(false);

    // === HEADER ENCODER (only accessed by writer thread) ===
    private final H2RequestHeaderEncoder headerEncoder;
    private final AtomicInteger pendingTableSizeUpdate = new AtomicInteger(-1);

    // === WRITER THREAD ===
    private final Thread workerThread;

    /**
     * Create a new multiplexer.
     *
     * @param connectionCallback callback for connection-level state
     * @param frameCodec the frame codec for writing
     * @param initialTableSize initial HPACK table size
     * @param threadName name for the writer thread
     * @param initialWindowSize initial flow control window size
     */
    H2Muxer(
            ConnectionCallback connectionCallback,
            H2FrameCodec frameCodec,
            int initialTableSize,
            String threadName,
            int initialWindowSize
    ) {
        this.connectionCallback = connectionCallback;
        this.frameCodec = frameCodec;
        this.initialWindowSize = initialWindowSize;
        this.connectionSendWindow = new FlowControlWindow(DEFAULT_INITIAL_WINDOW_SIZE);
        this.allocator = new ByteAllocator(64, initialWindowSize, initialWindowSize, 1024);
        this.headerEncoder = new H2RequestHeaderEncoder(
                new HpackEncoder(initialTableSize),
                new ByteBufferOutputStream(512));
        this.workerThread = Thread.ofVirtual().name(threadName).start(this::workerLoop);
    }

    // ==================== LIFECYCLE ====================

    /**
     * Create a new exchange for a request.
     */
    H2Exchange newExchange(HttpRequest request, long readTimeoutMs, long writeTimeoutMs) throws IOException {
        if (!accepting) {
            throw new IOException("Connection is not accepting new streams");
        }

        if (goawayReceived) {
            int nextId = nextStreamId.get();
            if (nextId > goawayLastStreamId) {
                throw new IOException("Connection received GOAWAY with lastStreamId=" +
                        goawayLastStreamId + ", cannot create stream " + nextId);
            }
        }

        if (!tryReserveStream()) {
            throw new IOException("Connection at max concurrent streams: " + activeStreamCount.get() +
                    " (limit: " + remoteMaxConcurrentStreams + ")");
        }

        return new H2Exchange(this, request, readTimeoutMs, writeTimeoutMs, initialWindowSize);
    }

    /**
     * Close all exchanges gracefully.
     */
    void closeExchanges(Duration timeout) {
        accepting = false;
        streams.forEach(null, (exchange, _ignore) -> {
            exchange.signalConnectionClosed(null);
        });

        long deadline = System.nanoTime() + timeout.toNanos();
        while (activeStreamCount.get() > 0 && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Force close any remaining exchanges and clear slots
        streams.clearAndClose(exchange -> {
            try {
                exchange.close();
            } catch (Exception ignored) {
                // trying to close, ignore failure
            }
        });
        activeStreamCount.set(0);
    }

    H2Exchange getExchange(int streamId) {
        return streams.get(streamId);
    }

    int getActiveStreamCount() {
        return activeStreamCount.get();
    }

    boolean canAcceptMoreStreams() {
        return accepting && !goawayReceived && activeStreamCount.get() < remoteMaxConcurrentStreams;
    }

    /**
     * Get the active stream count if this muxer can accept more streams, or -1 if not.
     * Combines the availability check with getting the count to avoid redundant atomic reads.
     */
    int getActiveStreamCountIfAccepting() {
        if (!accepting || goawayReceived) {
            return -1;
        }
        int count = activeStreamCount.get();
        return count < remoteMaxConcurrentStreams ? count : -1;
    }

    int getLastAllocatedStreamId() {
        return lastAllocatedStreamId;
    }

    private boolean tryReserveStream() {
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

    void releaseStream(int streamId) {
        if (streams.remove(streamId)) {
            activeStreamCount.decrementAndGet();
        }
    }

    void releaseStreamSlot() {
        activeStreamCount.decrementAndGet();
    }

    int allocateAndRegisterStream(H2Exchange exchange) {
        int streamId = nextStreamId.getAndAdd(2);
        exchange.setStreamId(streamId);
        streams.put(streamId, exchange);
        lastAllocatedStreamId = streamId;
        return streamId;
    }

    void onConnectionClosing(Throwable error) {
        accepting = false;
        streams.forEach(error, H2Exchange::signalConnectionClosed);
    }

    void onSettingsReceived(int maxConcurrentStreams, int initialWindowSize, int maxFrameSize) {
        this.remoteMaxConcurrentStreams = maxConcurrentStreams;
        this.remoteMaxFrameSize = maxFrameSize;

        int delta = initialWindowSize - this.remoteInitialWindowSize;
        this.remoteInitialWindowSize = initialWindowSize;
        if (delta != 0) {
            streams.forEach(delta, H2Exchange::adjustSendWindow);
        }
    }

    void onGoaway(int lastStreamId, int errorCode) {
        goawayReceived = true;
        goawayLastStreamId = lastStreamId;
        accepting = false;

        H2Exception refusedError = new H2Exception(
                errorCode,
                "Stream affected by GOAWAY (lastStreamId=" + lastStreamId
                        + ", error=" + H2Constants.errorCodeName(errorCode) + ")");
        streams.forEachMatching(
                streamId -> streamId > lastStreamId,
                exchange -> exchange.signalConnectionClosed(refusedError));
    }

    // ==================== FLOW CONTROL ====================

    void acquireConnectionWindow(int requestedBytes, long timeoutMs)
            throws SocketTimeoutException, InterruptedException {
        if (!connectionSendWindow.tryAcquire(requestedBytes, timeoutMs)) {
            throw new SocketTimeoutException(
                    "Write timed out after " + timeoutMs + "ms waiting for connection flow control window");
        }
    }

    void releaseConnectionWindow(int bytes) {
        int currentWindow = connectionSendWindow.available();
        if ((long) currentWindow + bytes <= Integer.MAX_VALUE) {
            connectionSendWindow.release(bytes);
        }
    }

    // ==================== WRITE QUEUE ====================

    void signalDataReady(H2Exchange exchange) {
        if (!accepting) {
            return;
        }
        dataWorkQueue.offer(exchange);
        // CAS ensures only one thread enqueues CHECK_DATA_QUEUE per batch
        if (dataWorkPending.compareAndSet(false, true)) {
            enqueue(CHECK_DATA_QUEUE);
        }
    }

    /**
     * Enqueue a work item with deadline and signal the writer.
     */
    private void enqueue(H2MuxerWorkItem item, long timeoutMs) {
        item.deadlineTick = deadlineTick(timeoutMs);
        workQueue.add(item);
        signalWriter();
    }

    /**
     * Enqueue a work item without timeout and signal the writer.
     */
    private void enqueue(H2MuxerWorkItem item) {
        item.deadlineTick = 0;
        workQueue.add(item);
        signalWriter();
    }

    void queueControlFrame(int streamId, ControlFrameType frameType, Object payload, long timeoutMs) {
        H2MuxerWorkItem item = switch (frameType) {
            case RST_STREAM -> new H2MuxerWorkItem.WriteRst(streamId, (Integer) payload);
            case WINDOW_UPDATE -> new H2MuxerWorkItem.WriteWindowUpdate(streamId, (Integer) payload);
            case SETTINGS_ACK -> SETTINGS_ACK;
            case PING -> new H2MuxerWorkItem.WritePing((byte[]) payload, false);
            case GOAWAY -> {
                Object[] args = (Object[]) payload;
                yield new H2MuxerWorkItem.WriteGoaway((Integer) args[0], (Integer) args[1], (String) args[2]);
            }
        };
        enqueue(item, timeoutMs);
    }

    void queueTrailers(int streamId, HttpHeaders trailers) {
        enqueue(new H2MuxerWorkItem.WriteTrailers(streamId, trailers));
    }

    void queueData(int streamId, byte[] data, int offset, int length, int flags) {
        enqueue(new H2MuxerWorkItem.WriteData(streamId, data, offset, length, flags));
    }

    /**
     * Submit a HEADERS frame for encoding and writing.
     * Always succeeds (CLQ is unbounded, bounded by stream slots).
     * Timeout is enforced by watchdog sweep checking deadlineTick.
     *
     * <p>After calling this method, the caller should call {@link H2Exchange#awaitWriteCompletion()}
     * to block until the write completes, then read the stream ID from the exchange.
     *
     * @param request the HTTP request
     * @param exchange the exchange
     * @param endStream whether END_STREAM should be set
     * @param timeoutMs timeout for write completion (checked by watchdog)
     * @return true if submitted, false if not accepting
     */
    boolean submitHeaders(HttpRequest request, H2Exchange exchange, boolean endStream, long timeoutMs) {
        if (!accepting) {
            return false;
        }
        enqueue(new H2MuxerWorkItem.EncodeHeaders(request, exchange, endStream), timeoutMs);
        return true;
    }

    // ==================== BUFFER ALLOCATION ====================

    byte[] borrowBuffer(int minSize) {
        return allocator.borrow(minSize);
    }

    void returnBuffer(byte[] buffer) {
        allocator.release(buffer);
    }

    // ==================== SETTINGS ====================

    int getRemoteMaxFrameSize() {
        return remoteMaxFrameSize;
    }

    int getRemoteInitialWindowSize() {
        return remoteInitialWindowSize;
    }

    int getInitialWindowSize() {
        return initialWindowSize;
    }

    /**
     * Get the current timeout tick for deadline calculations.
     * Called by exchanges when read activity occurs.
     */
    int currentTimeoutTick() {
        return timeoutTick;
    }

    /**
     * Convert timeout in milliseconds to deadline tick.
     * Returns 0 if timeoutMs <= 0 (no timeout).
     */
    private int deadlineTick(long timeoutMs) {
        if (timeoutMs <= 0) {
            return 0;
        }
        int timeoutTicks = (int) Math.ceil((double) timeoutMs / TIMEOUT_POLL_INTERVAL_MS);
        return timeoutTick + timeoutTicks;
    }

    /**
     * Signal the writer thread that work is available.
     * Uses LockSupport.unpark which is safe to call even if thread isn't parked.
     */
    private void signalWriter() {
        LockSupport.unpark(workerThread);
    }

    void setMaxTableSize(int newSize) {
        pendingTableSizeUpdate.set(newSize);
    }

    IOException getWriteError() {
        return writeError;
    }

    /**
     * Check all active streams for read timeouts, called periodically from the worker loop.
     *
     * <p>Read timeouts are approximate: ±100ms due to the polling interval. This is acceptable because network
     * I/O already has inherent latency variance, and callers setting a "30s timeout" don't expect millisecond
     * precision.
     *
     * <p>Uses a tick-based system where the watchdog increments a global tick counter every poll interval.
     * Exchanges track their deadline as a tick number rather than nanoseconds, eliminating System.nanoTime()
     * calls from the hot path.
     *
     * <p>There is an unavoidable race: data could arrive just after we decide to timeout but before we signal.
     * We mitigate this by checking both deadline and activity sequence twice - we only timeout if the stream
     * appears expired and idle across two snapshots. The remaining race window is small and acceptable because
     * timeouts are approximate and failure is recoverable at the caller layer.
     */
    private void checkReadTimeouts(int tick) {
        streams.forEach(tick, TIMEOUT_CHECKER);
    }

    private static void checkExchangeTimeout(H2Exchange exchange, int nowTick) {
        long seq1 = exchange.getReadSeq();
        int d1 = exchange.getReadDeadlineTick();
        if (d1 <= 0 || nowTick < d1) {
            return;
        }

        // Second snapshot: did anything change while we were looking?
        long seq2 = exchange.getReadSeq();
        int d2 = exchange.getReadDeadlineTick();
        if (seq1 != seq2 || d2 <= 0 || nowTick < d2) {
            return;
        }

        // Try to claim the timeout - only first caller wins
        if (!exchange.markReadTimedOut()) {
            return;
        }

        exchange.signalConnectionClosed(new SocketTimeoutException(
                "Read timeout: no data received for " + exchange.getReadTimeoutMs() + "ms"));
    }

    /**
     * Check for write timeouts by examining the head of the work queue.
     * If the head item has a deadline that has passed, fail the connection.
     * Since items are processed in order, if head is stuck, everything is stuck.
     */
    private void checkWriteTimeouts(int tick) {
        H2MuxerWorkItem head = workQueue.peek();
        if (head != null && head.deadlineTick > 0 && tick >= head.deadlineTick) {
            failWriter(new SocketTimeoutException(
                    "Write timeout: work item stuck in queue (deadline tick " + head.deadlineTick
                            + ", current tick " + tick + ")"));
        }
    }

    // ==================== WRITER THREAD ====================

    private void workerLoop() {
        var batch = new ArrayList<H2MuxerWorkItem>(64);
        IOException failure = null;
        long lastTimeoutCheck = System.currentTimeMillis();

        try {
            while (running) {
                // Drain all available work items from the queue
                H2MuxerWorkItem item;
                while ((item = workQueue.poll()) != null) {
                    if (item instanceof H2MuxerWorkItem.Shutdown) {
                        processBatch(batch);
                        return;
                    }
                    if (!(item instanceof H2MuxerWorkItem.CheckDataQueue)) {
                        batch.add(item);
                    }
                }

                if (!batch.isEmpty()) {
                    processBatch(batch);
                }

                dataWorkPending.set(false);

                boolean processedData = false;
                H2Exchange exchange;
                while ((exchange = dataWorkQueue.poll()) != null) {
                    processExchangePendingWrites(exchange);
                    processedData = true;
                }

                if (processedData) {
                    try {
                        frameCodec.flush();
                    } catch (IOException e) {
                        failWriter(e);
                        return;
                    }
                }

                // Check for timeouts periodically using tick-based system
                long now = System.currentTimeMillis();
                if (now - lastTimeoutCheck >= TIMEOUT_POLL_INTERVAL_MS) {
                    int tick = ++timeoutTick;
                    checkReadTimeouts(tick);
                    checkWriteTimeouts(tick);
                    lastTimeoutCheck = now;
                }

                // Park until signaled or timeout interval elapses (for watchdog)
                // LockSupport.parkNanos is VT-friendly and doesn't create DelayScheduler tasks
                LockSupport.parkNanos(TIMEOUT_POLL_INTERVAL_MS * 1_000_000L);
            }
        } catch (Throwable t) {
            failure = new IOException("Writer thread crashed", t);
        } finally {
            if (failure != null) {
                failWriter(failure);
            } else {
                drainAndFailPending(new IOException("Muxer shutting down"));
            }
        }
    }

    private void processExchangePendingWrites(H2Exchange exchange) {
        exchange.inWorkQueue = false;

        int streamId = exchange.getStreamId();
        PendingWrite pw;
        while ((pw = exchange.pendingWrites.poll()) != null) {
            byte[] buffer = pw.data;
            try {
                frameCodec.writeFrame(
                        FRAME_TYPE_DATA,
                        pw.flags,
                        streamId,
                        pw.data,
                        pw.offset,
                        pw.length);
            } catch (IOException e) {
                exchange.returnBuffer(buffer);
                failWriter(e);
                return;
            }
            exchange.returnBuffer(buffer);
            pw.reset();
        }

        if (!exchange.pendingWrites.isEmpty() && !exchange.inWorkQueue) {
            exchange.inWorkQueue = true;
            dataWorkQueue.offer(exchange);
        }
    }

    private void processBatch(ArrayList<H2MuxerWorkItem> batch) {
        if (batch.isEmpty()) {
            return;
        }

        try {
            for (H2MuxerWorkItem item : batch) {
                processItem(item);
            }
            frameCodec.flush();
            for (H2MuxerWorkItem item : batch) {
                completeItem(item, null);
            }
        } catch (IOException e) {
            for (H2MuxerWorkItem item : batch) {
                completeItem(item, e);
            }
        } finally {
            batch.clear();
        }
    }

    private void processItem(H2MuxerWorkItem item) throws IOException {
        switch (item) {
            case H2MuxerWorkItem.EncodeHeaders h -> processEncodeHeaders(h);
            case H2MuxerWorkItem.WriteData d ->
                frameCodec.writeFrame(FRAME_TYPE_DATA, d.flags, d.streamId, d.data, d.offset, d.length);
            case H2MuxerWorkItem.WriteTrailers t -> processWriteTrailers(t);
            case H2MuxerWorkItem.WriteRst r -> frameCodec.writeRstStream(r.streamId, r.errorCode);
            case H2MuxerWorkItem.WriteGoaway g -> frameCodec.writeGoaway(g.lastStreamId, g.errorCode, g.debugData);
            case H2MuxerWorkItem.WriteWindowUpdate w -> frameCodec.writeWindowUpdate(w.streamId, w.increment);
            case H2MuxerWorkItem.WriteSettingsAck s -> frameCodec.writeSettingsAck();
            case H2MuxerWorkItem.WritePing p ->
                frameCodec.writeFrame(FRAME_TYPE_PING, p.ack ? FLAG_ACK : 0, 0, p.payload);
            case H2MuxerWorkItem.Shutdown s -> {
            }
            case H2MuxerWorkItem.CheckDataQueue c -> {
            }
        }
    }

    private void processEncodeHeaders(H2MuxerWorkItem.EncodeHeaders req) throws IOException {
        H2Exchange exchange = req.exchange;

        if (!connectionCallback.isAcceptingStreams()) {
            throw new IOException("Connection is not accepting new streams");
        }

        int streamId = allocateAndRegisterStream(exchange);

        try {
            // Atomically read and clear to avoid losing updates from concurrent setMaxTableSize calls
            int tableUpdate = pendingTableSizeUpdate.getAndSet(-1);
            if (tableUpdate >= 0) {
                headerEncoder.setMaxTableSize(tableUpdate);
            }

            headerEncoder.encodeHeaders(req.request, connectionCallback.getRemoteMaxHeaderListSize());

            exchange.onHeadersEncoded(req.endStream);
            frameCodec.writeHeaders(streamId, headerEncoder.buffer(), 0, headerEncoder.size(), req.endStream);

            // Stream ID is already set on exchange by allocateAndRegisterStream
            // Caller will read it after awaitWriteCompletion returns

        } catch (Exception e) {
            releaseStream(streamId);
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException("Encoding failed", e);
        }
    }

    private void processWriteTrailers(H2MuxerWorkItem.WriteTrailers req) throws IOException {
        headerEncoder.encodeTrailers(req.trailers);
        frameCodec.writeHeaders(req.streamId, headerEncoder.buffer(), 0, headerEncoder.size(), true);
    }

    private void completeItem(H2MuxerWorkItem item, IOException error) {
        // Get the exchange to signal (only EncodeHeaders has an exchange directly)
        H2Exchange exchange = (item instanceof H2MuxerWorkItem.EncodeHeaders h) ? h.exchange : null;
        if (exchange != null) {
            if (error == null) {
                exchange.signalWriteSuccess();
            } else {
                exchange.signalWriteFailure(error);
            }
        }
    }

    private void failWriter(IOException e) {
        if (writeError == null) {
            writeError = e;
        }
        accepting = false;
        drainAndFailPending(writeError);
    }

    private void drainAndFailPending(IOException error) {
        H2MuxerWorkItem item;
        while ((item = workQueue.poll()) != null) {
            completeItem(item, error);
        }
    }

    @Override
    public void close() {
        accepting = false;

        // Signal writer to process remaining work before we shut down
        signalWriter();

        long deadline = System.currentTimeMillis() + 1000;
        while (!workQueue.isEmpty() && System.currentTimeMillis() < deadline) {
            signalWriter(); // Keep signaling in case writer parks between checks
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        running = false;
        enqueue(SHUTDOWN);

        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        drainAndFailPending(new IOException("Muxer shutting down"));
    }

    void shutdownNow() {
        accepting = false;
        running = false;
        enqueue(SHUTDOWN);
        if (workerThread != null) {
            workerThread.interrupt();
        }
        drainAndFailPending(new IOException("Muxer shutting down"));
    }
}
