/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.ByteArrayOutputStream;
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
 * Benchmark-only single-loop H2C transport.
 *
 * <p>One event-loop thread owns both reads and writes for a connection so there is no reader/writer
 * thread split. Request bodies and response bodies are fully materialized, which is acceptable for
 * benchmarking but not a production design.
 */
final class EventLoopH2cTransport implements AutoCloseable {

    private static final byte[] PREFACE = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes();
    private static final int FRAME_HEADER_SIZE = 9;
    private static final int TYPE_DATA = 0x0;
    private static final int TYPE_HEADERS = 0x1;
    private static final int TYPE_RST_STREAM = 0x3;
    private static final int TYPE_SETTINGS = 0x4;
    private static final int TYPE_PING = 0x6;
    private static final int TYPE_GOAWAY = 0x7;
    private static final int TYPE_WINDOW_UPDATE = 0x8;
    private static final int FLAG_ACK = 0x1;
    private static final int FLAG_END_STREAM = 0x1;
    private static final int FLAG_END_HEADERS = 0x4;
    private static final int DEFAULT_INITIAL_WINDOW = 65535;
    private static final int DEFAULT_MAX_FRAME = 16384;
    private static final int TARGET_CONNECTION_WINDOW = 16 * 1024 * 1024;
    private static final int WRITE_SCRATCH_SIZE = 256 * 1024;

    private final Thread eventThread;
    private final Selector selector;
    private final SocketChannel channel;
    private final SelectionKey selectionKey;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    // Event-thread only fields.
    private final HpackEncoder encoder = new HpackEncoder();
    private final HpackDecoder decoder = new HpackDecoder();
    private final ByteBuffer readScratch = ByteBuffer.allocateDirect(1 << 17);
    private final ByteBuffer writeScratch = ByteBuffer.allocateDirect(WRITE_SCRATCH_SIZE);
    private final ByteArrayOutputStream headerBuilder = new ByteArrayOutputStream(512);
    private byte[] hpackDecodeScratch = new byte[1024];
    private final Map<Integer, Exchange> streams = new HashMap<>();
    private final ArrayDeque<Exchange> unsentBodyExchanges = new ArrayDeque<>();
    private int nextStreamId = 1;
    private int sendWindow = DEFAULT_INITIAL_WINDOW;
    private int recvWindow = TARGET_CONNECTION_WINDOW;
    private int remoteInitialWindow = DEFAULT_INITIAL_WINDOW;
    private int remoteMaxFrame = DEFAULT_MAX_FRAME;

    private int frameHdrBytes;
    private final byte[] frameHdrBuf = new byte[FRAME_HEADER_SIZE];
    private int curPayloadLen;
    private int curType;
    private int curFlags;
    private int curStreamId;
    private int curPayloadRead;
    private byte[] curStaged;
    private boolean headerParsed;

    private byte[] copyScratch = new byte[8192];

    private static final class Exchange {
        final int streamId;
        final CompletableFuture<HttpResponse> future;
        int sendWindow;
        int status;
        final ByteArrayOutputStream body = new ByteArrayOutputStream();
        ByteBuffer pendingRequestBody;
        boolean requestEndSent;

        Exchange(int streamId, int sendWindow, CompletableFuture<HttpResponse> future) {
            this.streamId = streamId;
            this.sendWindow = sendWindow;
            this.future = future;
        }
    }

    EventLoopH2cTransport(String host, int port) throws Exception {
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
        this.eventThread = new Thread(() -> run(started), "h2c-eventloop-" + host);
        eventThread.setDaemon(true);
        eventThread.start();
        started.get(10, TimeUnit.SECONDS);
    }

    HttpResponse send(HttpRequest request) throws IOException {
        CompletableFuture<HttpResponse> future = new CompletableFuture<>();
        byte[] body;
        if (request.body() != null && request.body().contentLength() != 0) {
            try (InputStream is = request.body().asInputStream()) {
                body = is.readAllBytes();
            }
        } else {
            body = new byte[0];
        }
        tasks.offer(() -> startExchange(request, body, future));
        selector.wakeup();
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IOException("Request failed", e);
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
            eventThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void run(CompletableFuture<Void> started) {
        try {
            writeRaw(PREFACE, 0, PREFACE.length);
            buildSettings();
            buildWindowUpdate(0, TARGET_CONNECTION_WINDOW - DEFAULT_INITIAL_WINDOW);
            flushWriteScratch();
            started.complete(null);

            while (selector.isOpen()) {
                Runnable task;
                while ((task = tasks.poll()) != null) {
                    task.run();
                }

                flushWriteScratch();
                int ops = SelectionKey.OP_READ | (writeScratch.position() > 0 ? SelectionKey.OP_WRITE : 0);
                selectionKey.interestOps(ops);

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
            for (Exchange ex : streams.values()) {
                ex.future.completeExceptionally(t);
            }
            streams.clear();
        }
    }

    private void ensureWriteCapacity(int need) throws IOException {
        if (need > writeScratch.capacity()) {
            throw new IOException("Frame too large: " + need + " > " + writeScratch.capacity());
        }
        while (writeScratch.remaining() < need) {
            flushWriteScratch();
            if (writeScratch.remaining() < need) {
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
        ensureWriteCapacity(FRAME_HEADER_SIZE + 6);
        writeFrameHeader(6, TYPE_SETTINGS, 0, 0);
        writeScratch.putShort((short) 0x4);
        writeScratch.putInt(16 * 1024 * 1024);
    }

    private void buildSettingsAck() throws IOException {
        ensureWriteCapacity(FRAME_HEADER_SIZE);
        writeFrameHeader(0, TYPE_SETTINGS, FLAG_ACK, 0);
    }

    private void buildWindowUpdate(int streamId, int increment) throws IOException {
        ensureWriteCapacity(FRAME_HEADER_SIZE + 4);
        writeFrameHeader(4, TYPE_WINDOW_UPDATE, 0, streamId);
        writeScratch.putInt(increment);
    }

    private void buildPingAck(byte[] data) throws IOException {
        ensureWriteCapacity(FRAME_HEADER_SIZE + 8);
        writeFrameHeader(8, TYPE_PING, FLAG_ACK, 0);
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
            var ex = new Exchange(streamId, remoteInitialWindow, future);
            if (body.length > 0) {
                ex.pendingRequestBody = ByteBuffer.wrap(body);
            } else {
                ex.requestEndSent = true;
            }
            streams.put(streamId, ex);

            headerBuilder.reset();
            var uri = request.uri();
            String path = uri.getPath();
            if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
                path = path + "?" + uri.getQuery();
            }
            encoder.encodeHeader(headerBuilder, ":method", request.method(), false);
            encoder.encodeHeader(headerBuilder, ":path", path, false);
            encoder.encodeHeader(headerBuilder, ":scheme", uri.getScheme(), false);
            String authority = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            encoder.encodeHeader(headerBuilder, ":authority", authority, false);
            for (Map.Entry<String, List<String>> e : request.headers().map().entrySet()) {
                for (String value : e.getValue()) {
                    encoder.encodeHeader(headerBuilder, e.getKey(), value, false);
                }
            }
            byte[] hpackBuf = headerBuilder.toByteArray();

            int flags = FLAG_END_HEADERS | (body.length == 0 ? FLAG_END_STREAM : 0);
            ensureWriteCapacity(FRAME_HEADER_SIZE + hpackBuf.length);
            writeFrameHeader(hpackBuf.length, TYPE_HEADERS, flags, streamId);
            writeScratch.put(hpackBuf);

            pumpExchangeData(ex);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
    }

    private void pumpExchangeData(Exchange ex) throws IOException {
        if (ex.pendingRequestBody == null || ex.requestEndSent) {
            return;
        }
        while (ex.pendingRequestBody.hasRemaining()) {
            int canSend = Math.min(Math.min(ex.sendWindow, sendWindow), remoteMaxFrame);
            if (canSend <= 0) {
                if (!unsentBodyExchanges.contains(ex)) {
                    unsentBodyExchanges.add(ex);
                }
                return;
            }

            int chunk = Math.min(canSend, ex.pendingRequestBody.remaining());
            boolean end = chunk == ex.pendingRequestBody.remaining();
            int flags = end ? FLAG_END_STREAM : 0;
            ensureWriteCapacity(FRAME_HEADER_SIZE + chunk);
            writeFrameHeader(chunk, TYPE_DATA, flags, ex.streamId);
            int oldLimit = ex.pendingRequestBody.limit();
            ex.pendingRequestBody.limit(ex.pendingRequestBody.position() + chunk);
            writeScratch.put(ex.pendingRequestBody);
            ex.pendingRequestBody.limit(oldLimit);

            ex.sendWindow -= chunk;
            sendWindow -= chunk;
            if (end) {
                ex.requestEndSent = true;
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
                int want = FRAME_HEADER_SIZE - frameHdrBytes;
                int take = Math.min(want, readScratch.remaining());
                readScratch.get(frameHdrBuf, frameHdrBytes, take);
                frameHdrBytes += take;
                if (frameHdrBytes < FRAME_HEADER_SIZE) {
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
                frameHdrBytes = 0;
                headerParsed = true;
                curPayloadRead = 0;
            }

            if (curType == TYPE_DATA) {
                Exchange ex = streams.get(curStreamId);
                int remaining = curPayloadLen - curPayloadRead;
                int take = Math.min(remaining, readScratch.remaining());
                if (take > 0) {
                    if (ex != null) {
                        byte[] tmp = borrowTmp(take);
                        readScratch.get(tmp, 0, take);
                        ex.body.write(tmp, 0, take);
                    } else {
                        readScratch.position(readScratch.position() + take);
                    }
                    curPayloadRead += take;
                }
                if (curPayloadRead == curPayloadLen) {
                    onDataFrameEnd(ex);
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

    private byte[] borrowTmp(int size) {
        if (copyScratch.length < size) {
            copyScratch = new byte[Math.max(size, copyScratch.length * 2)];
        }
        return copyScratch;
    }

    private void resetFrameState() {
        headerParsed = false;
        curPayloadRead = 0;
    }

    private void onDataFrameEnd(Exchange ex) throws IOException {
        recvWindow -= curPayloadLen;
        if (recvWindow < 8 * 1024 * 1024) {
            int increment = 16 * 1024 * 1024 - recvWindow;
            recvWindow += increment;
            buildWindowUpdate(0, increment);
        }
        if (ex != null && (curFlags & FLAG_END_STREAM) != 0) {
            completeExchange(ex);
        }
    }

    private void dispatchControlFrame() throws IOException {
        switch (curType) {
            case TYPE_SETTINGS -> handleSettings();
            case TYPE_HEADERS -> handleHeaders();
            case TYPE_WINDOW_UPDATE -> handleWindowUpdate();
            case TYPE_RST_STREAM -> handleRst();
            case TYPE_GOAWAY -> { }
            case TYPE_PING -> {
                if ((curFlags & FLAG_ACK) == 0 && curPayloadLen == 8) {
                    byte[] data = new byte[8];
                    System.arraycopy(curStaged, 0, data, 0, 8);
                    buildPingAck(data);
                }
            }
            default -> { }
        }
    }

    private void handleSettings() throws IOException {
        if ((curFlags & FLAG_ACK) != 0) {
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
                for (Exchange ex : streams.values()) {
                    ex.sendWindow += delta;
                }
            } else if (id == 0x5) {
                remoteMaxFrame = value;
            }
            i += 6;
        }
        buildSettingsAck();
    }

    private void handleHeaders() throws IOException {
        Exchange ex = streams.get(curStreamId);
        if (ex == null) {
            return;
        }
        if ((curFlags & FLAG_END_HEADERS) == 0) {
            throw new IOException("CONTINUATION unsupported in prototype");
        }
        byte[] hpackInput;
        if (curPayloadLen == curStaged.length) {
            hpackInput = curStaged;
        } else {
            if (hpackDecodeScratch.length < curPayloadLen) {
                hpackDecodeScratch = new byte[curPayloadLen];
            }
            System.arraycopy(curStaged, 0, hpackDecodeScratch, 0, curPayloadLen);
            hpackInput = hpackDecodeScratch;
            if (hpackInput.length != curPayloadLen) {
                hpackInput = new byte[curPayloadLen];
                System.arraycopy(curStaged, 0, hpackInput, 0, curPayloadLen);
            }
        }
        List<String> fields = decoder.decode(hpackInput);
        for (int i = 0; i < fields.size() - 1; i += 2) {
            if (":status".equals(fields.get(i))) {
                ex.status = Integer.parseInt(fields.get(i + 1));
                break;
            }
        }
        if ((curFlags & FLAG_END_STREAM) != 0) {
            completeExchange(ex);
        }
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
            int size = unsentBodyExchanges.size();
            for (int k = 0; k < size; k++) {
                Exchange ex = unsentBodyExchanges.poll();
                if (ex != null && !ex.requestEndSent) {
                    pumpExchangeData(ex);
                }
            }
        } else {
            Exchange ex = streams.get(curStreamId);
            if (ex != null) {
                ex.sendWindow += increment;
                if (!ex.requestEndSent) {
                    pumpExchangeData(ex);
                }
            }
        }
    }

    private void handleRst() {
        Exchange ex = streams.remove(curStreamId);
        if (ex != null) {
            ex.future.completeExceptionally(new IOException("Stream reset by server"));
        }
    }

    private void completeExchange(Exchange ex) {
        streams.remove(ex.streamId);
        byte[] body = ex.body.toByteArray();
        var response = HttpResponse.create()
                .setHttpVersion(HttpVersion.HTTP_2)
                .setStatusCode(ex.status)
                .setHeaders(HttpHeaders.ofModifiable())
                .setBody(DataStream.ofBytes(body));
        ex.future.complete(response);
    }
}
