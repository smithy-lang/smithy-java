/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import static software.amazon.smithy.java.http.client.h2.H2Constants.ERROR_REFUSED_STREAM;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FLAG_ACK;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_DATA;
import static software.amazon.smithy.java.http.client.h2.H2Constants.FRAME_TYPE_PING;
import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_AUTHORITY;
import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_METHOD;
import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_PATH;
import static software.amazon.smithy.java.http.client.h2.H2Constants.PSEUDO_SCHEME;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.client.h2.hpack.HpackEncoder;
import software.amazon.smithy.java.io.ByteBufferOutputStream;

/**
 * Combined HPACK encoder and frame writer for HTTP/2 connections.
 *
 * <p>This class handles both header encoding AND frame writing in a single thread,
 * eliminating the handoff overhead between separate encoder and writer threads.
 * All socket writes are serialized through this thread.
 *
 * <h2>Thread Model</h2>
 * <ul>
 *   <li>Caller threads submit work items to the queue</li>
 *   <li>Single thread processes requests: encode (if needed) → write → flush</li>
 *   <li>Batching: drains queue, processes all, flushes once</li>
 * </ul>
 *
 * <h2>Ordering Guarantees</h2>
 * <ul>
 *   <li>Stream IDs are allocated in submission order</li>
 *   <li>HPACK dynamic table updates happen in wire order</li>
 *   <li>Frames are written in submission order</li>
 * </ul>
 */
final class H2StreamWriter implements AutoCloseable {

    /**
     * Work items that can be submitted to the encoder/writer thread.
     */
    sealed interface WorkItem {
        /** Encode headers and write HEADERS frame for a new stream. */
        record EncodeHeaders(
                HttpRequest request,
                H2Exchange exchange,
                boolean endStream,
                CompletableFuture<Integer> streamIdFuture,
                CompletableFuture<Void> writeComplete) implements WorkItem {}

        /** Write a DATA frame. */
        record WriteData(
                int streamId,
                byte[] data,
                int offset,
                int length,
                int flags,
                CompletableFuture<Void> completion) implements WorkItem {}

        /** Write a RST_STREAM frame. */
        record WriteRst(
                int streamId,
                int errorCode,
                CompletableFuture<Void> completion) implements WorkItem {}

        /** Write a GOAWAY frame (fire-and-forget). */
        record WriteGoaway(int lastStreamId, int errorCode, String debugData) implements WorkItem {}

        /** Write a WINDOW_UPDATE frame (fire-and-forget). */
        record WriteWindowUpdate(int streamId, int increment) implements WorkItem {}

        /** Write a SETTINGS ACK frame (fire-and-forget). */
        record WriteSettingsAck() implements WorkItem {}

        /** Write a PING frame (fire-and-forget). */
        record WritePing(byte[] payload, boolean ack) implements WorkItem {}

        /** Shutdown marker. */
        record Shutdown() implements WorkItem {}
    }

    /**
     * Interface for encoder to manage streams on the connection.
     */
    interface StreamManager {
        boolean tryReserveStream();

        void releaseStreamSlot();

        void registerStream(int streamId, H2Exchange exchange);

        void unregisterStream(int streamId);

        void setLastStreamId(int streamId);

        boolean isAcceptingStreams();

        int getRemoteMaxHeaderListSize();
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

    private static final int QUEUE_CAPACITY = 1024;

    private final StreamManager streamManager;
    private final H2FrameCodec frameCodec;
    private final BlockingQueue<WorkItem> workQueue;
    private final Thread workerThread;

    // HPACK encoder state (only accessed by worker thread - no synchronization needed)
    private final HpackEncoder hpackEncoder;
    private final ByteBufferOutputStream headerEncodeBuffer;

    // Stream ID allocation (only accessed by worker thread)
    private final AtomicInteger nextStreamId = new AtomicInteger(1); // Client uses odd IDs

    // Pending table size update (set by connection thread, read by worker thread)
    private volatile int pendingTableSizeUpdate = -1;

    private volatile boolean running = true;
    private volatile boolean accepting = true;

    /**
     * Create a new combined encoder/writer.
     *
     * @param streamManager the stream manager for connection interaction
     * @param frameCodec the frame codec for writing to socket
     * @param initialTableSize initial HPACK dynamic table size
     * @param threadName name for the worker thread
     */
    H2StreamWriter(StreamManager streamManager, H2FrameCodec frameCodec, int initialTableSize, String threadName) {
        this.streamManager = streamManager;
        this.frameCodec = frameCodec;
        this.workQueue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.hpackEncoder = new HpackEncoder(initialTableSize);
        this.headerEncodeBuffer = new ByteBufferOutputStream(512);
        this.workerThread = Thread.ofVirtual().name(threadName).start(this::workerLoop);
    }

    /**
     * Submit a work item with timeout (blocking if queue is full).
     */
    boolean submitWork(WorkItem item, long timeoutMs) {
        if (!accepting) {
            return false;
        }
        try {
            return workQueue.offer(item, timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Submit a control frame (fire-and-forget, non-blocking).
     */
    boolean submitControlFrame(WorkItem item) {
        if (!accepting) {
            return false;
        }
        return workQueue.offer(item);
    }

    /**
     * Update the HPACK encoder's max table size.
     */
    void setMaxTableSize(int newSize) {
        this.pendingTableSizeUpdate = newSize;
    }

    /**
     * Get the current stream ID counter value.
     */
    int getNextStreamId() {
        return nextStreamId.get();
    }

    /**
     * Main worker loop - processes work items with batching.
     */
    private void workerLoop() {
        var batch = new ArrayList<WorkItem>(64);

        try {
            while (running) {
                // Block for the first request
                WorkItem item = workQueue.take();

                if (item instanceof WorkItem.Shutdown) {
                    break;
                }

                batch.add(item);

                // Drain opportunistically (non-blocking) for batching
                while ((item = workQueue.poll()) != null) {
                    if (item instanceof WorkItem.Shutdown) {
                        processBatch(batch);
                        return;
                    }
                    batch.add(item);
                }

                processBatch(batch);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        failRemainingRequests();
    }

    /**
     * Process a batch of work items with a single flush at the end.
     */
    private void processBatch(ArrayList<WorkItem> batch) {
        if (batch.isEmpty()) {
            return;
        }

        try {
            // Process all items (encode if needed, write frames)
            for (WorkItem item : batch) {
                processItem(item);
            }

            // Single flush for entire batch
            frameCodec.flush();

            // Complete futures for successful writes
            for (WorkItem item : batch) {
                completeItem(item, null);
            }
        } catch (IOException e) {
            // Fail all items in batch
            for (WorkItem item : batch) {
                completeItem(item, e);
            }
        } finally {
            batch.clear();
        }
    }

    /**
     * Process a single work item.
     */
    private void processItem(WorkItem item) throws IOException {
        switch (item) {
            case WorkItem.EncodeHeaders h -> processEncodeHeaders(h);
            case WorkItem.WriteData d ->
                frameCodec.writeFrame(FRAME_TYPE_DATA, d.flags(), d.streamId(), d.data(), d.offset(), d.length());
            case WorkItem.WriteRst r ->
                frameCodec.writeRstStream(r.streamId(), r.errorCode());
            case WorkItem.WriteGoaway g ->
                frameCodec.writeGoaway(g.lastStreamId(), g.errorCode(), g.debugData());
            case WorkItem.WriteWindowUpdate w ->
                frameCodec.writeWindowUpdate(w.streamId(), w.increment());
            case WorkItem.WriteSettingsAck s ->
                frameCodec.writeSettingsAck();
            case WorkItem.WritePing p ->
                frameCodec.writeFrame(FRAME_TYPE_PING, p.ack() ? FLAG_ACK : 0, 0, p.payload());
            case WorkItem.Shutdown s -> {
                // handled by caller
            }
        }
    }

    /**
     * Process an encode headers request: allocate stream, encode HPACK, write frame.
     */
    private void processEncodeHeaders(WorkItem.EncodeHeaders req) throws IOException {
        int streamId = -1;
        boolean slotReserved = false;
        boolean streamRegistered = false;

        try {
            if (!streamManager.isAcceptingStreams()) {
                req.streamIdFuture()
                        .completeExceptionally(
                                new IOException("Connection is not accepting new streams"));
                return;
            }

            if (!streamManager.tryReserveStream()) {
                req.streamIdFuture()
                        .completeExceptionally(
                                new IOException("Connection at max concurrent streams"));
                return;
            }
            slotReserved = true;

            streamId = nextStreamId.getAndAdd(2);
            if (streamId < 0) {
                req.streamIdFuture()
                        .completeExceptionally(
                                new H2Exception(ERROR_REFUSED_STREAM, "Stream ID space exhausted"));
                streamManager.releaseStreamSlot();
                return;
            }

            streamManager.setLastStreamId(streamId);
            req.exchange().setStreamId(streamId);
            streamManager.registerStream(streamId, req.exchange());
            streamRegistered = true;
            req.exchange().onHeadersEncoded(req.endStream());

            int tableUpdate = pendingTableSizeUpdate;
            if (tableUpdate >= 0) {
                hpackEncoder.setMaxTableSize(tableUpdate);
                pendingTableSizeUpdate = -1;
            }

            byte[] headerBlock = encodeHeaders(req.request());

            // Write directly - no intermediate queue!
            frameCodec.writeHeaders(streamId, headerBlock, 0, headerBlock.length, req.endStream());

            req.streamIdFuture().complete(streamId);

        } catch (Exception e) {
            if (streamRegistered) {
                // unregisterStream handles both map removal AND count decrement
                streamManager.unregisterStream(streamId);
            } else if (slotReserved) {
                // Slot reserved but stream not registered - just release the slot
                streamManager.releaseStreamSlot();
            }
            if (e instanceof IOException || e instanceof H2Exception) {
                req.streamIdFuture().completeExceptionally(e);
            } else {
                req.streamIdFuture().completeExceptionally(new IOException("Encoding failed", e));
            }
        }
    }

    /**
     * Complete a work item's future.
     */
    private void completeItem(WorkItem item, IOException error) {
        CompletableFuture<Void> completion = switch (item) {
            case WorkItem.EncodeHeaders h -> h.writeComplete();
            case WorkItem.WriteData d -> d.completion();
            case WorkItem.WriteRst r -> r.completion();
            case WorkItem.WriteGoaway g -> null;
            case WorkItem.WriteWindowUpdate w -> null;
            case WorkItem.WriteSettingsAck s -> null;
            case WorkItem.WritePing p -> null;
            case WorkItem.Shutdown s -> null;
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
     * Encode headers using HPACK.
     */
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

        int maxSize = streamManager.getRemoteMaxHeaderListSize();
        if (maxSize != Integer.MAX_VALUE && headerListSize > maxSize) {
            throw new IOException("Header list size (" + headerListSize +
                    ") exceeds limit (" + maxSize + ")");
        }

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

    private void failRemainingRequests() {
        IOException shutdownError = new IOException("Encoder shutting down");
        WorkItem item;
        while ((item = workQueue.poll()) != null) {
            if (item instanceof WorkItem.EncodeHeaders h) {
                h.streamIdFuture().completeExceptionally(shutdownError);
            }
            completeItem(item, shutdownError);
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

        failRemainingRequests();
    }

    void shutdownNow() {
        accepting = false;
        running = false;
        var _ignore = workQueue.offer(new WorkItem.Shutdown());
        if (workerThread != null) {
            workerThread.interrupt();
        }

        failRemainingRequests();
    }
}
