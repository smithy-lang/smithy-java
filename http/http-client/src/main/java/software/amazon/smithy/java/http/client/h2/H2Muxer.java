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
import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_AUTHORITY;
import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_METHOD;
import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_PATH;
import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_SCHEME;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.client.BufferPool;
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

    /**
     * Work items processed by the writer thread.
     */
    private sealed interface WorkItem {
        record EncodeHeaders(
                HttpRequest request,
                H2Exchange exchange,
                boolean endStream,
                CompletableFuture<Integer> streamIdFuture,
                CompletableFuture<Void> writeComplete) implements WorkItem {}

        record WriteData(
                int streamId,
                byte[] data,
                int offset,
                int length,
                int flags,
                CompletableFuture<Void> completion) implements WorkItem {}

        record WriteTrailers(
                int streamId,
                HttpHeaders trailers,
                CompletableFuture<Void> completion) implements WorkItem {}

        record WriteRst(int streamId, int errorCode) implements WorkItem {}

        record WriteGoaway(int lastStreamId, int errorCode, String debugData) implements WorkItem {}

        record WriteWindowUpdate(int streamId, int increment) implements WorkItem {}

        record WriteSettingsAck() implements WorkItem {}

        record WritePing(byte[] payload, boolean ack) implements WorkItem {}

        record Shutdown() implements WorkItem {}

        record CheckDataQueue() implements WorkItem {}
    }

    enum ControlFrameType {
        RST_STREAM,
        WINDOW_UPDATE,
        SETTINGS_ACK,
        PING,
        GOAWAY
    }

    // Headers that must not be sent over HTTP/2 (connection-specific)
    private static final Set<String> CONNECTION_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-connection",
            "transfer-encoding",
            "upgrade",
            "host");

    // Headers that should not be indexed in HPACK (contain sensitive data)
    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization",
            "cookie",
            "proxy-authorization",
            "set-cookie");

    // How often to check for read timeouts (every ~100ms)
    private static final long READ_TIMOUT_FREQUENCY = TimeUnit.MILLISECONDS.toNanos(100);

    // Singleton wake-up signal
    private static final WorkItem.CheckDataQueue CHECK_DATA_QUEUE = new WorkItem.CheckDataQueue();

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
    private final FlowControlWindow connectionSendWindow = new FlowControlWindow(DEFAULT_INITIAL_WINDOW_SIZE);

    // === STATE ===
    private volatile boolean accepting = true;
    private volatile boolean running = true;
    private volatile boolean goawayReceived = false;
    private volatile int goawayLastStreamId = Integer.MAX_VALUE;
    private volatile IOException writeError;

    // === DEPENDENCIES ===
    private final ConnectionCallback connectionCallback;
    private final H2FrameCodec frameCodec;
    private final BufferPool bufferPool;

    // === WORK QUEUES ===
    private final BlockingQueue<WorkItem> workQueue;
    private final ConcurrentLinkedQueue<H2Exchange> dataWorkQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean dataWorkPending;

    // === HPACK ENCODER (only accessed by writer thread) ===
    private final HpackEncoder hpackEncoder;
    private final ByteBufferOutputStream headerEncodeBuffer;
    private volatile int pendingTableSizeUpdate = -1;

    // === WRITER THREAD ===
    private final Thread workerThread;

    /**
     * Create a new multiplexer.
     *
     * @param connectionCallback callback for connection-level state
     * @param frameCodec the frame codec for writing
     * @param initialTableSize initial HPACK table size
     * @param threadName name for the writer thread
     */
    H2Muxer(ConnectionCallback connectionCallback, H2FrameCodec frameCodec, int initialTableSize, String threadName) {
        this.connectionCallback = connectionCallback;
        this.frameCodec = frameCodec;
        this.bufferPool = new BufferPool(32, DEFAULT_INITIAL_WINDOW_SIZE, DEFAULT_INITIAL_WINDOW_SIZE, 1024);
        this.workQueue = new ArrayBlockingQueue<>(H2Constants.WRITER_QUEUE_CAPACITY);
        this.hpackEncoder = new HpackEncoder(initialTableSize);
        this.headerEncodeBuffer = new ByteBufferOutputStream(512);
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

        return new H2Exchange(this, request, readTimeoutMs, writeTimeoutMs);
    }

    /**
     * Close all exchanges gracefully.
     */
    void closeExchanges(Duration timeout) {
        accepting = false;

        streams.forEach(null, (exchange, _ignore) -> exchange.signalConnectionClosed(null));

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
                "Stream affected by GOAWAY (lastStreamId=" + lastStreamId +
                        ", error=" + H2Constants.errorCodeName(errorCode) + ")");
        streams.forEachMatching(
                streamId -> streamId > lastStreamId,
                exchange -> exchange.signalConnectionClosed(refusedError));
    }

    // ==================== FLOW CONTROL ====================

    void acquireConnectionWindow(int requestedBytes, long timeoutMs)
            throws SocketTimeoutException, InterruptedException {
        if (!connectionSendWindow.tryAcquire(requestedBytes, timeoutMs, TimeUnit.MILLISECONDS)) {
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
        if (!dataWorkPending) {
            dataWorkPending = true;
            workQueue.offer(CHECK_DATA_QUEUE);
        }
    }

    void queueControlFrame(int streamId, ControlFrameType frameType, Object payload, long timeoutMs)
            throws IOException {
        WorkItem item = switch (frameType) {
            case RST_STREAM -> new WorkItem.WriteRst(streamId, (Integer) payload);
            case WINDOW_UPDATE -> new WorkItem.WriteWindowUpdate(streamId, (Integer) payload);
            case SETTINGS_ACK -> new WorkItem.WriteSettingsAck();
            case PING -> new WorkItem.WritePing((byte[]) payload, false);
            case GOAWAY -> {
                Object[] args = (Object[]) payload;
                yield new WorkItem.WriteGoaway((Integer) args[0], (Integer) args[1], (String) args[2]);
            }
        };

        try {
            if (!workQueue.offer(item, timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new IOException("Work queue full, cannot queue control frame (timeout: " + timeoutMs + "ms)");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while queuing control frame", e);
        }
    }

    void queueTrailers(int streamId, HttpHeaders trailers) throws IOException {
        WorkItem item = new WorkItem.WriteTrailers(streamId, trailers, new CompletableFuture<>());
        if (!workQueue.offer(item)) {
            throw new IOException("Work queue full, cannot queue trailers");
        }
    }

    void queueData(int streamId, byte[] data, int offset, int length, int flags) throws IOException {
        WorkItem item = new WorkItem.WriteData(streamId, data, offset, length, flags, new CompletableFuture<>());
        if (!workQueue.offer(item)) {
            throw new IOException("Work queue full, cannot queue data frame");
        }
    }

    /**
     * Submit a HEADERS frame for encoding and writing.
     *
     * @param request the HTTP request
     * @param exchange the exchange
     * @param endStream whether END_STREAM should be set
     * @param streamIdFuture future completed with stream ID after allocation
     * @param writeComplete future completed when write finishes
     * @param timeoutMs timeout for queue submission
     * @return true if submitted, false if queue full or not accepting
     */
    boolean submitHeaders(
            HttpRequest request,
            H2Exchange exchange,
            boolean endStream,
            CompletableFuture<Integer> streamIdFuture,
            CompletableFuture<Void> writeComplete,
            long timeoutMs
    ) {
        if (!accepting) {
            return false;
        }
        var item = new WorkItem.EncodeHeaders(request, exchange, endStream, streamIdFuture, writeComplete);
        try {
            return workQueue.offer(item, timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ==================== BUFFER POOL ====================

    byte[] borrowBuffer(int minSize) {
        return bufferPool.borrow(minSize);
    }

    void returnBuffer(byte[] buffer) {
        if (buffer != null) {
            bufferPool.release(buffer);
        }
    }

    // ==================== SETTINGS ====================

    int getRemoteMaxFrameSize() {
        return remoteMaxFrameSize;
    }

    int getRemoteInitialWindowSize() {
        return remoteInitialWindowSize;
    }

    void setMaxTableSize(int newSize) {
        this.pendingTableSizeUpdate = newSize;
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
     * <p>There is an unavoidable race: data could arrive just after we decide to timeout but before we signal.
     * We mitigate this by checking both deadline and activity sequence twice - we only timeout if the stream
     * appears expired and idle across two snapshots. The remaining race window is small and acceptable because
     * timeouts are approximate and failure is recoverable at the caller layer.
     */
    private void checkReadTimeouts(long nowNanos) {
        streams.forEach(nowNanos, H2Muxer::checkExchangeTimeout);
    }

    private static void checkExchangeTimeout(H2Exchange exchange, long nowNanos) {
        long seq1 = exchange.getReadSeq();
        long d1 = exchange.getReadDeadlineNanos();
        if (d1 <= 0 || nowNanos <= d1) {
            return;
        }

        // Second snapshot: did anything change while we were looking?
        long seq2 = exchange.getReadSeq();
        long d2 = exchange.getReadDeadlineNanos();
        if (seq1 != seq2 || d2 <= 0 || nowNanos <= d2) {
            return;
        }

        // Try to claim the timeout - only first caller wins
        if (!exchange.markReadTimedOut()) {
            return;
        }

        exchange.signalConnectionClosed(new SocketTimeoutException(
                "Read timeout: no data received for " + exchange.getReadTimeoutMs() + "ms"));
    }

    // ==================== WRITER THREAD ====================

    private void workerLoop() {
        var batch = new ArrayList<WorkItem>(64);
        IOException failure = null;
        long lastTimeoutCheck = System.nanoTime();
        var readTimeoutFrequency = READ_TIMOUT_FREQUENCY;

        try {
            while (running) {
                WorkItem item = workQueue.take();

                if (item instanceof WorkItem.Shutdown) {
                    return;
                }

                if (!(item instanceof WorkItem.CheckDataQueue)) {
                    batch.add(item);
                }

                while ((item = workQueue.poll()) != null) {
                    if (item instanceof WorkItem.Shutdown) {
                        processBatch(batch);
                        return;
                    }
                    if (!(item instanceof WorkItem.CheckDataQueue)) {
                        batch.add(item);
                    }
                }

                if (!batch.isEmpty()) {
                    processBatch(batch);
                }

                dataWorkPending = false;

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

                // Check for read timeouts periodically
                long now = System.nanoTime();
                if (now - lastTimeoutCheck > readTimeoutFrequency) {
                    checkReadTimeouts(now);
                    lastTimeoutCheck = now;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (running) {
                failure = new IOException("Writer thread interrupted", e);
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

    private void processBatch(ArrayList<WorkItem> batch) {
        if (batch.isEmpty()) {
            return;
        }

        try {
            for (WorkItem item : batch) {
                processItem(item);
            }
            frameCodec.flush();
            for (WorkItem item : batch) {
                completeItem(item, null);
            }
        } catch (IOException e) {
            for (WorkItem item : batch) {
                completeItem(item, e);
            }
        } finally {
            batch.clear();
        }
    }

    private void processItem(WorkItem item) throws IOException {
        switch (item) {
            case WorkItem.EncodeHeaders h -> processEncodeHeaders(h);
            case WorkItem.WriteData d ->
                frameCodec.writeFrame(FRAME_TYPE_DATA, d.flags(), d.streamId(), d.data(), d.offset(), d.length());
            case WorkItem.WriteTrailers t -> processWriteTrailers(t);
            case WorkItem.WriteRst r -> frameCodec.writeRstStream(r.streamId(), r.errorCode());
            case WorkItem.WriteGoaway g -> frameCodec.writeGoaway(g.lastStreamId(), g.errorCode(), g.debugData());
            case WorkItem.WriteWindowUpdate w -> frameCodec.writeWindowUpdate(w.streamId(), w.increment());
            case WorkItem.WriteSettingsAck s -> frameCodec.writeSettingsAck();
            case WorkItem.WritePing p -> frameCodec.writeFrame(FRAME_TYPE_PING, p.ack() ? FLAG_ACK : 0, 0, p.payload());
            case WorkItem.Shutdown s -> {
            }
            case WorkItem.CheckDataQueue c -> {
            }
        }
    }

    private void processEncodeHeaders(WorkItem.EncodeHeaders req) throws IOException {
        H2Exchange exchange = req.exchange();

        try {
            if (!connectionCallback.isAcceptingStreams()) {
                req.streamIdFuture()
                        .completeExceptionally(new IOException("Connection is not accepting new streams"));
                return;
            }

            int streamId = allocateAndRegisterStream(exchange);

            int tableUpdate = pendingTableSizeUpdate;
            if (tableUpdate >= 0) {
                hpackEncoder.setMaxTableSize(tableUpdate);
                pendingTableSizeUpdate = -1;
            }

            byte[] headerBlock = encodeHeaders(req.request());

            exchange.onHeadersEncoded(req.endStream());

            frameCodec.writeHeaders(streamId, headerBlock, 0, headerBlock.length, req.endStream());

            req.streamIdFuture().complete(streamId);

        } catch (Exception e) {
            int streamId = exchange.getStreamId();
            if (streamId > 0) {
                releaseStream(streamId);
            }
            if (e instanceof IOException || e instanceof H2Exception) {
                req.streamIdFuture().completeExceptionally(e);
            } else {
                req.streamIdFuture().completeExceptionally(new IOException("Encoding failed", e));
            }
        }
    }

    private void processWriteTrailers(WorkItem.WriteTrailers req) throws IOException {
        byte[] headerBlock = encodeTrailers(req.trailers());
        frameCodec.writeHeaders(req.streamId(), headerBlock, 0, headerBlock.length, true);
    }

    private byte[] encodeHeaders(HttpRequest request) throws IOException {
        headerEncodeBuffer.reset();
        hpackEncoder.beginHeaderBlock(headerEncodeBuffer);

        long headerListSize = 0;
        String method = request.method();
        boolean isConnect = "CONNECT".equalsIgnoreCase(method);

        String authority = getAuthority(request);
        String scheme = isConnect ? null : request.uri().getScheme();
        String path = isConnect ? null : getPath(request);

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

        for (var entry : request.headers()) {
            String name = entry.getKey();
            if (CONNECTION_HEADERS.contains(name)) {
                continue;
            }
            boolean isTe = "te".equals(name);
            boolean sensitive = SENSITIVE_HEADERS.contains(name);
            for (String value : entry.getValue()) {
                if (isTe && !"trailers".equalsIgnoreCase(value)) {
                    continue;
                }
                hpackEncoder.encodeHeader(headerEncodeBuffer, name, value, sensitive);
                headerListSize += name.length() + value.length() + 32;
            }
        }

        int maxSize = connectionCallback.getRemoteMaxHeaderListSize();
        if (maxSize != Integer.MAX_VALUE && headerListSize > maxSize) {
            throw new IOException("Header list size (" + headerListSize + ") exceeds limit (" + maxSize + ")");
        }

        return finishHeaderBlock();
    }

    private byte[] encodeTrailers(HttpHeaders trailers) throws IOException {
        headerEncodeBuffer.reset();
        hpackEncoder.beginHeaderBlock(headerEncodeBuffer);

        for (var entry : trailers) {
            String name = entry.getKey();
            if (name.startsWith(":")) {
                throw new IOException("Trailers must not contain pseudo-header: " + name);
            }
            boolean sensitive = SENSITIVE_HEADERS.contains(name);
            for (String value : entry.getValue()) {
                hpackEncoder.encodeHeader(headerEncodeBuffer, name, value, sensitive);
            }
        }

        return finishHeaderBlock();
    }

    private byte[] finishHeaderBlock() {
        ByteBuffer buffer = headerEncodeBuffer.toByteBuffer();
        byte[] result = new byte[buffer.remaining()];
        buffer.get(result);
        return result;
    }

    private String getAuthority(HttpRequest request) {
        String host = request.uri().getHost();
        int port = request.uri().getPort();
        String scheme = request.uri().getScheme();
        if (port == -1 || (port == 443 && "https".equalsIgnoreCase(scheme))
                || (port == 80 && "http".equalsIgnoreCase(scheme))) {
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

    private void completeItem(WorkItem item, IOException error) {
        CompletableFuture<Void> completion = switch (item) {
            case WorkItem.EncodeHeaders h -> h.writeComplete();
            case WorkItem.WriteData d -> d.completion();
            case WorkItem.WriteTrailers t -> t.completion();
            case WorkItem.WriteRst r -> null;
            case WorkItem.WriteGoaway g -> null;
            case WorkItem.WriteWindowUpdate w -> null;
            case WorkItem.WriteSettingsAck s -> null;
            case WorkItem.WritePing p -> null;
            case WorkItem.Shutdown s -> null;
            case WorkItem.CheckDataQueue c -> null;
        };
        if (completion != null) {
            if (error == null) {
                completion.complete(null);
            } else {
                completion.completeExceptionally(error);
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
        WorkItem item;
        while ((item = workQueue.poll()) != null) {
            if (item instanceof WorkItem.EncodeHeaders h) {
                h.streamIdFuture().completeExceptionally(error);
            }
            completeItem(item, error);
        }
    }

    @Override
    public void close() {
        accepting = false;

        long deadline = System.currentTimeMillis() + 1000;
        while (!workQueue.isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        running = false;
        var _ignore = workQueue.offer(new WorkItem.Shutdown());

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
        var _ignore = workQueue.offer(new WorkItem.Shutdown());
        if (workerThread != null) {
            workerThread.interrupt();
        }
        drainAndFailPending(new IOException("Muxer shutting down"));
    }
}
