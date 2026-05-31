/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
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
import java.util.function.Consumer;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Drives a single HTTP/1.1 request/response over a {@link VtH1Connection}, synchronously on the
 * calling (virtual) thread.
 *
 * <p>Flow: write request line + headers, stream the request body straight through
 * {@link DataStream#writeTo(OutputStream)} (one pass, no materialization — see
 * {@link H1Executor} for the rationale), then read the status line, headers, and body. The response
 * body is returned as a lazily-consumed {@link InputStream}; closing it drains any remainder and
 * hands the connection back to the pool (or disposes it when the connection cannot be reused).
 */
final class VtH1Exchange {

    private static final int UPLOAD_CHUNK = 64 * 1024;

    private VtH1Exchange() {}

    /**
     * Execute the request and return the response headers; the response body is attached as a
     * streaming {@link DataStream} whose close callback releases/disposes the connection.
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

        var nettyReq = new DefaultHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.valueOf(request.method()),
                buildRequestLine(request));
        NettyUtils.fillH1Headers(request, nettyReq.headers());
        if (hasBody && contentLength > 0) {
            nettyReq.headers().set(HttpHeaderNames.CONTENT_LENGTH, contentLength);
        } else if (hasBody) {
            nettyReq.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        }
        nettyReq.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        // Write headers, then stream the body (each flush blocks on the socket = natural backpressure).
        conn.write(nettyReq);
        if (hasBody) {
            var sink = new ConnectionBodyOutputStream(conn);
            try (var body = request.body()) {
                body.writeTo(sink);
                sink.finishChunked();
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

        var smithyResponse = software.amazon.smithy.java.http.api.HttpResponse.create()
                .setHttpVersion(software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_1)
                .setStatusCode(nettyResp.status().code())
                .setHeaders(NettyUtils.fromH1Headers(nettyResp.headers()))
                .setBody(DataStream.ofEmpty());

        // If the first object already includes the terminating LastHttpContent (empty-body
        // response, e.g. the S3 PUT 200), short-circuit the streaming machinery entirely.
        if (nettyResp instanceof LastHttpContent last) {
            ReferenceCountUtil.release(last);
            onComplete.accept(keepAlive && conn.isOpen());
            return smithyResponse.toModifiable().setBody(DataStream.ofEmpty()).toUnmodifiable();
        }

        var bodyStream = new ResponseBodyStream(conn, keepAlive, onComplete);
        return smithyResponse.toModifiable()
                .setBody(DataStream.ofInputStream(bodyStream))
                .toUnmodifiable();
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
     * Writes the request body into the connection as {@link HttpContent} chunks, flushing each chunk
     * to the socket. Blocking on the socket write provides backpressure (no sleep-poll). The
     * HttpClientCodec applies chunked transfer-encoding framing when the request used it.
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
                    byte[] scratch = new byte[UPLOAD_CHUNK];
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
