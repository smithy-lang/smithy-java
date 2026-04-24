/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
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
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Executes an HTTP/1.1 request on a Netty channel. One request per channel at a time
 * (no pipelining). Supports streaming request and response bodies.
 */
final class H1Executor {

    private static final int UPLOAD_CHUNK = 64 * 1024;
    private static final int RESPONSE_QUEUE_CAPACITY = 64;

    private H1Executor() {}

    static software.amazon.smithy.java.http.api.HttpResponse execute(
            Channel channel, HttpRequest request, long requestTimeoutMs) throws IOException {
        var headersFuture = new CompletableFuture<software.amazon.smithy.java.http.api.HttpResponse>();
        var bodyQueue = new LinkedBlockingQueue<ByteBuf>(RESPONSE_QUEUE_CAPACITY);
        var error = new AtomicReference<Throwable>();
        ChannelHandler handler = new ResponseHandler(headersFuture, bodyQueue, error);
        channel.pipeline().addLast("h1-response", handler);

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

        channel.eventLoop().execute(() -> channel.write(nettyReq));

        if (hasBody) {
            try (InputStream in = request.body().asInputStream()) {
                streamRequestBody(channel, in);
            } catch (IOException e) {
                channel.close();
                throw e;
            }
        } else {
            channel.eventLoop().execute(() -> channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT));
        }

        software.amazon.smithy.java.http.api.HttpResponse headResponse;
        try {
            headResponse = requestTimeoutMs > 0
                    ? headersFuture.get(requestTimeoutMs, TimeUnit.MILLISECONDS)
                    : headersFuture.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.close();
            throw new IOException("Interrupted waiting for H1 response headers", e);
        } catch (ExecutionException e) {
            channel.close();
            Throwable cause = e.getCause();
            if (cause instanceof IOException io) throw io;
            throw new IOException("H1 request failed", cause);
        } catch (TimeoutException e) {
            channel.close();
            throw new IOException("Request timed out waiting for H1 headers", e);
        }

        var bodyStream = new ResponseBodyInputStream(bodyQueue, error, () -> {
            channel.pipeline().remove(handler);
        });
        return headResponse.toModifiable()
                .setBody(DataStream.ofInputStream(bodyStream))
                .toUnmodifiable();
    }

    private static String buildRequestLine(HttpRequest request) {
        var uri = request.uri();
        String path = uri.getPath();
        if (path == null || path.isEmpty()) path = "/";
        if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            path = path + "?" + uri.getQuery();
        }
        return path;
    }

    private static void streamRequestBody(Channel channel, InputStream in) throws IOException {
        byte[] buf = new byte[UPLOAD_CHUNK];
        while (true) {
            int n = in.read(buf);
            if (n < 0) {
                channel.eventLoop().execute(() -> channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT));
                return;
            }
            if (n == 0) continue;

            while (!channel.isWritable()) {
                LockSupport.parkNanos(100_000);
                if (!channel.isOpen()) {
                    throw new IOException("Channel closed while waiting for writability");
                }
            }

            ByteBuf out = channel.alloc().buffer(n);
            out.writeBytes(buf, 0, n);
            channel.eventLoop().execute(() -> channel.writeAndFlush(new DefaultHttpContent(out)));
        }
    }

    private static final class ResponseHandler extends SimpleChannelInboundHandler<HttpObject> {
        private final CompletableFuture<software.amazon.smithy.java.http.api.HttpResponse> headersFuture;
        private final LinkedBlockingQueue<ByteBuf> bodyQueue;
        private final AtomicReference<Throwable> error;

        ResponseHandler(CompletableFuture<software.amazon.smithy.java.http.api.HttpResponse> headersFuture,
                LinkedBlockingQueue<ByteBuf> bodyQueue,
                AtomicReference<Throwable> error) {
            this.headersFuture = headersFuture;
            this.bodyQueue = bodyQueue;
            this.error = error;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
            if (msg instanceof HttpResponse nettyResp) {
                var response = software.amazon.smithy.java.http.api.HttpResponse.create()
                        .setHttpVersion(software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_1)
                        .setStatusCode(nettyResp.status().code())
                        .setHeaders(NettyUtils.fromH1Headers(nettyResp.headers()))
                        .setBody(DataStream.ofEmpty());
                headersFuture.complete(response);
            }
            if (msg instanceof HttpContent content) {
                ByteBuf c = content.content();
                if (c.readableBytes() > 0) {
                    bodyQueue.put(c.retain());
                }
                if (msg instanceof LastHttpContent) {
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
