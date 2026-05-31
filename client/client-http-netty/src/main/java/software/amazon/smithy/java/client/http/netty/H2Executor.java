/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2StreamChannel;
import io.netty.handler.codec.http2.Http2StreamChannelBootstrap;
import io.netty.handler.codec.http2.Http2StreamFrame;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Executes an HTTP/2 request on a multiplexed connection using a fresh stream channel.
 *
 * <p>The response body is delivered through a {@link ResponseBodyChannel} with a single-slot
 * inline handoff path: when the caller VT is parked in {@code read}, the event loop copies
 * DATA-frame bytes directly into the caller's buffer, bypassing a queue and the ByteBuf→byte[]
 * copy. Falls back to an unbounded deque when the consumer isn't parked; backpressure is
 * applied by toggling the stream channel's autoRead when the deque depth crosses watermarks.
 */
final class H2Executor {

    private static final int UPLOAD_CHUNK = 64 * 1024;
    private static final int UPLOAD_BATCH_CHUNKS = 4;
    private static final int BODY_HIGH_WATER = 32;
    private static final int BODY_LOW_WATER = 8;

    private H2Executor() {}

    static HttpResponse execute(Channel parent, HttpRequest request, long requestTimeoutMs) throws IOException {
        Http2StreamChannel stream;
        try {
            stream = new Http2StreamChannelBootstrap(parent).open().sync().getNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted opening H2 stream", e);
        }

        var headersFuture = new CompletableFuture<HttpResponse>();
        var error = new AtomicReference<Throwable>();
        var bodyChannel = new ResponseBodyChannel(
                error,
                resume -> stream.eventLoop().execute(() -> stream.config().setAutoRead(resume)),
                stream::close,
                BODY_HIGH_WATER,
                BODY_LOW_WATER);
        var handler = new ResponseHandler(headersFuture, bodyChannel, error);
        stream.pipeline().addLast(handler);

        var nettyHeaders = NettyUtils.toH2Headers(request);
        boolean hasBody = request.body() != null && request.body().contentLength() != 0;

        stream.eventLoop().execute(() -> {
            stream.write(new DefaultHttp2HeadersFrame(nettyHeaders, !hasBody));
            if (!hasBody) {
                stream.flush();
            }
        });

        if (hasBody) {
            try {
                streamRequestBody(stream, request.body());
            } catch (IOException e) {
                stream.close();
                throw e;
            } finally {
                request.body().close();
            }
        }

        HttpResponse headResponse;
        try {
            headResponse = requestTimeoutMs > 0
                    ? headersFuture.get(requestTimeoutMs, TimeUnit.MILLISECONDS)
                    : headersFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stream.close();
            throw new IOException("Interrupted waiting for H2 response headers", e);
        } catch (ExecutionException e) {
            stream.close();
            Throwable cause = e.getCause();
            if (cause instanceof IOException io)
                throw io;
            throw new IOException("H2 request failed", cause);
        } catch (TimeoutException e) {
            stream.close();
            throw new IOException("Request timed out waiting for H2 headers", e);
        }

        return headResponse.toModifiable()
                .setBody(DataStream.ofInputStream(bodyChannel))
                .toUnmodifiable();
    }

    private static void streamRequestBody(Http2StreamChannel stream, DataStream body) throws IOException {
        // Stream straight through DataStream.writeTo(OutputStream) — one pass, no intermediate
        // materialization. See H1Executor.streamRequestBody for why the old asChannel()/asInputStream()
        // probe double-materialized in-memory bodies.
        var sink = new StreamBatchingOutputStream(stream);
        try {
            body.writeTo(sink);
            sink.finish();
        } catch (IOException | RuntimeException e) {
            sink.discard();
            throw e;
        }
    }

    /**
     * An {@link OutputStream} that batches written bytes into {@link ByteBuf} chunks, hands them to
     * the H2 stream's event loop as DATA frames, and applies writability backpressure between
     * batches. Buffers handed to the event loop are owned by it; buffers still held here are
     * released on {@link #discard()}.
     */
    private static final class StreamBatchingOutputStream extends OutputStream {
        private final Http2StreamChannel stream;
        private final List<ByteBuf> batch = new ArrayList<>(UPLOAD_BATCH_CHUNKS);
        private ByteBuf current;

        StreamBatchingOutputStream(Http2StreamChannel stream) {
            this.stream = stream;
        }

        @Override
        public void write(int b) throws IOException {
            ensureCurrent(1).writeByte(b);
            maybeFlushCurrent();
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
                maybeFlushCurrent();
            }
        }

        private ByteBuf ensureCurrent(int minWritable) throws IOException {
            if (current == null) {
                awaitWritable();
                current = stream.alloc().buffer(Math.max(UPLOAD_CHUNK, minWritable));
            }
            return current;
        }

        private void awaitWritable() throws IOException {
            while (!stream.isWritable()) {
                flushBatch(stream, batch, false);
                LockSupport.parkNanos(100_000);
                if (!stream.isOpen()) {
                    throw new IOException("Stream closed while waiting for writability");
                }
            }
        }

        private void maybeFlushCurrent() {
            if (current != null && !current.isWritable()) {
                batch.add(current);
                current = null;
                if (batch.size() >= UPLOAD_BATCH_CHUNKS) {
                    flushBatch(stream, batch, false);
                }
            }
        }

        void finish() {
            if (current != null && current.isReadable()) {
                batch.add(current);
                current = null;
            } else if (current != null) {
                current.release();
                current = null;
            }
            flushBatch(stream, batch, true);
        }

        void discard() {
            if (current != null) {
                current.release();
                current = null;
            }
            for (ByteBuf b : batch) {
                b.release();
            }
            batch.clear();
        }
    }

    private static void flushBatch(Http2StreamChannel stream, List<ByteBuf> batch, boolean endStream) {
        if (batch.isEmpty()) {
            stream.eventLoop()
                    .execute(() -> stream.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.EMPTY_BUFFER, endStream)));
            return;
        }

        ByteBuf[] bufs = batch.toArray(ByteBuf[]::new);
        batch.clear();
        stream.eventLoop().execute(() -> {
            for (int i = 0; i < bufs.length; i++) {
                boolean frameEndStream = endStream && i == bufs.length - 1;
                stream.write(new DefaultHttp2DataFrame(bufs[i], frameEndStream));
            }
            stream.flush();
        });
    }

    private static final class ResponseHandler extends SimpleChannelInboundHandler<Http2StreamFrame> {
        private final CompletableFuture<HttpResponse> headersFuture;
        private final ResponseBodyChannel body;
        private final AtomicReference<Throwable> error;
        private int status;
        private CompositeByteBuf batch; // accumulated DATA within a read-complete turn
        private boolean pendingEos;

        ResponseHandler(
                CompletableFuture<HttpResponse> headersFuture,
                ResponseBodyChannel body,
                AtomicReference<Throwable> error
        ) {
            this.headersFuture = headersFuture;
            this.body = body;
            this.error = error;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) throws Exception {
            if (msg instanceof Http2HeadersFrame hf) {
                var s = hf.headers().status();
                if (s != null)
                    status = Integer.parseInt(s.toString());
                var response = HttpResponse.create()
                        .setHttpVersion(HttpVersion.HTTP_2)
                        .setStatusCode(status)
                        .setHeaders(NettyUtils.fromH2Headers(hf.headers()))
                        .setBody(DataStream.ofEmpty());
                headersFuture.complete(response);
                if (hf.isEndStream()) {
                    pendingEos = true;
                }
            } else if (msg instanceof Http2DataFrame df) {
                ByteBuf content = df.content();
                if (content.readableBytes() > 0) {
                    if (batch == null) {
                        batch = ctx.alloc().compositeBuffer(16);
                    }
                    batch.addComponent(true, content.retain());
                }
                if (df.isEndStream()) {
                    pendingEos = true;
                }
            }
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
            if (batch != null) {
                body.publish(batch);
                batch = null;
            }
            if (pendingEos) {
                pendingEos = false;
                body.publishEos();
            }
            ctx.fireChannelReadComplete();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            error.compareAndSet(null, cause);
            if (!headersFuture.isDone()) {
                headersFuture.completeExceptionally(cause);
            }
            if (batch != null) {
                batch.release();
                batch = null;
            }
            body.publishError(cause);
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (batch != null) {
                body.publish(batch);
                batch = null;
            }
            body.publishEos();
        }
    }
}
