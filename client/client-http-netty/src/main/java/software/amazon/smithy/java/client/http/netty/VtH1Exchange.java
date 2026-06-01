/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.ReferenceCountUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Drives a single HTTP/1.1 request/response over a {@link VtH1Connection}, synchronously on the
 * calling (virtual) thread.
 *
 * <h2>Zero-copy strategy</h2>
 * This path wraps Netty's primitives rather than copying through smithy's intermediate
 * representations:
 * <ul>
 *   <li><b>Request body</b> — a resident ({@code isReplayable()}) body is gathered into a single
 *       {@link ByteBuf} by wrapping its already-resident {@link ByteBuffer}(s) (no byte copy), and
 *       written as one {@link DefaultLastHttpContent}. Handing {@code SslHandler} a single large
 *       buffer lets it slice TLS records out of it instead of coalesce-copying many small writes
 *       (the cost previously misattributed to the HTTP codec). True streaming bodies still batch
 *       through {@link ConnectionBodyOutputStream}.</li>
 *   <li><b>Response headers</b> — wrapped by reference via {@link NettyH1Headers} (no
 *       {@code ArrayHttpHeaders} copy) and attached with {@link
 *       software.amazon.smithy.java.http.api.HttpResponse#of} (no builder round-trip copy).</li>
 *   <li><b>Response body</b> — a small/known-length body is aggregated into one array-backed
 *       {@link DataStream} so the codec deserializer takes its {@code array()} fast path with zero
 *       further copy, and the connection is freed immediately. Large/unknown bodies keep the
 *       streaming {@link ResponseBodyStream} path (backpressure + drain-on-close).</li>
 * </ul>
 */
final class VtH1Exchange {

    private static final int UPLOAD_CHUNK = 64 * 1024;

    /**
     * Responses with a known {@code Content-Length} at or below this size are aggregated into a
     * single array-backed {@link DataStream} (one copy, then zero serde copy and immediate
     * connection reuse). Larger/unknown bodies stream. Mirrors the JDK transport's small-body
     * fast-path threshold; overridable for tuning.
     */
    private static final int RESPONSE_AGGREGATE_THRESHOLD = Integer.getInteger(
            "software.amazon.smithy.java.client.http.netty.responseAggregateThreshold",
            64 * 1024);

    private VtH1Exchange() {}

    /**
     * Execute the request and return the response headers; the response body is attached as a
     * {@link DataStream} whose close callback releases/disposes the connection.
     *
     * @param conn the connection (exclusively owned for the duration of this exchange)
     * @param request the smithy request
     * @param onComplete callback invoked exactly once when the response body is fully consumed or
     *     closed: {@code reuse=true} means the connection is healthy and fully drained and may be
     *     pooled; {@code false} means it must be disposed.
     */
    static software.amazon.smithy.java.http.api.HttpResponse execute(
            VtH1Connection conn,
            HttpRequest request,
            Consumer<Boolean> onComplete
    ) throws IOException {
        boolean hasBody = request.body() != null && request.body().contentLength() != 0;
        long contentLength = hasBody ? request.body().contentLength() : 0;

        var nettyReq = NettyUtils.buildH1Request(
                request,
                HttpVersion.HTTP_1_1,
                HttpMethod.valueOf(request.method()),
                buildRequestLine(request));
        if (hasBody && contentLength > 0) {
            nettyReq.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentLength);
        } else if (hasBody) {
            nettyReq.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        }
        nettyReq.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        conn.write(nettyReq);
        if (hasBody) {
            try (var body = request.body()) {
                writeBody(conn, body, contentLength);
            }
        } else {
            conn.write(LastHttpContent.EMPTY_LAST_CONTENT);
        }
        conn.flushOutboundToSocket();

        // Read the response status line + headers.
        Object first = conn.readInbound();
        if (!(first instanceof HttpResponse nettyResp)) {
            ReferenceCountUtil.release(first);
            conn.close();
            onComplete.accept(false);
            throw new IOException("Expected HTTP response, got " + first);
        }

        boolean keepAlive = HttpUtil.isKeepAlive(nettyResp);
        conn.setKeepAlive(keepAlive);

        int status = nettyResp.status().code();
        var headers = new NettyH1Headers(nettyResp.headers());
        String contentType = nettyResp.headers().get(HttpHeaderNames.CONTENT_TYPE);
        long responseLength = HttpUtil.getContentLength(nettyResp, -1L);

        // If the first object already includes the terminating LastHttpContent (empty-body
        // response, e.g. the S3 PUT 200), short-circuit the streaming machinery entirely.
        if (nettyResp instanceof LastHttpContent last) {
            ReferenceCountUtil.release(last);
            onComplete.accept(keepAlive && conn.isOpen());
            return software.amazon.smithy.java.http.api.HttpResponse.of(
                    software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_1,
                    status,
                    headers,
                    DataStream.ofEmpty());
        }

        DataStream body;
        if (responseLength == 0) {
            // No body to follow; some servers omit a trailing chunk for 204/304-style responses.
            onComplete.accept(keepAlive && conn.isOpen());
            body = DataStream.ofEmpty();
        } else if (responseLength > 0 && responseLength <= RESPONSE_AGGREGATE_THRESHOLD) {
            // Small known-length body: aggregate into one array-backed buffer. The codec
            // deserializer then reads it via array() with no further copy, and the connection is
            // returned to the pool immediately (body lifetime is fully decoupled from the socket).
            byte[] bytes = aggregateBody(conn, (int) responseLength);
            onComplete.accept(keepAlive && conn.isOpen());
            body = DataStream.ofBytes(bytes, contentType);
        } else {
            // Large or unknown-length body: stream it, deferring connection release to body close.
            var bodyStream = new ResponseBodyStream(conn, keepAlive, onComplete);
            body = DataStream.ofInputStream(bodyStream, contentType, responseLength);
        }

        return software.amazon.smithy.java.http.api.HttpResponse.of(
                software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_1,
                status,
                headers,
                body);
    }

    private static String buildRequestLine(HttpRequest request) {
        var uri = request.uri();
        String path = uri.getPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            path = path + "?" + uri.getQuery();
        }
        return path;
    }

    /**
     * Write the request body, then a terminating {@link DefaultLastHttpContent}.
     *
     * <p>Resident (replayable) bodies are handed to the codec as a single {@link ByteBuf}; true
     * streaming bodies fall back to the chunk-batching {@link ConnectionBodyOutputStream}.
     *
     * <h3>Direct staging for tcnative</h3>
     * When the connection's TLS is the tcnative/OpenSSL engine ({@code wantsDirectBuffer=true}),
     * {@code SslHandler.wrap} would otherwise copy heap plaintext into a pooled <em>direct</em>
     * scratch buffer for every 16&nbsp;KiB TLS record before encrypting. Staging the body into one
     * pooled direct buffer up front makes the plaintext already-direct, so {@code wrap} encrypts it
     * in place — trading ~N per-record heap&rarr;direct copies for one contiguous copy and removing
     * the per-record scratch-buffer churn. On cleartext or the JDK engine (copy-free on heap input)
     * we keep the zero-copy heap wrap, since staging there would only add a copy.
     */
    private static void writeBody(VtH1Connection conn, DataStream body, long contentLength) throws IOException {
        boolean stageDirect = conn.usesOpenSslTls();
        if (body.hasByteBuffer()) {
            // Single resident buffer (e.g. a serialized JSON/CBOR payload).
            ByteBuffer src = body.asByteBuffer();
            ByteBuf buf;
            if (stageDirect) {
                buf = conn.channel().alloc().directBuffer(src.remaining());
                try {
                    buf.writeBytes(src.duplicate());
                } catch (RuntimeException e) {
                    buf.release();
                    throw e;
                }
            } else {
                buf = Unpooled.wrappedBuffer(src);
            }
            conn.write(new DefaultLastHttpContent(buf));
        } else if (body.isReplayable()) {
            // Resident but multi-buffer/framed (e.g. aws-chunked SigV4 upload): gather the views
            // emitted by subscribe() into one buffer (direct copy for tcnative, else heap composite).
            ByteBuf buf = gatherResidentBody(conn, body, contentLength, stageDirect);
            conn.write(new DefaultLastHttpContent(buf));
        } else {
            // Non-replayable streaming body: batch through the socket with backpressure.
            var sink = new ConnectionBodyOutputStream(conn);
            body.writeTo(sink);
            sink.finishChunked();
        }
    }

    /**
     * Collect a resident, replayable {@link DataStream}'s bytes into a single {@link ByteBuf}. These
     * streams emit synchronously from {@code subscribe()}, so collection completes inline.
     *
     * <p>When {@code stageDirect} is true and the content length is known, the fragments are copied
     * into one pooled <em>direct</em> buffer sized exactly to the body (the tcnative fast path).
     * Otherwise each emitted {@link ByteBuffer} is wrapped (no copy) into a {@link CompositeByteBuf}.
     */
    private static ByteBuf gatherResidentBody(
            VtH1Connection conn,
            DataStream body,
            long contentLength,
            boolean stageDirect
    ) throws IOException {
        boolean direct = stageDirect && contentLength >= 0;
        ByteBuf target = direct
                ? conn.channel().alloc().directBuffer((int) contentLength)
                : conn.channel().alloc().compositeBuffer();
        var collector = new GatheringSubscriber(target, direct);
        try {
            body.subscribe(collector);
            collector.rethrowIfFailed();
            if (!collector.isComplete()) {
                throw new IOException("Request body publisher did not complete synchronously");
            }
            if (direct && target.readableBytes() != contentLength) {
                throw new IOException("Request body length " + target.readableBytes()
                        + " does not match declared content length " + contentLength);
            }
            ByteBuf result = target;
            target = null;
            return result;
        } finally {
            if (target != null) {
                target.release();
            }
        }
    }

    /**
     * Drain a known-length response body into a single array-backed buffer, releasing each inbound
     * {@link ByteBuf} as it is consumed so connection reuse is independent of body lifetime.
     */
    private static byte[] aggregateBody(VtH1Connection conn, int length) throws IOException {
        byte[] out = new byte[length];
        int off = 0;
        while (true) {
            Object msg = conn.readInbound();
            if (msg == null) {
                throw new IOException("Connection closed before response body completed");
            }
            boolean done = msg instanceof LastHttpContent;
            if (msg instanceof HttpContent content) {
                ByteBuf buf = content.content();
                try {
                    int n = buf.readableBytes();
                    if (n > 0) {
                        if (off + n > out.length) {
                            throw new IOException("Response body exceeds declared Content-Length");
                        }
                        buf.readBytes(out, off, n);
                        off += n;
                    }
                } finally {
                    ReferenceCountUtil.release(content);
                }
            } else {
                ReferenceCountUtil.release(msg);
            }
            if (done) {
                return out;
            }
        }
    }

    /**
     * Synchronous {@link Flow.Subscriber} that collects the {@link ByteBuffer}s a resident
     * {@link DataStream} emits inline from {@code subscribe()} into a single target {@link ByteBuf}.
     *
     * <p>In {@code copyIntoTarget} mode the bytes are copied into a pre-sized (typically pooled
     * direct) buffer — the tcnative fast path. Otherwise each fragment is wrapped without copying and
     * appended to the target {@link CompositeByteBuf}.
     */
    private static final class GatheringSubscriber implements Flow.Subscriber<ByteBuffer> {
        private final ByteBuf target;
        private final boolean copyIntoTarget;
        private boolean complete;
        private Throwable error;

        GatheringSubscriber(ByteBuf target, boolean copyIntoTarget) {
            this.target = target;
            this.copyIntoTarget = copyIntoTarget;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            if (item.hasRemaining()) {
                if (copyIntoTarget) {
                    target.writeBytes(item.duplicate());
                } else {
                    ((CompositeByteBuf) target).addComponent(true, Unpooled.wrappedBuffer(item));
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onComplete() {
            this.complete = true;
        }

        boolean isComplete() {
            return complete;
        }

        void rethrowIfFailed() throws IOException {
            if (error != null) {
                throw new IOException("Failed to read request body", error);
            }
        }
    }

    /**
     * Writes the request body into the connection as {@link HttpContent} chunks, flushing each chunk
     * to the socket. Blocking on the socket write provides backpressure (no sleep-poll). Used only
     * for non-replayable streaming bodies; resident bodies take the single-buffer path.
     */
    private static final class ConnectionBodyOutputStream extends OutputStream {
        private final VtH1Connection conn;
        private ByteBuf current;

        ConnectionBodyOutputStream(VtH1Connection conn) {
            this.conn = conn;
        }

        @Override
        public void write(int b) throws IOException {
            ensureCurrent(1).writeByte(b);
            maybeFlush();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            int remaining = len;
            int pos = off;
            while (remaining > 0) {
                int n = Math.min(remaining, UPLOAD_CHUNK);
                ensureCurrent(n).writeBytes(b, pos, n);
                pos += n;
                remaining -= n;
                maybeFlush();
            }
        }

        private ByteBuf ensureCurrent(int minWritable) {
            if (current == null) {
                current = conn.channel().alloc().buffer(Math.max(UPLOAD_CHUNK, minWritable));
            }
            return current;
        }

        private void maybeFlush() throws IOException {
            if (current != null && !current.isWritable()) {
                conn.write(new DefaultHttpContent(current));
                current = null;
                conn.flushOutboundToSocket();
            }
        }

        void finishChunked() throws IOException {
            if (current != null && current.isReadable()) {
                conn.write(new DefaultHttpContent(current));
                current = null;
            } else if (current != null) {
                current.release();
                current = null;
            }
            conn.write(LastHttpContent.EMPTY_LAST_CONTENT);
        }
    }

    /**
     * Streaming response body. Pulls {@link HttpContent} from the connection on demand and releases
     * the connection (reuse or dispose) when fully consumed or closed.
     */
    private static final class ResponseBodyStream extends InputStream {
        private final VtH1Connection conn;
        private final boolean keepAlive;
        private final Consumer<Boolean> onComplete;
        private ByteBuf current;
        private boolean eos;
        private boolean closed;
        private boolean completedNotified;

        ResponseBodyStream(
                VtH1Connection conn,
                boolean keepAlive,
                Consumer<Boolean> onComplete
        ) {
            this.conn = conn;
            this.keepAlive = keepAlive;
            this.onComplete = onComplete;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (closed) {
                throw new IOException("Stream closed");
            }
            if (len == 0) {
                return 0;
            }
            while (current == null || !current.isReadable()) {
                releaseCurrent();
                if (eos) {
                    notifyComplete(true);
                    return -1;
                }
                Object msg = conn.readInbound();
                if (msg == null) {
                    // EOF before the terminating chunk: body truncated, connection unusable.
                    eos = true;
                    notifyComplete(false);
                    return -1;
                }
                if (msg instanceof HttpContent content) {
                    current = content.content();
                    if (content instanceof LastHttpContent) {
                        eos = true;
                        // A LastHttpContent may carry final bytes; fall through to serve them, then
                        // EOS on the next call.
                        if (!current.isReadable()) {
                            releaseCurrent();
                            notifyComplete(true);
                            return -1;
                        }
                    }
                } else {
                    ReferenceCountUtil.release(msg);
                }
            }
            int toCopy = Math.min(len, current.readableBytes());
            current.readBytes(b, off, toCopy);
            return toCopy;
        }

        /**
         * Drain the whole body straight from the inbound {@link ByteBuf}s into {@code out}.
         *
         * <p>Overrides {@link InputStream#transferTo}, which would otherwise allocate a fresh 16&nbsp;KiB
         * scratch {@code byte[]} on every call and copy through it — on the S3 GET discard path that
         * was ~44% of this transport's download allocation. Writing each {@code ByteBuf} directly to
         * {@code out} via {@link ByteBuf#readBytes(OutputStream, int)} keeps the buffer reuse the
         * pull loop already provides and adds no per-call allocation. The benchmark/SDK
         * {@code discard()} routes here through {@code InputStreamDataStream.discard ->
         * transferTo(nullOutputStream)}.
         */
        @Override
        public long transferTo(OutputStream out) throws IOException {
            if (closed) {
                throw new IOException("Stream closed");
            }
            long transferred = 0;
            while (true) {
                while (current == null || !current.isReadable()) {
                    releaseCurrent();
                    if (eos) {
                        notifyComplete(true);
                        return transferred;
                    }
                    Object msg = conn.readInbound();
                    if (msg == null) {
                        eos = true;
                        notifyComplete(false);
                        return transferred;
                    }
                    if (msg instanceof HttpContent content) {
                        current = content.content();
                        if (content instanceof LastHttpContent) {
                            eos = true;
                            if (!current.isReadable()) {
                                releaseCurrent();
                                notifyComplete(true);
                                return transferred;
                            }
                        }
                    } else {
                        ReferenceCountUtil.release(msg);
                    }
                }
                int n = current.readableBytes();
                current.readBytes(out, n);
                transferred += n;
            }
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                return;
            }
            closed = true;
            // Drain any remaining body so the connection can be reused; bounded by the eos flag.
            boolean reuse = keepAlive;
            try {
                if (!eos) {
                    while (true) {
                        releaseCurrent();
                        Object msg = conn.readInbound();
                        if (msg == null) {
                            reuse = false;
                            break;
                        }
                        boolean done = msg instanceof LastHttpContent;
                        if (msg instanceof HttpContent content) {
                            ReferenceCountUtil.release(content);
                        } else {
                            ReferenceCountUtil.release(msg);
                        }
                        if (done) {
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                reuse = false;
            } finally {
                releaseCurrent();
                notifyComplete(reuse && conn.isOpen());
            }
        }

        private void releaseCurrent() {
            if (current != null) {
                current.release();
                current = null;
            }
        }

        private void notifyComplete(boolean reuse) {
            if (!completedNotified) {
                completedNotified = true;
                onComplete.accept(reuse);
            }
        }
    }
}
