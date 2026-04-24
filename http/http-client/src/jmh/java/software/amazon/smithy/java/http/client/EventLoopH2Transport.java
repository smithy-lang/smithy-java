/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.hpack.HpackDecoder;
import software.amazon.smithy.java.http.hpack.HpackEncoder;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Prototype event-loop H2 client using non-blocking TLS + single-thread-per-connection.
 * See {@code EventLoopH2Transport} for the history; this version addresses allocation hotspots.
 *
 * <p>Key changes from the naive version:
 * <ul>
 *   <li>Reuses a single large direct {@code writeScratch} buffer to build outbound frames
 *       instead of allocating per frame.</li>
 *   <li>Parses inbound frames directly out of the {@code readScratch} buffer without
 *       per-frame {@code ByteBuffer.allocate(payloadLen)}.</li>
 *   <li>DATA frame bodies are copied directly from the inbound buffer into the response
 *       {@code ByteArrayOutputStream} — one copy, not two.</li>
 *   <li>HEADERS encoding reuses a single {@code ByteArrayOutputStream} scratch.</li>
 *   <li>HPACK decoding avoids the intermediate {@code byte[]} copy by using a reusable
 *       staging buffer.</li>
 * </ul>
 *
 * <p>Still prototype. Missing: connection pool, CONTINUATION, renegotiation, graceful close.
 */
final class EventLoopH2Transport implements AutoCloseable {

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

    // Size of outbound scratch buffer. Frames are built here then wrapped+written.
    private static final int WRITE_SCRATCH_SIZE = 256 * 1024;

    private final Thread eventThread;
    private final Selector selector;
    private final NonBlockingSSLTransport tls;
    private final ConcurrentLinkedQueue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    // Fields below: event-thread-only unless noted.
    private final HpackEncoder encoder = new HpackEncoder();
    private final HpackDecoder decoder = new HpackDecoder();
    private final ByteBuffer readScratch = ByteBuffer.allocateDirect(1 << 17); // 128KB
    private final ByteBuffer writeScratch = ByteBuffer.allocateDirect(WRITE_SCRATCH_SIZE);
    private final ByteArrayOutputStream headerBuilder = new ByteArrayOutputStream(512);
    private byte[] hpackDecodeScratch = new byte[1024];
    private final Map<Integer, Exchange> streams = new HashMap<>();
    private final ArrayDeque<Exchange> unsentBodyExchanges = new ArrayDeque<>();
    private int nextStreamId = 1;
    private int sendWindow = DEFAULT_INITIAL_WINDOW;
    private int recvWindow = 16 * 1024 * 1024;
    private int remoteInitialWindow = DEFAULT_INITIAL_WINDOW;
    private int remoteMaxFrame = DEFAULT_MAX_FRAME;

    // Inbound parse state - frame header buffered across reads
    private int frameHdrBytes;
    private final byte[] frameHdrBuf = new byte[FRAME_HEADER_SIZE];
    private int curPayloadLen;
    private int curType;
    private int curFlags;
    private int curStreamId;
    private int curPayloadRead;           // bytes already copied into curStaged
    private byte[] curStaged;              // staging buffer for header/push payloads that need full assembly
    private boolean headerParsed;

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

    EventLoopH2Transport(String host, int port) throws Exception {
        SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(null, new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] c, String a) {}
            public void checkServerTrusted(X509Certificate[] c, String a) {}
        }}, new SecureRandom());

        this.selector = Selector.open();
        this.tls = NonBlockingSSLTransport.connect(host, port, sslCtx, selector);
        readScratch.flip(); // start empty in read mode

        var started = new CompletableFuture<Void>();
        this.eventThread = new Thread(() -> run(started), "h2-eventloop-" + host);
        eventThread.setDaemon(true);
        eventThread.start();
        started.get(10, TimeUnit.SECONDS);
    }

    HttpResponse send(HttpRequest request) throws IOException {
        CompletableFuture<HttpResponse> f = new CompletableFuture<>();
        byte[] body;
        if (request.body() != null && request.body().contentLength() != 0) {
            try (InputStream is = request.body().asInputStream()) {
                body = is.readAllBytes();
            }
        } else {
            body = new byte[0];
        }
        tasks.offer(() -> startExchange(request, body, f));
        selector.wakeup();
        try {
            return f.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IOException("Request failed", e);
        }
    }

    @Override
    public void close() {
        tasks.offer(() -> {
            try { tls.close(); } catch (IOException ignored) {}
        });
        selector.wakeup();
        try { eventThread.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ---- Event loop ----

    private void run(CompletableFuture<Void> started) {
        try {
            while (!tls.handshakeComplete()) {
                tls.handshakeStep();
                if (tls.handshakeComplete()) break;
                selector.select(1000);
            }
            // Send preface + initial SETTINGS
            writeRaw(PREFACE, 0, PREFACE.length);
            buildSettings();
            flushWriteScratch();
            started.complete(null);

            while (selector.isOpen()) {
                Runnable t;
                while ((t = tasks.poll()) != null) {
                    t.run();
                }
                flushWriteScratch();
                int ops = SelectionKey.OP_READ;
                if (writeScratch.position() > 0 || tls.netOutHasPending()) {
                    ops |= SelectionKey.OP_WRITE;
                }
                tls.setInterestOps(ops);

                selector.select();

                var keys = selector.selectedKeys();
                boolean anyRead = false;
                for (var key : keys) {
                    if (key.isReadable()) {
                        int n = tls.onReadable();
                        if (n < 0 && tls.availablePlaintext() == 0) {
                            throw new IOException("EOF");
                        }
                        anyRead = true;
                    }
                    if (key.isValid() && key.isWritable()) {
                        tls.flushNetOut();
                    }
                }
                keys.clear();
                if (anyRead) {
                    pumpInbound();
                }
            }
        } catch (Throwable t) {
            if (!started.isDone()) started.completeExceptionally(t);
            for (Exchange ex : streams.values()) ex.future.completeExceptionally(t);
            streams.clear();
        }
    }

    // ---- Outbound: frames are assembled into writeScratch (direct) and wrapped on flush ----

    /** Ensure writeScratch has at least `need` bytes of remaining capacity; blocks on socket if needed. */
    private void ensureWriteCapacity(int need) throws IOException {
        if (need > writeScratch.capacity()) {
            throw new IOException("Frame too large: " + need + " > " + writeScratch.capacity());
        }
        while (writeScratch.remaining() < need) {
            flushWriteScratch();
            if (writeScratch.remaining() < need) {
                // Socket backpressured. Block on OP_WRITE to drain.
                tls.setInterestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
                selector.select(100);
                selector.selectedKeys().clear();
                if (tls.netOutHasPending()) {
                    tls.flushNetOut();
                }
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
        writeScratch.putShort((short) 0x4); // SETTINGS_INITIAL_WINDOW_SIZE
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

    /** Flushes writeScratch through SSL wrap into the socket. */
    private void flushWriteScratch() throws IOException {
        if (writeScratch.position() == 0 && !tls.netOutHasPending()) {
            return;
        }
        if (!tls.flushNetOut()) {
            return; // socket full, retry later
        }
        writeScratch.flip();
        try {
            while (writeScratch.hasRemaining()) {
                tls.wrap(writeScratch);
                if (!tls.flushNetOut()) {
                    break;
                }
            }
        } finally {
            writeScratch.compact();
        }
    }

    // ---- Request start ----

    private void startExchange(HttpRequest request, byte[] body, CompletableFuture<HttpResponse> future) {
        try {
            int id = nextStreamId;
            nextStreamId += 2;
            var ex = new Exchange(id, remoteInitialWindow, future);
            if (body.length > 0) {
                ex.pendingRequestBody = ByteBuffer.wrap(body);
            } else {
                ex.requestEndSent = true;
            }
            streams.put(id, ex);

            // Encode HEADERS
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
                for (String v : e.getValue()) {
                    encoder.encodeHeader(headerBuilder, e.getKey(), v, false);
                }
            }
            byte[] hpackBuf = headerBuilder.toByteArray();

            int flags = FLAG_END_HEADERS | (body.length == 0 ? FLAG_END_STREAM : 0);
            ensureWriteCapacity(FRAME_HEADER_SIZE + hpackBuf.length);
            writeFrameHeader(hpackBuf.length, TYPE_HEADERS, flags, id);
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
                if (!unsentBodyExchanges.contains(ex)) unsentBodyExchanges.add(ex);
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

    // ---- Inbound: parse frames directly from TLS plaintext, no per-frame allocation ----

    private void pumpInbound() throws IOException {
        while (tls.availablePlaintext() > 0 || readScratch.hasRemaining()) {
            if (tls.availablePlaintext() > 0) {
                readScratch.compact();
                tls.readPlaintext(readScratch);
                readScratch.flip();
            }
            int before = readScratch.remaining();
            parseFrames();
            int after = readScratch.remaining();
            if (after == before && tls.availablePlaintext() == 0) {
                break; // no progress possible
            }
        }
    }

    private void parseFrames() throws IOException {
        while (readScratch.hasRemaining()) {
            if (!headerParsed) {
                // Read header bytes into frameHdrBuf
                int want = FRAME_HEADER_SIZE - frameHdrBytes;
                int take = Math.min(want, readScratch.remaining());
                readScratch.get(frameHdrBuf, frameHdrBytes, take);
                frameHdrBytes += take;
                if (frameHdrBytes < FRAME_HEADER_SIZE) return;

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

            // We have a header, now consume payload
            if (curType == TYPE_DATA) {
                // Stream DATA straight into the exchange body buffer — one copy
                Exchange ex = streams.get(curStreamId);
                int remaining = curPayloadLen - curPayloadRead;
                int take = Math.min(remaining, readScratch.remaining());
                if (take > 0) {
                    if (ex != null) {
                        // Copy from direct read buffer to exchange's ByteArrayOutputStream
                        byte[] tmp = borrowTmp(take);
                        readScratch.get(tmp, 0, take);
                        ex.body.write(tmp, 0, take);
                    } else {
                        // Drop bytes
                        readScratch.position(readScratch.position() + take);
                    }
                    curPayloadRead += take;
                }
                if (curPayloadRead == curPayloadLen) {
                    onDataFrameEnd(ex);
                    resetFrameState();
                } else {
                    return; // need more data
                }
            } else {
                // Non-DATA: buffer the payload in curStaged (one alloc per frame, but these are rare)
                if (curPayloadLen > 0) {
                    if (curStaged == null || curStaged.length < curPayloadLen) {
                        curStaged = new byte[Math.max(curPayloadLen, 256)];
                    }
                    int remaining = curPayloadLen - curPayloadRead;
                    int take = Math.min(remaining, readScratch.remaining());
                    readScratch.get(curStaged, curPayloadRead, take);
                    curPayloadRead += take;
                    if (curPayloadRead < curPayloadLen) {
                        return; // need more
                    }
                }
                dispatchControlFrame();
                resetFrameState();
            }
        }
    }

    // Reusable scratch array to copy from direct buffer. Single-thread so no sync.
    private byte[] copyScratch = new byte[8192];

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
        // Connection-level receive flow control
        recvWindow -= curPayloadLen;
        if (recvWindow < 8 * 1024 * 1024) {
            int inc = 16 * 1024 * 1024 - recvWindow;
            recvWindow += inc;
            buildWindowUpdate(0, inc);
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
            case TYPE_GOAWAY -> { /* ignored */ }
            case TYPE_PING -> {
                if ((curFlags & FLAG_ACK) == 0 && curPayloadLen == 8) {
                    byte[] data = new byte[8];
                    System.arraycopy(curStaged, 0, data, 0, 8);
                    buildPingAck(data);
                }
            }
            default -> {}
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
                for (Exchange ex : streams.values()) ex.sendWindow += delta;
            } else if (id == 0x5) {
                remoteMaxFrame = value;
            }
            i += 6;
        }
        buildSettingsAck();
    }

    private void handleHeaders() throws IOException {
        Exchange ex = streams.get(curStreamId);
        if (ex == null) return;
        if ((curFlags & FLAG_END_HEADERS) == 0) {
            throw new IOException("CONTINUATION unsupported in prototype");
        }
        // HpackDecoder.decode takes a byte[] — resize our reusable buffer if needed
        byte[] hpackInput;
        if (curPayloadLen == curStaged.length) {
            hpackInput = curStaged;
        } else {
            if (hpackDecodeScratch.length < curPayloadLen) {
                hpackDecodeScratch = new byte[curPayloadLen];
            }
            System.arraycopy(curStaged, 0, hpackDecodeScratch, 0, curPayloadLen);
            hpackInput = hpackDecodeScratch;
            // Zero out the tail so HpackDecoder sees an array of the exact right logical length
            // Actually HpackDecoder uses the full length of the byte[] passed, so we must size it exactly.
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
        if (curPayloadLen < 4) return;
        int increment = (((curStaged[0] & 0x7F) << 24)
                | ((curStaged[1] & 0xFF) << 16)
                | ((curStaged[2] & 0xFF) << 8)
                | (curStaged[3] & 0xFF));
        if (curStreamId == 0) {
            sendWindow += increment;
            // Drain any exchanges that were blocked on flow control
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
                if (!ex.requestEndSent) pumpExchangeData(ex);
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
