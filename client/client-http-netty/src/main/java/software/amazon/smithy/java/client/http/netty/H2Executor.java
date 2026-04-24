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
import java.util.concurrent.LinkedBlockingQueue;
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
 * <p>Streaming: request body is read from the caller's {@link java.io.InputStream} in 64KB chunks
 * and written to the stream channel, respecting {@code channel.isWritable()} backpressure.
 * The response is returned as soon as the HEADERS frame arrives; body bytes stream through a
 * blocking {@link ResponseBodyInputStream}.
 */
final class H2Executor {

    private static final int UPLOAD_CHUNK = 64 * 1024;
    private static final int RESPONSE_QUEUE_CAPACITY = 64;

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
        var bodyQueue = new LinkedBlockingQueue<ByteBuf>(RESPONSE_QUEUE_CAPACITY);
        var error = new AtomicReference<Throwable>();
        var handler = new ResponseHandler(headersFuture, bodyQueue, error);
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
            if (cause instanceof IOException io) throw io;
            throw new IOException("H2 request failed", cause);
        } catch (TimeoutException e) {
            stream.close();
            throw new IOException("Request timed out waiting for H2 headers", e);
        }

        var bodyStream = new ResponseBodyInputStream(bodyQueue, error, stream::close);
        return headResponse.toModifiable()
                .setBody(DataStream.ofInputStream(bodyStream))
                .toUnmodifiable();
    }

    private static void streamRequestBody(Http2StreamChannel stream, InputStream in) throws IOException {
        byte[] buf = new byte[UPLOAD_CHUNK];
        while (true) {
            int n = in.read(buf);
            if (n < 0) {
                stream.eventLoop().execute(() ->
                        stream.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.EMPTY_BUFFER, true)));
                return;
            }
            if (n == 0) continue;

            while (!stream.isWritable()) {
                LockSupport.parkNanos(100_000);
                if (!stream.isOpen()) {
                    throw new IOException("Stream closed while waiting for writability");
                }
            }

            ByteBuf out = stream.alloc().buffer(n);
            out.writeBytes(buf, 0, n);
            stream.eventLoop().execute(() ->
                    stream.writeAndFlush(new DefaultHttp2DataFrame(out, false)));
        }
    }

    private static final class ResponseHandler extends SimpleChannelInboundHandler<Http2StreamFrame> {
        private final CompletableFuture<HttpResponse> headersFuture;
        private final LinkedBlockingQueue<ByteBuf> bodyQueue;
        private final AtomicReference<Throwable> error;
        private int status;

        ResponseHandler(CompletableFuture<HttpResponse> headersFuture,
                LinkedBlockingQueue<ByteBuf> bodyQueue,
                AtomicReference<Throwable> error) {
            this.headersFuture = headersFuture;
            this.bodyQueue = bodyQueue;
            this.error = error;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Http2StreamFrame msg) throws Exception {
            if (msg instanceof Http2HeadersFrame hf) {
                var s = hf.headers().status();
                if (s != null) status = Integer.parseInt(s.toString());
                var response = HttpResponse.create()
                        .setHttpVersion(HttpVersion.HTTP_2)
                        .setStatusCode(status)
                        .setHeaders(NettyUtils.fromH2Headers(hf.headers()))
                        .setBody(DataStream.ofEmpty());
                headersFuture.complete(response);
                if (hf.isEndStream()) {
                    bodyQueue.put(ResponseBodyInputStream.EOS_MARKER);
                }
            } else if (msg instanceof Http2DataFrame df) {
                ByteBuf content = df.content();
                if (content.readableBytes() > 0) {
                    bodyQueue.put(content.retain());
                }
                if (df.isEndStream()) {
                    bodyQueue.put(ResponseBodyInputStream.EOS_MARKER);
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            error.compareAndSet(null, cause);
            if (!headersFuture.isDone()) {
                headersFuture.completeExceptionally(cause);
            }
            bodyQueue.offer(ResponseBodyInputStream.EOS_MARKER);
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            bodyQueue.offer(ResponseBodyInputStream.EOS_MARKER);
        }
    }
}
