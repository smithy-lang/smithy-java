/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import software.amazon.smithy.java.client.core.error.TransportException;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Netty channel handler that processes HTTP responses. Streams responses only when
 * transfer-encoding is chunked or content-length is unknown, otherwise buffers the complete response.
 */
final class NettyHttpResponseHandler extends SimpleChannelInboundHandler<Object> {
    private static final NettyLogger LOGGER = NettyLogger.getLogger(NettyHttpResponseHandler.class);

    private final CompletableFuture<software.amazon.smithy.java.http.api.HttpResponse> responseFuture;
    private final Channel channel;
    private Flow.Subscriber<ByteBuffer> producerSubscriber;

    NettyHttpResponseHandler(
            CompletableFuture<software.amazon.smithy.java.http.api.HttpResponse> responseFuture,
            Channel channel
    ) {
        this.responseFuture = responseFuture;
        this.channel = channel;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpResponse response) {
            var statusCode = response.status().code();
            var dataStream = createDataStream(ctx.channel(), response);
            this.producerSubscriber = dataStream.producerSubscriber();
            producerSubscriber.onSubscribe(new ContentSubscription());
            var smithyResponse = toSmithyHttpResponse(response, dataStream);
            responseFuture.complete(smithyResponse);
            LOGGER.debug(ctx.channel(), "Completed response future with streaming body, status: {}", statusCode);
        } else if (msg instanceof LastHttpContent) {
            LOGGER.trace(ctx.channel(), "Last content received");
            producerSubscriber.onComplete();
        } else if (msg instanceof HttpContent content) {
            LOGGER.trace(ctx.channel(), "Content received");
            producerSubscriber.onNext(toByteBuffer(content));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error(ctx.channel(), "Exception in response handler", cause);
        if (producerSubscriber != null) {
            producerSubscriber.onError(cause);
        }
        if (!responseFuture.isDone()) {
            responseFuture.completeExceptionally(cause);
        }
    }

    private software.amazon.smithy.java.http.api.HttpResponse toSmithyHttpResponse(
            HttpResponse response,
            DataStream dataStream
    ) {
        var httpVersion = convertHttpVersion(response.protocolVersion());
        var smithyHeaders = toSmithyHeaders(response);
        var statusCode = response.status().code();
        return software.amazon.smithy.java.http.api.HttpResponse.builder()
                .httpVersion(httpVersion)
                .statusCode(statusCode)
                .headers(smithyHeaders)
                .body(dataStream)
                .build();
    }

    private NettyDataStream createDataStream(Channel channel, HttpResponse response) {
        var contentType = getContentType(response);
        var contentLength = getContentLength(response);
        return new NettyDataStream(contentType, contentLength, channel);
    }

    private HttpVersion convertHttpVersion(io.netty.handler.codec.http.HttpVersion nettyVersion) {
        if (nettyVersion.equals(io.netty.handler.codec.http.HttpVersion.HTTP_1_0)) {
            // Map 1.0 to 1.1, Smithy doesn't support 1.0
            return HttpVersion.HTTP_1_1;
        } else if (nettyVersion.equals(io.netty.handler.codec.http.HttpVersion.HTTP_1_1)) {
            return HttpVersion.HTTP_1_1;
        } else {
            return HttpVersion.HTTP_2; // Default to HTTP/2 for other versions
        }
    }

    private HttpHeaders toSmithyHeaders(HttpResponse response) {
        var smithyHeaders = HttpHeaders.ofModifiable();
        var nettyHeaders = response.headers();
        for (var entry : nettyHeaders) {
            smithyHeaders.addHeader(entry.getKey(), entry.getValue());
        }
        return smithyHeaders;
    }

    private String getContentType(HttpResponse response) {
        return response.headers().get(HttpHeaderNames.CONTENT_TYPE);
    }

    private long getContentLength(HttpResponse response) {
        var contentLength = response.headers().get(HttpHeaderNames.CONTENT_LENGTH.toString());
        if (contentLength != null) {
            try {
                return Long.parseLong(contentLength);
            } catch (NumberFormatException e) {
                LOGGER.warn(null, "Failed to parse content length header '{}'", contentLength);
            }
        }
        return -1;
    }

    private ByteBuffer toByteBuffer(HttpContent content) {
        var byteBuf = content.content();
        if (byteBuf.isReadable()) {
            // Convert Netty's ByteBuf to Java's ByteBuffer
            var buffer = ByteBuffer.allocate(byteBuf.readableBytes());
            byteBuf.getBytes(byteBuf.readerIndex(), buffer);
            buffer.flip();
            return buffer;
        }
        return null;
    }

    class ContentSubscription implements Flow.Subscription {
        @Override
        public void request(long n) {
            LOGGER.trace(channel, "Content requested {}", n);
            // Ignore the request amount and just submit one read request. Netty will ignore any new read
            // request if there's one already queued.
            channel.eventLoop().submit(channel::read);
        }

        @Override
        public void cancel() {
            channel.pipeline().fireExceptionCaught(new TransportException("Content subscription cancelled"));
        }
    }
}
