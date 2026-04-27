/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.h2;

import java.io.InputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
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
import java.util.concurrent.atomic.LongAdder;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLSession;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.http.hpack.HpackDecoder;
import software.amazon.smithy.java.http.hpack.HpackEncoder;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Connection-owner HTTP/2 transport over selector-driven {@link SSLEngine} TLS.
 */
final class ConnectionAgentH2Transport implements AutoCloseable {

    private static final int RESPONSE_CANCEL_ERROR = H2Constants.ERROR_CANCEL;
    private static final int REQUEST_STREAM_BUFFER_SIZE = 64 * 1024;
    private static final int TARGET_CONNECTION_WINDOW = 16 * 1024 * 1024;
    private static final int POOLED_DATA_CHUNK_SIZE = 64 * 1024;
    private static final int MAX_POOLED_DATA_CHUNKS = 256;
    private final Route route;
    private final Thread connectionThread;
    private final Selector selector;
    private final SocketChannel channel;
    private final SelectionKey selectionKey;
    private final SSLEngine engine;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final SelectorReadableChannel readableChannel = new SelectorReadableChannel();
    private final SelectorWritableChannel writableChannel = new SelectorWritableChannel();
    private final ChannelFrameReader reader;
    private final ChannelFrameWriter writer;
    private final H2FrameCodec frameCodec;
    private final HpackDecoder decoder = new HpackDecoder();
    private final HpackEncoder encoder = new HpackEncoder();
    private final AtomicReference<Runnable> streamReleaseCallback = new AtomicReference<>(() -> {});
    private final ConcurrentLinkedDeque<ByteBuffer> inboundBufferPool = new ConcurrentLinkedDeque<>();
    private final AtomicInteger inboundBufferPoolSize = new AtomicInteger();
    private final TlsStats stats = new TlsStats();

    private final Map<Integer, StreamState> streams = new HashMap<>();
    private final ArrayDeque<StreamState> unsentBodyStreams = new ArrayDeque<>();
    private int nextStreamId = 1;
    private int sendWindow = H2Constants.DEFAULT_INITIAL_WINDOW_SIZE;
    private int recvWindow = TARGET_CONNECTION_WINDOW;
    private int remoteInitialWindow = H2Constants.DEFAULT_INITIAL_WINDOW_SIZE;
    private int remoteMaxFrame = H2Constants.DEFAULT_MAX_FRAME_SIZE;
    private volatile boolean interruptibleReadWait;
    private volatile int activeStreamCount;
    private volatile boolean active = true;
    private volatile long lastActivityNanos = System.nanoTime();
    private volatile boolean acceptingNewStreams = true;
    private volatile int goawayLastStreamId = Integer.MAX_VALUE;
    private volatile int goawayErrorCode;

    private final class TlsIo {
        private ByteBuffer netIn;
        private ByteBuffer netOut;
        private ByteBuffer appIn;

        private TlsIo() {
            SSLSession session = engine.getSession();
            this.netIn = ByteBuffer.allocateDirect(session.getPacketBufferSize());
            this.netOut = ByteBuffer.allocateDirect(session.getPacketBufferSize());
            this.appIn = ByteBuffer.allocate(session.getApplicationBufferSize());
            this.appIn.flip();
        }

        private void handshake() throws IOException {
            engine.beginHandshake();
            HandshakeStatus hs = engine.getHandshakeStatus();
            while (hs != HandshakeStatus.FINISHED && hs != HandshakeStatus.NOT_HANDSHAKING) {
                switch (hs) {
                    case NEED_WRAP -> hs = handshakeWrap();
                    case NEED_UNWRAP, NEED_UNWRAP_AGAIN -> hs = handshakeUnwrap();
                    case NEED_TASK -> hs = runDelegatedTasks();
                    default -> throw new IOException("Unexpected TLS handshake status: " + hs);
                }
            }
        }

        private HandshakeStatus handshakeWrap() throws IOException {
            netOut.clear();
            SSLEngineResult result = engine.wrap(ByteBuffer.allocate(0), netOut);
            stats.wrapCalls.increment();
            stats.wrapCiphertextBytes.add(result.bytesProduced());
            if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                netOut = ensureCapacity(netOut, engine.getSession().getPacketBufferSize());
                return result.getHandshakeStatus();
            }
            if (result.getStatus() == Status.CLOSED) {
                throw new IOException("TLS engine closed during handshake wrap");
            }
            netOut.flip();
            writeNetOut();
            return result.getHandshakeStatus();
        }

        private HandshakeStatus handshakeUnwrap() throws IOException {
            if (netIn.position() == 0) {
                if (!readIntoNetIn(false)) {
                    throw new IOException("Connection closed during TLS handshake");
                }
            }
            while (true) {
                netIn.flip();
                appIn.clear();
                SSLEngineResult result = engine.unwrap(netIn, appIn);
                netIn.compact();
                appIn.flip();
                switch (result.getStatus()) {
                    case OK -> {
                        return result.getHandshakeStatus();
                    }
                    case BUFFER_UNDERFLOW -> {
                        netIn = ensureCapacity(netIn, engine.getSession().getPacketBufferSize());
                        if (!readIntoNetIn(false)) {
                            throw new IOException("Connection closed during TLS handshake unwrap");
                        }
                    }
                    case BUFFER_OVERFLOW -> {
                        appIn = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
                        appIn.flip();
                    }
                    case CLOSED -> throw new IOException("TLS engine closed during handshake unwrap");
                }
            }
        }

        private HandshakeStatus runDelegatedTasks() {
            Runnable task;
            while ((task = engine.getDelegatedTask()) != null) {
                task.run();
            }
            return engine.getHandshakeStatus();
        }

        private int read(ByteBuffer dst, boolean allowInterrupt) throws IOException {
            if (appIn.hasRemaining()) {
                return drainAppIn(dst);
            }
            while (true) {
                if (netIn.position() == 0) {
                    if (!readIntoNetIn(allowInterrupt)) {
                        return -1;
                    }
                }
                netIn.flip();
                int appBufSize = engine.getSession().getApplicationBufferSize();
                boolean directUnwrap = dst.remaining() >= appBufSize;
                SSLEngineResult result;
                if (directUnwrap) {
                    result = engine.unwrap(netIn, dst);
                    stats.unwrapCalls.increment();
                    stats.unwrapCiphertextBytes.add(result.bytesConsumed());
                    stats.unwrapPlaintextBytes.add(result.bytesProduced());
                    netIn.compact();
                    switch (result.getStatus()) {
                        case OK -> {
                            runDelegatedTasksIfNeeded(result);
                            if (result.bytesProduced() > 0) {
                                return result.bytesProduced();
                            }
                        }
                        case BUFFER_UNDERFLOW -> {
                            netIn = ensureCapacity(netIn, engine.getSession().getPacketBufferSize());
                            if (!readIntoNetIn(allowInterrupt)) {
                                return -1;
                            }
                        }
                        case BUFFER_OVERFLOW -> {
                            directUnwrap = false;
                        }
                        case CLOSED -> {
                            return -1;
                        }
                    }
                    if (directUnwrap) {
                        continue;
                    }
                    netIn.flip();
                }

                appIn.clear();
                result = engine.unwrap(netIn, appIn);
                stats.unwrapCalls.increment();
                stats.unwrapCiphertextBytes.add(result.bytesConsumed());
                stats.unwrapPlaintextBytes.add(result.bytesProduced());
                netIn.compact();
                appIn.flip();
                switch (result.getStatus()) {
                    case OK -> {
                        runDelegatedTasksIfNeeded(result);
                        if (appIn.hasRemaining()) {
                            return drainAppIn(dst);
                        }
                    }
                    case BUFFER_UNDERFLOW -> {
                        netIn = ensureCapacity(netIn, engine.getSession().getPacketBufferSize());
                        if (!readIntoNetIn(allowInterrupt)) {
                            return -1;
                        }
                    }
                    case BUFFER_OVERFLOW -> {
                        appIn = ByteBuffer.allocate(appBufSize);
                        appIn.flip();
                    }
                    case CLOSED -> {
                        return -1;
                    }
                }
            }
        }

        private int write(ByteBuffer src) throws IOException {
            int totalConsumed = 0;
            while (src.hasRemaining()) {
                netOut.clear();
                SSLEngineResult result = engine.wrap(src, netOut);
                stats.wrapCalls.increment();
                stats.wrapPlaintextBytes.add(result.bytesConsumed());
                stats.wrapCiphertextBytes.add(result.bytesProduced());
                totalConsumed += result.bytesConsumed();
                if (result.getStatus() == Status.BUFFER_OVERFLOW) {
                    netOut = ensureCapacity(netOut, engine.getSession().getPacketBufferSize());
                    continue;
                }
                if (result.getStatus() == Status.CLOSED) {
                    throw new IOException("TLS engine closed during write");
                }
                netOut.flip();
                writeNetOut();
                runDelegatedTasksIfNeeded(result);
            }
            return totalConsumed;
        }

        private boolean hasBufferedPlaintext() {
            return appIn.hasRemaining();
        }

        private void close() throws IOException {
            try {
                engine.closeOutbound();
                netOut.clear();
                SSLEngineResult result = engine.wrap(ByteBuffer.allocate(0), netOut);
                stats.wrapCalls.increment();
                stats.wrapCiphertextBytes.add(result.bytesProduced());
                if (result.getStatus() != Status.CLOSED && result.getStatus() != Status.OK) {
                    return;
                }
                netOut.flip();
                writeNetOut();
            } finally {
                channel.close();
                selector.close();
            }
        }

        private int drainAppIn(ByteBuffer dst) {
            int toCopy = Math.min(appIn.remaining(), dst.remaining());
            int oldLimit = appIn.limit();
            appIn.limit(appIn.position() + toCopy);
            dst.put(appIn);
            appIn.limit(oldLimit);
            return toCopy;
        }

        private boolean readIntoNetIn(boolean allowInterrupt) throws IOException {
            if (!netIn.hasRemaining()) {
                netIn = ensureCapacity(netIn, netIn.capacity() * 2);
            }
            while (true) {
                if (allowInterrupt && !tasks.isEmpty()) {
                    throw new ReadInterruptedException();
                }
                int n = channel.read(netIn);
                if (n != 0) {
                    stats.socketReadCalls.increment();
                    if (n > 0) {
                        stats.socketReadBytes.add(n);
                    }
                    return n > 0;
                }
                waitFor(SelectionKey.OP_READ, allowInterrupt);
            }
        }

        private void writeNetOut() throws IOException {
            while (netOut.hasRemaining()) {
                int n = channel.write(netOut);
                if (n == 0) {
                    waitFor(SelectionKey.OP_WRITE, false);
                } else {
                    stats.socketWriteCalls.increment();
                    stats.socketWriteBytes.add(n);
                }
            }
        }

        private void runDelegatedTasksIfNeeded(SSLEngineResult result) {
            if (result.getHandshakeStatus() == HandshakeStatus.NEED_TASK) {
                Runnable task;
                while ((task = engine.getDelegatedTask()) != null) {
                    task.run();
                }
            }
        }
    }

    private final class SelectorReadableChannel implements ReadableByteChannel {
        @Override
        public int read(ByteBuffer dst) throws IOException {
            return tls.read(dst, interruptibleReadWait);
        }

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public void close() throws IOException {
            ConnectionAgentH2Transport.this.close();
        }
    }

    private final class SelectorWritableChannel implements WritableByteChannel {
        @Override
        public int write(ByteBuffer src) throws IOException {
            return tls.write(src);
        }

        @Override
        public boolean isOpen() {
            return channel.isOpen();
        }

        @Override
        public void close() throws IOException {
            ConnectionAgentH2Transport.this.close();
        }
    }

    private static final class ReadInterruptedException extends IOException {
        private static final long serialVersionUID = 1L;
    }

    private static final class TlsStats {
        final LongAdder wrapCalls = new LongAdder();
        final LongAdder wrapPlaintextBytes = new LongAdder();
        final LongAdder wrapCiphertextBytes = new LongAdder();
        final LongAdder unwrapCalls = new LongAdder();
        final LongAdder unwrapCiphertextBytes = new LongAdder();
        final LongAdder unwrapPlaintextBytes = new LongAdder();
        final LongAdder socketWriteCalls = new LongAdder();
        final LongAdder socketWriteBytes = new LongAdder();
        final LongAdder socketReadCalls = new LongAdder();
        final LongAdder socketReadBytes = new LongAdder();

        @Override
        public String toString() {
            long wraps = wrapCalls.sum();
            long wrapPlain = wrapPlaintextBytes.sum();
            long wrapCipher = wrapCiphertextBytes.sum();
            long unwraps = unwrapCalls.sum();
            long unwrapCipher = unwrapCiphertextBytes.sum();
            long unwrapPlain = unwrapPlaintextBytes.sum();
            long writeCalls = socketWriteCalls.sum();
            long writeBytes = socketWriteBytes.sum();
            long readCalls = socketReadCalls.sum();
            long readBytes = socketReadBytes.sum();
            return "TlsStats{"
                    + "wraps=" + wraps
                    + ", wrapPlainBytes=" + wrapPlain
                    + ", wrapCipherBytes=" + wrapCipher
                    + ", avgPlainPerWrap=" + avg(wrapPlain, wraps)
                    + ", avgCipherPerWrap=" + avg(wrapCipher, wraps)
                    + ", unwraps=" + unwraps
                    + ", unwrapCipherBytes=" + unwrapCipher
                    + ", unwrapPlainBytes=" + unwrapPlain
                    + ", avgCipherPerUnwrap=" + avg(unwrapCipher, unwraps)
                    + ", avgPlainPerUnwrap=" + avg(unwrapPlain, unwraps)
                    + ", socketWrites=" + writeCalls
                    + ", socketWriteBytes=" + writeBytes
                    + ", avgBytesPerSocketWrite=" + avg(writeBytes, writeCalls)
                    + ", socketReads=" + readCalls
                    + ", socketReadBytes=" + readBytes
                    + ", avgBytesPerSocketRead=" + avg(readBytes, readCalls)
                    + '}';
        }

        private static long avg(long total, long count) {
            return count == 0 ? 0 : total / count;
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

        private StreamState(
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
        private final ChunkRing queue = new ChunkRing(128);
        private final Runnable responseCancelAction;
        private volatile Throwable failure;
        private volatile boolean completed;
        private volatile boolean closed;
        private volatile boolean consumed;

        private StreamBody(Runnable responseCancelAction) {
            this.responseCancelAction = responseCancelAction;
        }

        @Override
        public long contentLength() {
            return -1;
        }

        @Override
        public String contentType() {
            return "application/octet-stream";
        }

        @Override
        public boolean isReplayable() {
            return false;
        }

        @Override
        public boolean isAvailable() {
            return !consumed || !closed;
        }

        @Override
        public InputStream asInputStream() {
            consumed = true;
            return new StreamBodyInputStream(this);
        }

        @Override
        public ReadableByteChannel asChannel() {
            return java.nio.channels.Channels.newChannel(asInputStream());
        }

        private void enqueue(Chunk chunk) {
            queue.offer(chunk);
        }

        private void complete() {
            completed = true;
            queue.offer(Chunk.EOF);
        }

        private void fail(Throwable t) {
            failure = t;
            completed = true;
            queue.offer(Chunk.EOF);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            queue.close();
            if (!completed) {
                responseCancelAction.run();
            }
        }
    }

    private static final class StreamBodyInputStream extends InputStream {
        private final StreamBody body;
        private final byte[] transferBuffer = new byte[64 * 1024];
        private Chunk current;

        private StreamBodyInputStream(StreamBody body) {
            this.body = body;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            while (true) {
                if (current != null && current.buffer.hasRemaining()) {
                    int n = Math.min(len, current.buffer.remaining());
                    current.buffer.get(b, off, n);
                    if (!current.buffer.hasRemaining()) {
                        releaseCurrent();
                    }
                    return n;
                }
                releaseCurrent();
                current = body.queue.take();
                if (current == Chunk.EOF) {
                    Throwable failure = body.failure;
                    if (failure != null) {
                        if (failure instanceof IOException ioe) {
                            throw ioe;
                        }
                        throw new IOException("Response body failed", failure);
                    }
                    return -1;
                }
            }
        }

        @Override
        public long transferTo(java.io.OutputStream out) throws IOException {
            long transferred = 0;
            while (true) {
                if (current != null && current.buffer.hasRemaining()) {
                    int remaining = current.buffer.remaining();
                    if (current.buffer.hasArray()) {
                        int offset = current.buffer.arrayOffset() + current.buffer.position();
                        out.write(current.buffer.array(), offset, remaining);
                        current.buffer.position(current.buffer.limit());
                    } else {
                        int chunk = Math.min(remaining, transferBuffer.length);
                        current.buffer.get(transferBuffer, 0, chunk);
                        out.write(transferBuffer, 0, chunk);
                    }
                    transferred += remaining;
                    releaseCurrent();
                    continue;
                }
                releaseCurrent();
                current = body.queue.take();
                if (current == Chunk.EOF) {
                    Throwable failure = body.failure;
                    if (failure != null) {
                        if (failure instanceof IOException ioe) {
                            throw ioe;
                        }
                        throw new IOException("Response body failed", failure);
                    }
                    return transferred;
                }
            }
        }

        @Override
        public void close() throws IOException {
            releaseCurrent();
            body.close();
        }

        private void releaseCurrent() {
            if (current != null) {
                current.release();
                current = null;
            }
        }
    }

    private static final class ChunkRing {
        private final Chunk[] elements;
        private int head;
        private int tail;
        private int size;
        private boolean closed;

        private ChunkRing(int capacity) {
            this.elements = new Chunk[capacity];
        }

        private synchronized void offer(Chunk chunk) {
            while (!closed && size == elements.length) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for response queue space", e);
                }
            }
            if (closed) {
                if (chunk != null && chunk != Chunk.EOF) {
                    chunk.release();
                }
                return;
            }
            elements[tail] = chunk;
            tail = (tail + 1) % elements.length;
            size++;
            notifyAll();
        }

        private synchronized Chunk take() throws IOException {
            while (size == 0) {
                if (closed) {
                    return Chunk.EOF;
                }
                try {
                    wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for response data", e);
                }
            }
            Chunk chunk = elements[head];
            elements[head] = null;
            head = (head + 1) % elements.length;
            size--;
            notifyAll();
            return chunk;
        }

        private synchronized void close() {
            closed = true;
            while (size > 0) {
                Chunk chunk = elements[head];
                elements[head] = null;
                head = (head + 1) % elements.length;
                size--;
                if (chunk != null && chunk != Chunk.EOF) {
                    chunk.release();
                }
            }
            notifyAll();
        }
    }

    private static final class Chunk {
        static final Chunk EOF = new Chunk(ByteBuffer.allocate(0), () -> {});

        final ByteBuffer buffer;
        final Runnable release;

        private Chunk(ByteBuffer buffer, Runnable release) {
            this.buffer = buffer;
            this.release = release;
        }

        private void release() {
            release.run();
        }
    }

    private final TlsIo tls;

    ConnectionAgentH2Transport(Route route, Socket socket, SSLEngine engine) throws Exception {
        this.route = route;
        this.engine = engine;
        this.selector = Selector.open();
        this.channel = socket.getChannel();
        if (channel == null) {
            throw new IllegalArgumentException("ConnectionAgentH2Transport requires a SocketChannel-backed socket");
        }
        channel.configureBlocking(false);
        this.selectionKey = channel.register(selector, SelectionKey.OP_READ);
        this.tls = new TlsIo();
        tls.handshake();
        this.reader = new ChannelFrameReader(readableChannel, 1 << 17, tls::hasBufferedPlaintext);
        this.writer = new ChannelFrameWriter(writableChannel, 256 * 1024);
        this.frameCodec = new H2FrameCodec(reader, writer, H2Constants.MAX_MAX_FRAME_SIZE);

        var started = new CompletableFuture<Void>();
        this.connectionThread = Thread.startVirtualThread(() -> run(started));
        started.get(10, TimeUnit.SECONDS);
    }

    HttpResponse send(HttpRequest request) throws IOException {
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

    int getActiveStreamCountIfAccepting() {
        return active && channel.isOpen() && acceptingNewStreams ? activeStreamCount : -1;
    }

    boolean canAcceptMoreStreams() {
        return isActive() && acceptingNewStreams;
    }

    boolean isActive() {
        return active && channel.isOpen();
    }

    long getIdleTimeNanos() {
        if (activeStreamCount > 0 || !isActive()) {
            return 0;
        }
        return Math.max(0L, System.nanoTime() - lastActivityNanos);
    }

    void setStreamReleaseCallback(Runnable callback) {
        streamReleaseCallback.set(callback != null ? callback : () -> {});
    }

    SSLSession sslSession() {
        return engine.getSession();
    }

    String negotiatedProtocol() {
        String protocol = engine.getApplicationProtocol();
        return protocol != null ? protocol : "h2";
    }

    TlsStats getStats() {
        return stats;
    }

    @Override
    public void close() throws IOException {
        if (!active) {
            return;
        }
        active = false;
        tls.close();
        try {
            connectionThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run(CompletableFuture<Void> started) {
        try {
            frameCodec.writeConnectionPreface();
            frameCodec.writeSettings(H2Constants.SETTINGS_INITIAL_WINDOW_SIZE, 16 * 1024 * 1024);
            frameCodec.writeWindowUpdate(0, TARGET_CONNECTION_WINDOW - H2Constants.DEFAULT_INITIAL_WINDOW_SIZE);
            writer.flush();
            started.complete(null);

            while (active && channel.isOpen()) {
                drainTasks();
                writer.flush();
                try {
                    pumpInbound();
                } catch (ReadInterruptedException ignored) {
                    // Wakeup from queued work.
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
            if (!frameCodec.hasBufferedData() && !tls.hasBufferedPlaintext()) {
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

    private void waitFor(int interestOps, boolean allowInterrupt) throws IOException {
        while (selector.isOpen()) {
            selectionKey.interestOps(interestOps);
            if (allowInterrupt && !tasks.isEmpty()) {
                throw new ReadInterruptedException();
            }
            selector.select();
            boolean ready = selectionKey.isValid()
                    && ((interestOps & SelectionKey.OP_READ) == 0 || selectionKey.isReadable())
                    && ((interestOps & SelectionKey.OP_WRITE) == 0 || selectionKey.isWritable());
            selector.selectedKeys().clear();
            if (allowInterrupt && !tasks.isEmpty()) {
                throw new ReadInterruptedException();
            }
            if (ready) {
                return;
            }
        }
        throw new IOException("TLS selector closed");
    }

    private static ByteBuffer ensureCapacity(ByteBuffer buf, int minCapacity) {
        if (buf.capacity() >= minCapacity) {
            return buf;
        }
        ByteBuffer newBuf = buf.isDirect()
                ? ByteBuffer.allocateDirect(minCapacity)
                : ByteBuffer.allocate(minCapacity);
        buf.flip();
        newBuf.put(buf);
        return newBuf;
    }
}
