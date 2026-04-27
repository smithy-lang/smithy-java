/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.hpack.HpackDecoder;
import software.amazon.smithy.java.http.hpack.HpackEncoder;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Benchmark-only H2C transport with a single virtual-thread connection owner and passive per-stream
 * state objects. The connection agent owns all socket I/O and H2 state, while callers block on
 * stream-local response bodies.
 */
final class ConnectionAgentH2cTransport implements AutoCloseable {

    private static final int TARGET_CONNECTION_WINDOW = 16 * 1024 * 1024;
    private static final int WRITE_SCRATCH_SIZE = 256 * 1024;
    private static final ByteBuffer END_OF_STREAM = ByteBuffer.allocate(0);

    private final Thread connectionThread;
    private final Selector selector;
    private final SocketChannel channel;
    private final SelectionKey selectionKey;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    // Connection-thread only state.
    private final HpackEncoder encoder = new HpackEncoder();
    private final HpackDecoder decoder = new HpackDecoder();
    private final ByteBuffer readScratch = ByteBuffer.allocateDirect(1 << 17);
    private final ByteBuffer writeScratch = ByteBuffer.allocateDirect(WRITE_SCRATCH_SIZE);
    private final Map<Integer, StreamState> streams = new HashMap<>();
    private final ArrayDeque<StreamState> unsentBodyStreams = new ArrayDeque<>();
    private int nextStreamId = 1;
    private int sendWindow = ConnectionAgentH2Constants.DEFAULT_INITIAL_WINDOW_SIZE;
    private int recvWindow = TARGET_CONNECTION_WINDOW;
    private int remoteInitialWindow = ConnectionAgentH2Constants.DEFAULT_INITIAL_WINDOW_SIZE;
    private int remoteMaxFrame = ConnectionAgentH2Constants.DEFAULT_MAX_FRAME_SIZE;

    private int frameHdrBytes;
    private final byte[] frameHdrBuf = new byte[ConnectionAgentH2Constants.FRAME_HEADER_SIZE];
    private int curPayloadLen;
    private int curType;
    private int curFlags;
    private int curStreamId;
    private int curPayloadRead;
    private byte[] curStaged;
    private boolean headerParsed;
    private byte[] copyScratch = new byte[8192];

    private static final class StreamState {
        final int streamId;
        final CompletableFuture<HttpResponse> responseFuture;
        final StreamBody body;
        final ConnectionAgentH2StreamState state = new ConnectionAgentH2StreamState();
        int sendWindow;
        ByteBuffer pendingRequestBody;

        StreamState(int streamId, int sendWindow, CompletableFuture<HttpResponse> responseFuture) {
            this.streamId = streamId;
            this.sendWindow = sendWindow;
            this.responseFuture = responseFuture;
            this.body = new StreamBody();
        }
    }

    private static final class StreamBody implements DataStream {
        private final ChunkRing chunks = new ChunkRing();
        private volatile boolean consumed;

        @Override
        public long contentLength() {
            return -1;
        }

        @Override
        public String contentType() {
            return null;
        }

        @Override
        public boolean isReplayable() {
            return false;
        }

        @Override
        public boolean isAvailable() {
            return !consumed;
        }

        @Override
        public InputStream asInputStream() {
            if (consumed) {
                throw new IllegalStateException("Response body is not replayable and has already been consumed");
            }
            consumed = true;
            return new StreamBodyInputStream(chunks);
        }

        @Override
        public void close() {
            chunks.finish();
        }

        void enqueue(byte[] bytes, int length) {
            if (length == 0) {
                return;
            }
            chunks.offer(ByteBuffer.wrap(bytes, 0, length));
        }

        void fail(Throwable throwable) {
            chunks.fail(throwable);
        }

        void complete() {
            chunks.finish();
        }
    }

    private static final class ChunkRing {
        private ByteBuffer[] ring = new ByteBuffer[32];
        private int head;
        private int tail;
        private int size;
        private Throwable failure;
        private boolean finished;

        synchronized void offer(ByteBuffer buffer) {
            if (finished || failure != null) {
                return;
            }
            if (size == ring.length) {
                grow();
            }
            ring[tail] = buffer;
            tail = (tail + 1) & (ring.length - 1);
            size++;
            notifyAll();
        }

        synchronized ByteBuffer take() throws IOException {
            while (size == 0 && failure == null && !finished) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted waiting for response body data", e);
                }
            }
            if (failure != null) {
                if (failure instanceof IOException ioe) {
                    throw ioe;
                }
                throw new IOException("Response body failed", failure);
            }
            if (size == 0) {
                return END_OF_STREAM;
            }
            ByteBuffer result = ring[head];
            ring[head] = null;
            head = (head + 1) & (ring.length - 1);
            size--;
            return result;
        }

        synchronized void finish() {
            finished = true;
            notifyAll();
        }

        synchronized void fail(Throwable throwable) {
            failure = throwable;
            notifyAll();
        }

        private void grow() {
            ByteBuffer[] next = new ByteBuffer[ring.length << 1];
            for (int i = 0; i < size; i++) {
                next[i] = ring[(head + i) & (ring.length - 1)];
            }
            ring = next;
            head = 0;
            tail = size;
        }
    }

    private static final class StreamBodyInputStream extends InputStream {
        private final ChunkRing chunks;
        private ByteBuffer current;
        private boolean eof;

        private StreamBodyInputStream(ChunkRing chunks) {
            this.chunks = chunks;
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int read = read(single, 0, 1);
            return read == -1 ? -1 : single[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (eof) {
                return -1;
            }
            while (current == null || !current.hasRemaining()) {
                current = chunks.take();
                if (current == END_OF_STREAM) {
                    eof = true;
                    return -1;
                }
            }
            int toRead = Math.min(len, current.remaining());
            current.get(b, off, toRead);
            return toRead;
        }
    }

    ConnectionAgentH2cTransport(String host, int port) throws Exception {
        this.selector = Selector.open();
        this.channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
        channel.connect(new InetSocketAddress(host, port));
        while (!channel.finishConnect()) {
            Thread.sleep(1);
        }
        this.selectionKey = channel.register(selector, SelectionKey.OP_READ);
        readScratch.flip();

        var started = new CompletableFuture<Void>();
        this.connectionThread = Thread.startVirtualThread(() -> run(started));
        started.get(10, TimeUnit.SECONDS);
    }

    HttpResponse send(HttpRequest request) throws IOException {
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        byte[] body = extractBody(request.body());
        tasks.offer(() -> startExchange(request, body, future));
        selector.wakeup();
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IOException("Request failed", e);
        }
    }

    private static byte[] extractBody(DataStream body) throws IOException {
        if (body == null || body.contentLength() == 0) {
            return new byte[0];
        }
        if (body.isReplayable() && body.hasKnownLength() && body.contentLength() <= Integer.MAX_VALUE) {
            ByteBuffer buffer = body.asByteBuffer();
            if (!buffer.hasRemaining()) {
                return new byte[0];
            }
            if (buffer.hasArray()) {
                int offset = buffer.arrayOffset() + buffer.position();
                int length = buffer.remaining();
                if (offset == 0 && length == buffer.array().length) {
                    return buffer.array();
                }
                byte[] copy = new byte[length];
                System.arraycopy(buffer.array(), offset, copy, 0, length);
                return copy;
            }
            byte[] copy = new byte[buffer.remaining()];
            buffer.get(copy);
            return copy;
        }
        try (InputStream is = body.asInputStream()) {
            return is.readAllBytes();
        }
    }

    @Override
    public void close() {
        tasks.offer(() -> {
            try {
                selectionKey.cancel();
                channel.close();
                selector.close();
            } catch (IOException ignored) {
            }
        });
        selector.wakeup();
        try {
            connectionThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run(CompletableFuture<Void> started) {
        try {
            writeRaw(
                    ConnectionAgentH2Constants.CONNECTION_PREFACE,
                    0,
                    ConnectionAgentH2Constants.CONNECTION_PREFACE.length);
            buildSettings();
            buildWindowUpdate(
                    0,
                    TARGET_CONNECTION_WINDOW - ConnectionAgentH2Constants.DEFAULT_INITIAL_WINDOW_SIZE);
            flushWriteScratch();
            started.complete(null);

            while (selector.isOpen()) {
                drainTasks();
                flushWriteScratch();

                int interestOps = SelectionKey.OP_READ | (writeScratch.position() > 0 ? SelectionKey.OP_WRITE : 0);
                selectionKey.interestOps(interestOps);
                selector.select(100);

                var keys = selector.selectedKeys();
                boolean anyRead = false;
                for (var key : keys) {
                    if (key.isReadable()) {
                        int n = channel.read(readScratch.compact());
                        readScratch.flip();
                        if (n < 0 && !readScratch.hasRemaining()) {
                            throw new IOException("EOF");
                        }
                        anyRead = n != 0;
                    }
                    if (key.isValid() && key.isWritable()) {
                        flushWriteScratch();
                    }
                }
                keys.clear();
                if (anyRead || readScratch.hasRemaining()) {
                    pumpInbound();
                }
            }
        } catch (Throwable t) {
            if (!started.isDone()) {
                started.completeExceptionally(t);
            }
            for (StreamState stream : streams.values()) {
                stream.body.fail(t);
                stream.responseFuture.completeExceptionally(t);
            }
            streams.clear();
        }
    }

    private void drainTasks() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }

    private void ensureWriteCapacity(int needed) throws IOException {
        if (needed > writeScratch.capacity()) {
            throw new IOException("Frame too large: " + needed);
        }
        while (writeScratch.remaining() < needed) {
            flushWriteScratch();
            if (writeScratch.remaining() < needed) {
                selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                selector.select(100);
                selector.selectedKeys().clear();
            }
        }
    }

    private void writeRaw(byte[] src, int off, int len) throws IOException {
        while (len > 0) {
            if (writeScratch.remaining() == 0) {
                flushWriteScratch();
            }
            int chunk = Math.min(writeScratch.remaining(), len);
            writeScratch.put(src, off, chunk);
            off += chunk;
            len -= chunk;
        }
    }

    private void writeFrameHeader(int payloadLen, int type, int flags, int streamId) {
        writeScratch.put((byte) ((payloadLen >> 16) & 0xFF));
        writeScratch.put((byte) ((payloadLen >> 8) & 0xFF));
        writeScratch.put((byte) (payloadLen & 0xFF));
        writeScratch.put((byte) type);
        writeScratch.put((byte) flags);
        writeScratch.put((byte) ((streamId >> 24) & 0x7F));
        writeScratch.put((byte) ((streamId >> 16) & 0xFF));
        writeScratch.put((byte) ((streamId >> 8) & 0xFF));
        writeScratch.put((byte) (streamId & 0xFF));
    }

    private void buildSettings() throws IOException {
        ensureWriteCapacity(ConnectionAgentH2Constants.FRAME_HEADER_SIZE + 6);
        writeFrameHeader(6, ConnectionAgentH2Constants.FRAME_TYPE_SETTINGS, 0, 0);
        writeScratch.putShort((short) 0x4);
        writeScratch.putInt(16 * 1024 * 1024);
    }

    private void buildSettingsAck() throws IOException {
        ensureWriteCapacity(ConnectionAgentH2Constants.FRAME_HEADER_SIZE);
        writeFrameHeader(
                0,
                ConnectionAgentH2Constants.FRAME_TYPE_SETTINGS,
                ConnectionAgentH2Constants.FLAG_ACK,
                0);
    }

    private void buildWindowUpdate(int streamId, int increment) throws IOException {
        ensureWriteCapacity(ConnectionAgentH2Constants.FRAME_HEADER_SIZE + 4);
        writeFrameHeader(4, ConnectionAgentH2Constants.FRAME_TYPE_WINDOW_UPDATE, 0, streamId);
        writeScratch.putInt(increment);
    }

    private void buildPingAck(byte[] data) throws IOException {
        ensureWriteCapacity(ConnectionAgentH2Constants.FRAME_HEADER_SIZE + 8);
        writeFrameHeader(8, ConnectionAgentH2Constants.FRAME_TYPE_PING, ConnectionAgentH2Constants.FLAG_ACK, 0);
        writeScratch.put(data);
    }

    private void flushWriteScratch() throws IOException {
        if (writeScratch.position() == 0) {
            return;
        }
        writeScratch.flip();
        try {
            while (writeScratch.hasRemaining()) {
                int n = channel.write(writeScratch);
                if (n == 0) {
                    break;
                }
            }
        } finally {
            writeScratch.compact();
        }
    }

    private void startExchange(HttpRequest request, byte[] body, CompletableFuture<HttpResponse> future) {
        try {
            int streamId = nextStreamId;
            nextStreamId += 2;
            var stream = new StreamState(streamId, remoteInitialWindow, future);
            if (body.length > 0) {
                stream.pendingRequestBody = ByteBuffer.wrap(body);
            }
            streams.put(streamId, stream);

            var headerBlock = encodeHeaders(request);
            boolean endStream = body.length == 0;
            stream.state.onHeadersEncoded(endStream);
            int flags = ConnectionAgentH2Constants.FLAG_END_HEADERS
                    | (endStream ? ConnectionAgentH2Constants.FLAG_END_STREAM : 0);
            ensureWriteCapacity(ConnectionAgentH2Constants.FRAME_HEADER_SIZE + headerBlock.length);
            writeFrameHeader(headerBlock.length, ConnectionAgentH2Constants.FRAME_TYPE_HEADERS, flags, streamId);
            writeScratch.put(headerBlock);

            pumpStreamData(stream);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
    }

    private byte[] encodeHeaders(HttpRequest request) throws IOException {
        var out = new java.io.ByteArrayOutputStream(512);
        var uri = request.uri();
        String path = uri.getPath();
        if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            path = path + "?" + uri.getQuery();
        }
        encoder.encodeHeader(out, ConnectionAgentH2Constants.PSEUDO_METHOD, request.method(), false);
        encoder.encodeHeader(out, ConnectionAgentH2Constants.PSEUDO_PATH, path, false);
        encoder.encodeHeader(out, ConnectionAgentH2Constants.PSEUDO_SCHEME, uri.getScheme(), false);
        String authority = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        encoder.encodeHeader(out, ConnectionAgentH2Constants.PSEUDO_AUTHORITY, authority, false);
        for (Map.Entry<String, List<String>> entry : request.headers().map().entrySet()) {
            for (String value : entry.getValue()) {
                encoder.encodeHeader(out, entry.getKey(), value, false);
            }
        }
        return out.toByteArray();
    }

    private void pumpStreamData(StreamState stream) throws IOException {
        if (stream.pendingRequestBody == null || stream.state.isEndStreamSent()) {
            return;
        }
        while (stream.pendingRequestBody.hasRemaining()) {
            int canSend = Math.min(Math.min(stream.sendWindow, sendWindow), remoteMaxFrame);
            if (canSend <= 0) {
                if (!unsentBodyStreams.contains(stream)) {
                    unsentBodyStreams.add(stream);
                }
                return;
            }
            int chunk = Math.min(canSend, stream.pendingRequestBody.remaining());
            boolean end = chunk == stream.pendingRequestBody.remaining();
            ensureWriteCapacity(ConnectionAgentH2Constants.FRAME_HEADER_SIZE + chunk);
            writeFrameHeader(
                    chunk,
                    ConnectionAgentH2Constants.FRAME_TYPE_DATA,
                    end ? ConnectionAgentH2Constants.FLAG_END_STREAM : 0,
                    stream.streamId);
            int oldLimit = stream.pendingRequestBody.limit();
            stream.pendingRequestBody.limit(stream.pendingRequestBody.position() + chunk);
            writeScratch.put(stream.pendingRequestBody);
            stream.pendingRequestBody.limit(oldLimit);
            stream.sendWindow -= chunk;
            sendWindow -= chunk;
            if (end) {
                stream.state.markEndStreamSent();
            }
        }
    }

    private void pumpInbound() throws IOException {
        while (readScratch.hasRemaining()) {
            int before = readScratch.remaining();
            parseFrames();
            if (readScratch.remaining() == before) {
                break;
            }
        }
    }

    private void parseFrames() throws IOException {
        while (readScratch.hasRemaining()) {
            if (!headerParsed) {
                int want = ConnectionAgentH2Constants.FRAME_HEADER_SIZE - frameHdrBytes;
                int take = Math.min(want, readScratch.remaining());
                readScratch.get(frameHdrBuf, frameHdrBytes, take);
                frameHdrBytes += take;
                if (frameHdrBytes < ConnectionAgentH2Constants.FRAME_HEADER_SIZE) {
                    return;
                }
                curPayloadLen = ((frameHdrBuf[0] & 0xFF) << 16)
                        | ((frameHdrBuf[1] & 0xFF) << 8)
                        | (frameHdrBuf[2] & 0xFF);
                curType = frameHdrBuf[3] & 0xFF;
                curFlags = frameHdrBuf[4] & 0xFF;
                curStreamId = ((frameHdrBuf[5] & 0x7F) << 24)
                        | ((frameHdrBuf[6] & 0xFF) << 16)
                        | ((frameHdrBuf[7] & 0xFF) << 8)
                        | (frameHdrBuf[8] & 0xFF);
                ConnectionAgentH2FrameOps.validateStreamId(curType, curStreamId);
                ConnectionAgentH2FrameOps.validateFrameSize(curType, curFlags, curPayloadLen);
                frameHdrBytes = 0;
                headerParsed = true;
                curPayloadRead = 0;
            }

            if (curType == ConnectionAgentH2Constants.FRAME_TYPE_DATA) {
                StreamState stream = streams.get(curStreamId);
                int remaining = curPayloadLen - curPayloadRead;
                int take = Math.min(remaining, readScratch.remaining());
                if (take > 0) {
                    if (stream != null) {
                        byte[] tmp = copyBytes(take);
                        readScratch.get(tmp, 0, take);
                        stream.body.enqueue(tmp, take);
                    } else {
                        readScratch.position(readScratch.position() + take);
                    }
                    curPayloadRead += take;
                }
                if (curPayloadRead == curPayloadLen) {
                    onDataFrameEnd(stream);
                    resetFrameState();
                } else {
                    return;
                }
            } else {
                if (curPayloadLen > 0) {
                    if (curStaged == null || curStaged.length < curPayloadLen) {
                        curStaged = new byte[Math.max(curPayloadLen, 256)];
                    }
                    int remaining = curPayloadLen - curPayloadRead;
                    int take = Math.min(remaining, readScratch.remaining());
                    readScratch.get(curStaged, curPayloadRead, take);
                    curPayloadRead += take;
                    if (curPayloadRead < curPayloadLen) {
                        return;
                    }
                }
                dispatchControlFrame();
                resetFrameState();
            }
        }
    }

    private byte[] copyBytes(int size) {
        if (copyScratch.length < size) {
            copyScratch = new byte[Math.max(size, copyScratch.length * 2)];
        }
        return new byte[size];
    }

    private void resetFrameState() {
        headerParsed = false;
        curPayloadRead = 0;
    }

    private void onDataFrameEnd(StreamState stream) throws IOException {
        recvWindow -= curPayloadLen;
        if (recvWindow < 8 * 1024 * 1024) {
            int increment = 16 * 1024 * 1024 - recvWindow;
            recvWindow += increment;
            buildWindowUpdate(0, increment);
        }
        if (stream != null && (curFlags & ConnectionAgentH2Constants.FLAG_END_STREAM) != 0) {
            stream.state.markEndStreamReceived();
            completeStream(stream);
        }
    }

    private void dispatchControlFrame() throws IOException {
        switch (curType) {
            case ConnectionAgentH2Constants.FRAME_TYPE_SETTINGS -> handleSettings();
            case ConnectionAgentH2Constants.FRAME_TYPE_HEADERS -> handleHeaders();
            case ConnectionAgentH2Constants.FRAME_TYPE_WINDOW_UPDATE -> handleWindowUpdate();
            case ConnectionAgentH2Constants.FRAME_TYPE_RST_STREAM -> handleRst();
            case ConnectionAgentH2Constants.FRAME_TYPE_GOAWAY -> { }
            case ConnectionAgentH2Constants.FRAME_TYPE_PING -> {
                if ((curFlags & ConnectionAgentH2Constants.FLAG_ACK) == 0 && curPayloadLen == 8) {
                    byte[] data = new byte[8];
                    System.arraycopy(curStaged, 0, data, 0, 8);
                    buildPingAck(data);
                }
            }
            default -> { }
        }
    }

    private void handleSettings() throws IOException {
        if ((curFlags & ConnectionAgentH2Constants.FLAG_ACK) != 0) {
            return;
        }
        int i = 0;
        while (i + 6 <= curPayloadLen) {
            int id = ((curStaged[i] & 0xFF) << 8) | (curStaged[i + 1] & 0xFF);
            int value = ((curStaged[i + 2] & 0xFF) << 24)
                    | ((curStaged[i + 3] & 0xFF) << 16)
                    | ((curStaged[i + 4] & 0xFF) << 8)
                    | (curStaged[i + 5] & 0xFF);
            if (id == 0x4) {
                int delta = value - remoteInitialWindow;
                remoteInitialWindow = value;
                for (StreamState stream : streams.values()) {
                    stream.sendWindow += delta;
                }
            } else if (id == 0x5) {
                remoteMaxFrame = value;
            }
            i += 6;
        }
        buildSettingsAck();
    }

    private void handleHeaders() throws IOException {
        StreamState stream = streams.get(curStreamId);
        if (stream == null) {
            return;
        }
        if ((curFlags & ConnectionAgentH2Constants.FLAG_END_HEADERS) == 0) {
            throw new IOException("CONTINUATION unsupported in prototype");
        }
        byte[] headerBlock = decodeScratch(curPayloadLen);
        List<String> fields = decoder.decode(headerBlock);
        for (int i = 0; i < fields.size() - 1; i += 2) {
            if (ConnectionAgentH2Constants.PSEUDO_STATUS.equals(fields.get(i))) {
                stream.state.setResponseHeadersReceived(Integer.parseInt(fields.get(i + 1)));
                break;
            }
        }
        if (!stream.responseFuture.isDone() && stream.state.isResponseHeadersReceived()) {
            startResponse(stream);
        }
        if ((curFlags & ConnectionAgentH2Constants.FLAG_END_STREAM) != 0) {
            stream.state.markEndStreamReceived();
            completeStream(stream);
        }
    }

    private byte[] decodeScratch(int length) {
        byte[] copy = new byte[length];
        System.arraycopy(curStaged, 0, copy, 0, length);
        return copy;
    }

    private void startResponse(StreamState stream) {
        var response = HttpResponse.create()
                .setHttpVersion(HttpVersion.HTTP_2)
                .setStatusCode(stream.state.getStatusCode())
                .setHeaders(HttpHeaders.ofModifiable())
                .setBody(stream.body);
        stream.responseFuture.complete(response);
    }

    private void handleWindowUpdate() throws IOException {
        if (curPayloadLen < 4) {
            return;
        }
        int increment = (((curStaged[0] & 0x7F) << 24)
                | ((curStaged[1] & 0xFF) << 16)
                | ((curStaged[2] & 0xFF) << 8)
                | (curStaged[3] & 0xFF));
        if (curStreamId == 0) {
            sendWindow += increment;
            int size = unsentBodyStreams.size();
            for (int i = 0; i < size; i++) {
                StreamState stream = unsentBodyStreams.poll();
                if (stream != null && !stream.state.isEndStreamSent()) {
                    pumpStreamData(stream);
                }
            }
        } else {
            StreamState stream = streams.get(curStreamId);
            if (stream != null) {
                stream.sendWindow += increment;
                if (!stream.state.isEndStreamSent()) {
                    pumpStreamData(stream);
                }
            }
        }
    }

    private void handleRst() {
        StreamState stream = streams.remove(curStreamId);
        if (stream != null) {
            var error = new IOException("Stream reset by server");
            stream.body.fail(error);
            stream.responseFuture.completeExceptionally(error);
        }
    }

    private void completeStream(StreamState stream) {
        streams.remove(stream.streamId);
        if (!stream.responseFuture.isDone()) {
            startResponse(stream);
        }
        stream.body.complete();
    }
}
