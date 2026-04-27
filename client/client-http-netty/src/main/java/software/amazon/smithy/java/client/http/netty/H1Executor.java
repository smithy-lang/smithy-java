/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpRequest;
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
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Executes an HTTP/1.1 request on a Netty channel. One request per channel at a time
 * (no pipelining). Supports streaming request and response bodies via a single-slot inline
 * handoff to the caller VT (see {@link ResponseBodyChannel}).
 */
final class H1Executor {

    private static final int UPLOAD_CHUNK = 64 * 1024;
    private static final int UPLOAD_BATCH_CHUNKS = 4;
    private static final int BODY_HIGH_WATER = 32;
    private static final int BODY_LOW_WATER = 8;

    private H1Executor() {}

    static software.amazon.smithy.java.http.api.HttpResponse execute(
            Channel channel,
            HttpRequest request,
            long requestTimeoutMs
    ) throws IOException {
        var headersFuture = new CompletableFuture<software.amazon.smithy.java.http.api.HttpResponse>();
        var error = new AtomicReference<Throwable>();
        var bodyChannel = new ResponseBodyChannel(
                error,
                resume -> channel.eventLoop().execute(() -> channel.config().setAutoRead(resume)),
                null,
                BODY_HIGH_WATER,
                BODY_LOW_WATER);
        ResponseHandler handler = new ResponseHandler(headersFuture, bodyChannel, error);
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
            try {
                streamRequestBody(channel, request.body());
            } catch (IOException e) {
                channel.close();
                throw e;
            } finally {
                request.body().close();
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
            if (cause instanceof IOException io)
                throw io;
            throw new IOException("H1 request failed", cause);
        } catch (TimeoutException e) {
            channel.close();
            throw new IOException("Request timed out waiting for H1 headers", e);
        }

        return headResponse.toModifiable()
                .setBody(DataStream.ofInputStream(bodyChannel))
                .toUnmodifiable();
    }

    private static String buildRequestLine(HttpRequest request) {
        var uri = request.uri();
        String path = uri.getPath();
        if (path == null || path.isEmpty())
            path = "/";
        if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
            path = path + "?" + uri.getQuery();
        }
        return path;
    }

    private static void streamRequestBody(Channel channel, DataStream body) throws IOException {
        List<ByteBuf> batch = new ArrayList<>(UPLOAD_BATCH_CHUNKS);
        try (ReadableByteChannel channelBody = body.asChannel()) {
            if (channelBody instanceof ScatteringByteChannel scattering) {
                streamRequestBody(channel, scattering, batch);
                return;
            }
        }

        try (InputStream in = body.asInputStream()) {
            byte[] copyBuffer = new byte[UPLOAD_CHUNK];
            while (true) {
                int n = in.read(copyBuffer);
                if (n < 0) {
                    flushBatch(channel, batch, true);
                    return;
                }
                if (n == 0) {
                    continue;
                }

                while (!channel.isWritable()) {
                    flushBatch(channel, batch, false);
                    LockSupport.parkNanos(100_000);
                    if (!channel.isOpen()) {
                        throw new IOException("Channel closed while waiting for writability");
                    }
                }

                ByteBuf out = channel.alloc().buffer(n);
                out.writeBytes(copyBuffer, 0, n);
                batch.add(out);
                if (batch.size() >= UPLOAD_BATCH_CHUNKS) {
                    flushBatch(channel, batch, false);
                }
            }
        }
    }

    private static void streamRequestBody(
            Channel channel,
            ScatteringByteChannel in,
            List<ByteBuf> batch
    ) throws IOException {
        while (true) {
            ByteBuf out = channel.alloc().buffer(UPLOAD_CHUNK);
            int n = out.writeBytes(in, UPLOAD_CHUNK);
            if (n < 0) {
                out.release();
                flushBatch(channel, batch, true);
                return;
            }
            if (n == 0) {
                out.release();
                continue;
            }

            while (!channel.isWritable()) {
                flushBatch(channel, batch, false);
                LockSupport.parkNanos(100_000);
                if (!channel.isOpen()) {
                    throw new IOException("Channel closed while waiting for writability");
                }
            }

            if (n < out.capacity()) {
                out.writerIndex(n);
                out.capacity(n);
            }
            batch.add(out);
            if (batch.size() >= UPLOAD_BATCH_CHUNKS) {
                flushBatch(channel, batch, false);
            }
        }
    }

    private static void flushBatch(Channel channel, List<ByteBuf> batch, boolean endStream) {
        if (batch.isEmpty()) {
            if (endStream) {
                channel.eventLoop().execute(() -> channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT));
            }
            return;
        }

        ByteBuf[] bufs = batch.toArray(ByteBuf[]::new);
        batch.clear();
        channel.eventLoop().execute(() -> {
            for (ByteBuf buf : bufs) {
                channel.write(new DefaultHttpContent(buf));
            }
            if (endStream) {
                channel.write(LastHttpContent.EMPTY_LAST_CONTENT);
            }
            channel.flush();
        });
    }

    private static final class ResponseHandler extends SimpleChannelInboundHandler<HttpObject> {
        private final CompletableFuture<software.amazon.smithy.java.http.api.HttpResponse> headersFuture;
        private final ResponseBodyChannel body;
        private final AtomicReference<Throwable> error;

        ResponseHandler(
                CompletableFuture<software.amazon.smithy.java.http.api.HttpResponse> headersFuture,
                ResponseBodyChannel body,
                AtomicReference<Throwable> error
        ) {
            this.headersFuture = headersFuture;
            this.body = body;
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
                    body.publish(c.retain());
                }
                if (msg instanceof LastHttpContent) {
                    body.publishEos();
                }
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            error.compareAndSet(null, cause);
            if (!headersFuture.isDone()) {
                headersFuture.completeExceptionally(cause);
            }
            body.publishError(cause);
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            body.publishEos();
        }
    }
}
