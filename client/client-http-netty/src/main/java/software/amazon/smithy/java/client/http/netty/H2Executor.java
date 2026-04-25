/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.buffer.ByteBuf;
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
import java.io.InputStream;
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
            try (InputStream in = request.body().asInputStream()) {
                streamRequestBody(stream, in);
            } catch (IOException e) {
                stream.close();
                throw e;
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

    private static void streamRequestBody(Http2StreamChannel stream, InputStream in) throws IOException {
        byte[] buf = new byte[UPLOAD_CHUNK];
        while (true) {
            int n = in.read(buf);
            if (n < 0) {
                stream.eventLoop()
                        .execute(() -> stream.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.EMPTY_BUFFER, true)));
                return;
            }
            if (n == 0)
                continue;

            while (!stream.isWritable()) {
                LockSupport.parkNanos(100_000);
                if (!stream.isOpen()) {
                    throw new IOException("Stream closed while waiting for writability");
                }
            }

            ByteBuf out = stream.alloc().buffer(n);
            out.writeBytes(buf, 0, n);
            stream.eventLoop().execute(() -> stream.writeAndFlush(new DefaultHttp2DataFrame(out, false)));
        }
    }

    private static final class ResponseHandler extends SimpleChannelInboundHandler<Http2StreamFrame> {
        private final CompletableFuture<HttpResponse> headersFuture;
        private final ResponseBodyChannel body;
        private final AtomicReference<Throwable> error;
        private int status;
        private io.netty.buffer.CompositeByteBuf batch; // accumulated DATA within a read-complete turn
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
