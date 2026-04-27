/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.Channels;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.http.hpack.HpackDecoder;
import software.amazon.smithy.java.http.hpack.HpackEncoder;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Connection-owner H2C transport that directly reuses the production H2 codec and
 * stream-state internals.
 */
public final class ConnectionAgentH2cTransport implements AutoCloseable {

    private static final int RESPONSE_CANCEL_ERROR = H2Constants.ERROR_CANCEL;
    private static final int REQUEST_STREAM_BUFFER_SIZE = 64 * 1024;
    private static final int TARGET_CONNECTION_WINDOW = 16 * 1024 * 1024;
    private static final int POOLED_DATA_CHUNK_SIZE = 64 * 1024;
    private static final int MAX_POOLED_DATA_CHUNKS = 256;
    private static final ByteBuffer END_OF_STREAM = ByteBuffer.allocate(0);

    private final Route route;
    private final Thread connectionThread;
    private final Selector selector;
    private final SocketChannel channel;
    private final SelectionKey selectionKey;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final ChannelFrameReader reader;
    private final ChannelFrameWriter writer;
    private final H2FrameCodec frameCodec;
    private final HpackDecoder decoder = new HpackDecoder();
    private final HpackEncoder encoder = new HpackEncoder();
    private final AtomicReference<Runnable> streamReleaseCallback = new AtomicReference<>(() -> {});
    private final ConcurrentLinkedDeque<ByteBuffer> inboundBufferPool = new ConcurrentLinkedDeque<>();
    private final AtomicInteger inboundBufferPoolSize = new AtomicInteger();

    private final Map<Integer, StreamState> streams = new HashMap<>();
    private final ArrayDeque<StreamState> unsentBodyStreams = new ArrayDeque<>();
    private volatile boolean interruptibleReadWait;
    private int nextStreamId = 1;
    private int sendWindow = H2Constants.DEFAULT_INITIAL_WINDOW_SIZE;
    private int recvWindow = TARGET_CONNECTION_WINDOW;
    private int remoteInitialWindow = H2Constants.DEFAULT_INITIAL_WINDOW_SIZE;
    private int remoteMaxFrame = H2Constants.DEFAULT_MAX_FRAME_SIZE;
    private volatile int activeStreamCount;
    private volatile boolean active = true;
    private volatile long lastActivityNanos = System.nanoTime();
    private volatile boolean acceptingNewStreams = true;
    private volatile int goawayLastStreamId = Integer.MAX_VALUE;
    private volatile int goawayErrorCode;

    private static final class ReadInterruptedException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private final class SelectorReadableChannel implements ReadableByteChannel {
        @Override
        public int read(ByteBuffer dst) throws IOException {
            while (true) {
                int n = channel.read(dst);
                if (n != 0) {
                    return n;
                }
                if (interruptibleReadWait && !tasks.isEmpty()) {
                    throw new ReadInterruptedException();
                }
                waitFor(SelectionKey.OP_READ);
                if (interruptibleReadWait && !tasks.isEmpty()) {
                    throw new ReadInterruptedException();
                }
            }
        }

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    private final class SelectorWritableChannel implements WritableByteChannel {
        @Override
        public int write(ByteBuffer src) throws IOException {
            while (true) {
                int n = channel.write(src);
                if (n != 0) {
                    return n;
                }
                waitFor(SelectionKey.OP_WRITE);
            }
        }

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }

    private static final class StreamState {
        final int streamId;
        final CompletableFuture<HttpResponse> responseFuture;
        final StreamBody body;
        final H2StreamState state = new H2StreamState();
        final RequestBodySource requestBody;
        int sendWindow;
        HttpHeaders responseHeaders = HttpHeaders.ofModifiable();
        HttpHeaders trailerHeaders = HttpHeaders.ofModifiable();
        long expectedContentLength = -1;
        long receivedContentLength;

        StreamState(
                int streamId,
                int sendWindow,
                CompletableFuture<HttpResponse> responseFuture,
                RequestBodySource requestBody,
                Runnable responseCancelAction) {
            this.streamId = streamId;
            this.sendWindow = sendWindow;
            this.responseFuture = responseFuture;
            this.requestBody = requestBody;
            this.body = new StreamBody(responseCancelAction);
        }
    }

    private sealed interface RequestBodySource extends AutoCloseable permits EmptyRequestBodySource, ByteArrayRequestBodySource, StreamingRequestBodySource {
        boolean isFinished();

        ByteBuffer nextChunk(int maxBytes) throws IOException;

        @Override
        void close() throws IOException;
    }

    private static final class EmptyRequestBodySource implements RequestBodySource {
        static final EmptyRequestBodySource INSTANCE = new EmptyRequestBodySource();

        @Override
        public boolean isFinished() {
            return true;
        }

        @Override
        public ByteBuffer nextChunk(int maxBytes) {
            return null;
        }

        @Override
        public void close() {}
    }

    private static final class ByteArrayRequestBodySource implements RequestBodySource {
        private final ByteBuffer buffer;

        private ByteArrayRequestBodySource(byte[] bytes) {
            this.buffer = ByteBuffer.wrap(bytes);
        }

        @Override
        public boolean isFinished() {
            return !buffer.hasRemaining();
        }

        @Override
        public ByteBuffer nextChunk(int maxBytes) {
            if (!buffer.hasRemaining()) {
                return null;
            }
            int chunk = Math.min(maxBytes, buffer.remaining());
            int oldLimit = buffer.limit();
            buffer.limit(buffer.position() + chunk);
            ByteBuffer slice = buffer.slice();
            buffer.position(buffer.limit());
            buffer.limit(oldLimit);
            return slice;
        }

        @Override
        public void close() {}
    }

    private static final class StreamingRequestBodySource implements RequestBodySource {
        private final ReadableByteChannel channel;
        private final ByteBuffer scratch = ByteBuffer.allocate(REQUEST_STREAM_BUFFER_SIZE);
        private boolean done;
        private boolean closed;

        private StreamingRequestBodySource(ReadableByteChannel channel) {
            this.channel = channel;
        }

        @Override
        public boolean isFinished() {
            return done;
        }

        @Override
        public ByteBuffer nextChunk(int maxBytes) throws IOException {
            if (done) {
                return null;
            }
            scratch.clear();
            scratch.limit(Math.min(maxBytes, scratch.capacity()));
            int read = channel.read(scratch);
            if (read < 0) {
                done = true;
                return null;
            }
            if (read == 0) {
                return ByteBuffer.allocate(0);
            }
            scratch.flip();
            ByteBuffer copy = ByteBuffer.allocate(read);
            copy.put(scratch);
            copy.flip();
            return copy;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            channel.close();
        }
    }

    private static final class StreamBody implements DataStream {
        private final ChunkRing chunks = new ChunkRing();
        private final Runnable responseCancelAction;
        private volatile boolean consumed;
        private volatile boolean closed;
        private volatile boolean completed;

        private StreamBody(Runnable responseCancelAction) {
            this.responseCancelAction = responseCancelAction;
        }

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
            if (closed) {
                return;
            }
            closed = true;
            if (!completed) {
                responseCancelAction.run();
            }
            chunks.finish();
        }

        void enqueue(Chunk chunk) {
            if (chunk == null || !chunk.buffer.hasRemaining()) {
                if (chunk != null) {
                    chunk.release();
                }
                return;
            }
            chunks.offer(chunk);
        }

        void fail(Throwable throwable) {
            chunks.fail(throwable);
        }

        void complete() {
            completed = true;
            chunks.finish();
        }
    }

    private static final class ChunkRing {
        private Chunk[] ring = new Chunk[32];
        private int head;
        private int tail;
        private int size;
        private Throwable failure;
        private boolean finished;

        synchronized void offer(Chunk buffer) {
            if (finished || failure != null) {
                if (buffer != null) {
                    buffer.release();
                }
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

        synchronized Chunk take() throws IOException {
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
                return null;
            }
            Chunk result = ring[head];
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
            clearQueued();
            failure = throwable;
            notifyAll();
        }

        synchronized void close() {
            clearQueued();
            finished = true;
            notifyAll();
        }

        private void clearQueued() {
            for (int i = 0; i < size; i++) {
                Chunk chunk = ring[(head + i) & (ring.length - 1)];
                if (chunk != null) {
                    chunk.release();
                }
            }
            for (int i = 0; i < ring.length; i++) {
                ring[i] = null;
            }
            head = 0;
            tail = 0;
            size = 0;
        }

        private void grow() {
            Chunk[] next = new Chunk[ring.length << 1];
            for (int i = 0; i < size; i++) {
                next[i] = ring[(head + i) & (ring.length - 1)];
            }
            ring = next;
            head = 0;
            tail = size;
        }
    }

    private static final class Chunk {
        final ByteBuffer buffer;
        final Runnable release;

        private Chunk(ByteBuffer buffer, Runnable release) {
            this.buffer = buffer;
            this.release = release;
        }

        void release() {
            release.run();
        }
    }

    private static final class StreamBodyInputStream extends InputStream {
        private final ChunkRing chunks;
        private Chunk current;
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
            while (current == null || !current.buffer.hasRemaining()) {
                releaseCurrent();
                current = chunks.take();
                if (current == null) {
                    eof = true;
                    return -1;
                }
            }
            int toRead = Math.min(len, current.buffer.remaining());
            current.buffer.get(b, off, toRead);
            if (!current.buffer.hasRemaining()) {
                releaseCurrent();
            }
            return toRead;
        }

        @Override
        public long transferTo(OutputStream out) throws IOException {
            long transferred = 0;
            if (eof) {
                return 0;
            }
            while (true) {
                while (current == null || !current.buffer.hasRemaining()) {
                    releaseCurrent();
                    current = chunks.take();
                    if (current == null) {
                        eof = true;
                        return transferred;
                    }
                }
                int remaining = current.buffer.remaining();
                if (current.buffer.hasArray()) {
                    out.write(current.buffer.array(),
                            current.buffer.arrayOffset() + current.buffer.position(),
                            remaining);
                    current.buffer.position(current.buffer.limit());
                } else {
                    byte[] copy = new byte[remaining];
                    current.buffer.get(copy);
                    out.write(copy);
                }
                transferred += remaining;
                releaseCurrent();
            }
        }

        @Override
        public void close() throws IOException {
            eof = true;
            releaseCurrent();
            chunks.close();
        }

        private void releaseCurrent() {
            if (current != null) {
                current.release();
                current = null;
            }
        }
    }

    public ConnectionAgentH2cTransport(Route route) throws Exception {
        if (route.isSecure()) {
            throw new IllegalArgumentException("ConnectionAgentH2cTransport only supports cleartext routes: " + route);
        }
        if (route.usesProxy()) {
            throw new IllegalArgumentException("ConnectionAgentH2cTransport does not support proxies: " + route);
        }
        this.route = route;
        this.selector = Selector.open();
        this.channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.setOption(java.net.StandardSocketOptions.TCP_NODELAY, true);
        channel.connect(new InetSocketAddress(route.host(), route.port()));
        while (!channel.finishConnect()) {
            Thread.sleep(1);
        }
        this.selectionKey = channel.register(selector, SelectionKey.OP_READ);
        this.reader = new ChannelFrameReader(new SelectorReadableChannel(), 1 << 17);
        this.writer = new ChannelFrameWriter(new SelectorWritableChannel(), 256 * 1024);
        this.frameCodec = new H2FrameCodec(reader, writer, H2Constants.MAX_MAX_FRAME_SIZE);

        var started = new CompletableFuture<Void>();
        this.connectionThread = Thread.startVirtualThread(() -> run(started));
        started.get(10, TimeUnit.SECONDS);
    }

    public Route route() {
        return route;
    }

    public HttpResponse send(HttpRequest request) throws IOException {
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        RequestBodySource body = createRequestBodySource(request.body());
        tasks.offer(() -> startExchange(request, body, future));
        selector.wakeup();
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            try {
                body.close();
            } catch (IOException ignored) {
            }
            throw new IOException("Request failed: " + request.method() + " " + request.uri(), e);
        }
    }

    private static RequestBodySource createRequestBodySource(DataStream body) throws IOException {
        if (body == null || body.contentLength() == 0) {
            return EmptyRequestBodySource.INSTANCE;
        }
        if (body.isReplayable() && body.hasKnownLength() && body.contentLength() <= Integer.MAX_VALUE) {
            ByteBuffer buffer = body.asByteBuffer();
            if (!buffer.hasRemaining()) {
                return EmptyRequestBodySource.INSTANCE;
            }
            if (buffer.hasArray()) {
                int offset = buffer.arrayOffset() + buffer.position();
                int length = buffer.remaining();
                if (offset == 0 && length == buffer.array().length) {
                    return new ByteArrayRequestBodySource(buffer.array());
                }
                byte[] copy = new byte[length];
                System.arraycopy(buffer.array(), offset, copy, 0, length);
                return new ByteArrayRequestBodySource(copy);
            }
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return new ByteArrayRequestBodySource(bytes);
        }
        return new StreamingRequestBodySource(body.asChannel());
    }

    @Override
    public void close() {
        active = false;
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

    public int getActiveStreamCountIfAccepting() {
        return active && selector.isOpen() && acceptingNewStreams ? activeStreamCount : -1;
    }

    public boolean canAcceptMoreStreams() {
        return isActive() && acceptingNewStreams;
    }

    public boolean isActive() {
        return active && selector.isOpen() && channel.isOpen();
    }

    public long getIdleTimeNanos() {
        if (activeStreamCount > 0 || !isActive()) {
            return 0;
        }
        return Math.max(0L, System.nanoTime() - lastActivityNanos);
    }

    public void setStreamReleaseCallback(Runnable callback) {
        streamReleaseCallback.set(callback != null ? callback : () -> {});
    }

    private void run(CompletableFuture<Void> started) {
        try {
            frameCodec.writeConnectionPreface();
            frameCodec.writeSettings(H2Constants.SETTINGS_INITIAL_WINDOW_SIZE, 16 * 1024 * 1024);
            frameCodec.writeWindowUpdate(0, TARGET_CONNECTION_WINDOW - H2Constants.DEFAULT_INITIAL_WINDOW_SIZE);
            writer.flush();
            started.complete(null);

            while (selector.isOpen()) {
                drainTasks();
                writer.flush();
                if (reader.hasBufferedData()) {
                    pumpInbound();
                    continue;
                }
                selectionKey.interestOps(SelectionKey.OP_READ);
                selector.select(100);
                boolean readable = selectionKey.isValid() && selectionKey.isReadable();
                selector.selectedKeys().clear();
                if (readable) {
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

    private void waitFor(int interestOps) throws IOException {
        while (selector.isOpen()) {
            selectionKey.interestOps(interestOps);
            selector.select(100);
            boolean ready = selectionKey.isValid()
                    && ((interestOps & SelectionKey.OP_READ) == 0 || selectionKey.isReadable())
                    && ((interestOps & SelectionKey.OP_WRITE) == 0 || selectionKey.isWritable());
            selector.selectedKeys().clear();
            if (ready) {
                return;
            }
        }
        throw new IOException("Connection selector closed");
    }

    private void startExchange(HttpRequest request, RequestBodySource body, CompletableFuture<HttpResponse> future) {
        int streamId = nextStreamId;
        try {
            if (!acceptingNewStreams) {
                throw new IOException("Connection is draining after GOAWAY for " + route);
            }
            markActivity();
            nextStreamId += 2;
            var stream = new StreamState(
                    streamId, remoteInitialWindow, future, body, () -> cancelResponseStream(streamId));
            streams.put(streamId, stream);
            activeStreamCount = streams.size();

            byte[] headers = encodeHeaders(request);
            boolean endStream = body.isFinished();
            stream.state.onHeadersEncoded(endStream);
            frameCodec.writeHeaders(streamId, headers, 0, headers.length, endStream);
            pumpStreamData(stream);
        } catch (Throwable t) {
            try {
                body.close();
            } catch (IOException suppressed) {
                t.addSuppressed(suppressed);
            }
            future.completeExceptionally(t);
            StreamState removed = streams.remove(streamId);
            if (removed != null) {
                activeStreamCount = streams.size();
                onStreamReleased();
            }
        }
    }

    private byte[] encodeHeaders(HttpRequest request) throws IOException {
        var out = new java.io.ByteArrayOutputStream(512);
        var uri = request.uri();
        String path = uri.getPath();
        if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            path = path + "?" + uri.getQuery();
        }
        encoder.encodeHeader(out, H2Constants.PSEUDO_METHOD, request.method(), false);
        encoder.encodeHeader(out, H2Constants.PSEUDO_PATH, path, false);
        encoder.encodeHeader(out, H2Constants.PSEUDO_SCHEME, uri.getScheme(), false);
        String authority = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
        encoder.encodeHeader(out, H2Constants.PSEUDO_AUTHORITY, authority, false);
        for (Map.Entry<String, List<String>> entry : request.headers().map().entrySet()) {
            for (String value : entry.getValue()) {
                encoder.encodeHeader(out, entry.getKey(), value, false);
            }
        }
        return out.toByteArray();
    }

    private void pumpStreamData(StreamState stream) throws IOException {
        if (stream.state.isEndStreamSent()) {
            return;
        }
        while (!stream.requestBody.isFinished()) {
            int canSend = Math.min(Math.min(stream.sendWindow, sendWindow), remoteMaxFrame);
            if (canSend <= 0) {
                if (!unsentBodyStreams.contains(stream)) {
                    unsentBodyStreams.add(stream);
                }
                return;
            }
            ByteBuffer slice = stream.requestBody.nextChunk(canSend);
            if (slice == null) {
                stream.state.markEndStreamSent();
                stream.requestBody.close();
                break;
            }
            if (!slice.hasRemaining()) {
                if (!unsentBodyStreams.contains(stream)) {
                    unsentBodyStreams.add(stream);
                }
                return;
            }
            int chunk = slice.remaining();
            boolean end = stream.requestBody.isFinished();
            frameCodec.writeFrame(H2Constants.FRAME_TYPE_DATA, end ? H2Constants.FLAG_END_STREAM : 0, stream.streamId, slice);
            stream.sendWindow -= chunk;
            sendWindow -= chunk;
            if (end) {
                stream.state.markEndStreamSent();
                stream.requestBody.close();
            }
        }
    }

    private void pumpInbound() throws IOException {
        while (true) {
            int type;
            interruptibleReadWait = true;
            try {
                type = frameCodec.nextFrame();
            } catch (ReadInterruptedException e) {
                return;
            } finally {
                interruptibleReadWait = false;
            }
            if (type < 0) {
                return;
            }
            markActivity();
            switch (type) {
                case H2Constants.FRAME_TYPE_DATA -> handleDataFrame();
                case H2Constants.FRAME_TYPE_HEADERS -> handleHeadersFrame();
                case H2Constants.FRAME_TYPE_SETTINGS -> handleSettingsFrame();
                case H2Constants.FRAME_TYPE_WINDOW_UPDATE -> handleWindowUpdateFrame();
                case H2Constants.FRAME_TYPE_RST_STREAM -> handleRstStreamFrame();
                case H2Constants.FRAME_TYPE_PING -> handlePingFrame();
                case H2Constants.FRAME_TYPE_GOAWAY -> handleGoAwayFrame();
                default -> frameCodec.skipBytes(frameCodec.framePayloadLength());
            }
            if (!frameCodec.hasBufferedData()) {
                return;
            }
        }
    }

    private void markActivity() {
        lastActivityNanos = System.nanoTime();
    }

    private void handleDataFrame() throws IOException {
        int streamId = frameCodec.frameStreamId();
        StreamState stream = streams.get(streamId);
        int payloadLength = frameCodec.framePayloadLength();
        if (stream != null && payloadLength > 0) {
            ByteBuffer data = borrowInboundBuffer(payloadLength);
            frameCodec.readPayloadDirect(data, payloadLength);
            data.flip();
            stream.receivedContentLength += payloadLength;
            stream.body.enqueue(new Chunk(data, releaseInboundBuffer(data)));
        } else if (payloadLength > 0) {
            frameCodec.skipBytes(payloadLength);
        }

        recvWindow -= payloadLength;
        if (recvWindow < 8 * 1024 * 1024) {
            int increment = 16 * 1024 * 1024 - recvWindow;
            recvWindow += increment;
            frameCodec.writeWindowUpdate(0, increment);
        }
        if (stream != null && frameCodec.hasFrameFlag(H2Constants.FLAG_END_STREAM)) {
            stream.state.markEndStreamReceived();
            completeStream(stream);
        }
    }

    private void handleHeadersFrame() throws IOException {
        int streamId = frameCodec.frameStreamId();
        StreamState stream = streams.get(streamId);
        byte[] payload = new byte[frameCodec.framePayloadLength()];
        frameCodec.readPayloadInto(payload, 0, payload.length);
        if (stream == null) {
            return;
        }
        byte[] block = frameCodec.readHeaderBlock(streamId, payload, payload.length);
        int blockLength = block == payload ? payload.length : frameCodec.headerBlockSize();
        List<String> fields = decoder.decode(block, 0, blockLength);
        boolean endStream = frameCodec.hasFrameFlag(H2Constants.FLAG_END_STREAM);
        if (!stream.state.isResponseHeadersReceived()) {
            var result = H2ResponseHeaderProcessor.processResponseHeaders(fields, streamId, endStream);
            if (!result.isInformational()) {
                stream.state.setResponseHeadersReceived(result.statusCode());
                stream.responseHeaders = result.headers();
                stream.expectedContentLength = result.contentLength();
                if (!stream.responseFuture.isDone()) {
                    startResponse(stream);
                }
            }
        } else {
            stream.trailerHeaders = H2ResponseHeaderProcessor.processTrailers(fields, streamId);
        }
        if (endStream) {
            stream.state.markEndStreamReceived();
            completeStream(stream);
        }
    }

    private void handleSettingsFrame() throws IOException {
        if (frameCodec.hasFrameFlag(H2Constants.FLAG_ACK)) {
            return;
        }
        byte[] payload = new byte[frameCodec.framePayloadLength()];
        frameCodec.readPayloadInto(payload, 0, payload.length);
        int[] settings = frameCodec.parseSettings(payload, payload.length);
        for (int i = 0; i < settings.length; i += 2) {
            int id = settings[i];
            int value = settings[i + 1];
            if (id == H2Constants.SETTINGS_INITIAL_WINDOW_SIZE) {
                int delta = value - remoteInitialWindow;
                remoteInitialWindow = value;
                for (StreamState stream : streams.values()) {
                    stream.sendWindow += delta;
                }
            } else if (id == H2Constants.SETTINGS_MAX_FRAME_SIZE) {
                remoteMaxFrame = value;
            }
        }
        frameCodec.writeSettingsAck();
    }

    private void handleWindowUpdateFrame() throws IOException {
        int increment = frameCodec.readAndParseWindowUpdate();
        int streamId = frameCodec.frameStreamId();
        if (streamId == 0) {
            sendWindow += increment;
            int size = unsentBodyStreams.size();
            for (int i = 0; i < size; i++) {
                StreamState stream = unsentBodyStreams.poll();
                if (stream != null && !stream.state.isEndStreamSent()) {
                    pumpStreamData(stream);
                }
            }
        } else {
            StreamState stream = streams.get(streamId);
            if (stream != null) {
                stream.sendWindow += increment;
                if (!stream.state.isEndStreamSent()) {
                    pumpStreamData(stream);
                }
            }
        }
    }

    private void handleRstStreamFrame() throws IOException {
        int errorCode = frameCodec.readAndParseRstStream();
        StreamState stream = streams.remove(frameCodec.frameStreamId());
        if (stream != null) {
            activeStreamCount = streams.size();
            onStreamReleased();
            var error = new IOException("Stream reset by server: " + errorCode);
            try {
                stream.requestBody.close();
            } catch (IOException suppressed) {
                error.addSuppressed(suppressed);
            }
            stream.body.fail(error);
            stream.responseFuture.completeExceptionally(error);
        }
    }

    private void handleGoAwayFrame() throws IOException {
        int payloadLength = frameCodec.framePayloadLength();
        byte[] payload = new byte[payloadLength];
        frameCodec.readPayloadInto(payload, 0, payload.length);
        if (payloadLength < 8) {
            throw new IOException("Invalid GOAWAY payload length: " + payloadLength);
        }
        acceptingNewStreams = false;
        goawayLastStreamId = ((payload[0] & 0x7f) << 24)
                | ((payload[1] & 0xff) << 16)
                | ((payload[2] & 0xff) << 8)
                | (payload[3] & 0xff);
        goawayErrorCode = ((payload[4] & 0xff) << 24)
                | ((payload[5] & 0xff) << 16)
                | ((payload[6] & 0xff) << 8)
                | (payload[7] & 0xff);
        failStreamsAboveGoAway();
    }

    private void handlePingFrame() throws IOException {
        byte[] payload = new byte[8];
        frameCodec.readPayloadInto(payload, 0, payload.length);
        if (!frameCodec.hasFrameFlag(H2Constants.FLAG_ACK)) {
            frameCodec.writeFrame(H2Constants.FRAME_TYPE_PING, H2Constants.FLAG_ACK, 0, payload);
        }
    }

    private void startResponse(StreamState stream) {
        var response = HttpResponse.create()
                .setHttpVersion(HttpVersion.HTTP_2)
                .setStatusCode(stream.state.getStatusCode())
                .setHeaders(stream.responseHeaders)
                .setBody(stream.body);
        stream.responseFuture.complete(response);
    }

    private void completeStream(StreamState stream) throws IOException {
        H2ResponseHeaderProcessor.validateContentLength(
                stream.expectedContentLength, stream.receivedContentLength, stream.streamId);
        streams.remove(stream.streamId);
        activeStreamCount = streams.size();
        onStreamReleased();
        stream.requestBody.close();
        if (!stream.responseFuture.isDone()) {
            startResponse(stream);
        }
        stream.body.complete();
    }

    private void cancelResponseStream(int streamId) {
        tasks.offer(() -> {
            StreamState stream = streams.remove(streamId);
            if (stream == null || stream.state.isEndStreamReceived()) {
                return;
            }
            activeStreamCount = streams.size();
            onStreamReleased();
            stream.state.setStreamStateClosed();
            try {
                stream.requestBody.close();
                frameCodec.writeRstStream(streamId, RESPONSE_CANCEL_ERROR);
            } catch (IOException e) {
                stream.body.fail(e);
                stream.responseFuture.completeExceptionally(e);
                return;
            }
            stream.body.complete();
        });
        selector.wakeup();
    }

    private void failStreamsAboveGoAway() {
        if (goawayLastStreamId == Integer.MAX_VALUE) {
            return;
        }
        var iterator = streams.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            StreamState stream = entry.getValue();
            if (stream.streamId > goawayLastStreamId) {
                iterator.remove();
                activeStreamCount = streams.size();
                onStreamReleased();
                IOException error = new IOException(
                        "Connection received GOAWAY(lastStreamId=" + goawayLastStreamId
                                + ", errorCode=" + goawayErrorCode + ")");
                try {
                    stream.requestBody.close();
                } catch (IOException suppressed) {
                    error.addSuppressed(suppressed);
                }
                stream.body.fail(error);
                stream.responseFuture.completeExceptionally(error);
            }
        }
    }

    private ByteBuffer borrowInboundBuffer(int payloadLength) {
        if (payloadLength > POOLED_DATA_CHUNK_SIZE) {
            return ByteBuffer.allocate(payloadLength);
        }
        ByteBuffer buffer = inboundBufferPool.pollFirst();
        if (buffer == null) {
            buffer = ByteBuffer.allocate(POOLED_DATA_CHUNK_SIZE);
        } else {
            inboundBufferPoolSize.decrementAndGet();
        }
        buffer.clear();
        buffer.limit(payloadLength);
        return buffer;
    }

    private Runnable releaseInboundBuffer(ByteBuffer buffer) {
        if (buffer.capacity() != POOLED_DATA_CHUNK_SIZE) {
            return () -> {};
        }
        return () -> {
            while (true) {
                int current = inboundBufferPoolSize.get();
                if (current >= MAX_POOLED_DATA_CHUNKS) {
                    return;
                }
                if (inboundBufferPoolSize.compareAndSet(current, current + 1)) {
                    break;
                }
            }
            try {
                buffer.clear();
                inboundBufferPool.offerFirst(buffer);
            } catch (RuntimeException e) {
                inboundBufferPoolSize.decrementAndGet();
                throw e;
            }
        };
    }

    private void onStreamReleased() {
        streamReleaseCallback.get().run();
    }
}
