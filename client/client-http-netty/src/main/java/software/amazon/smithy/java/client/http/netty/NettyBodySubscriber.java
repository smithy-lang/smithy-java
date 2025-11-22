/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.http.api.HttpResponse;

/**
 * Flow.Subscriber that streams request body data to a Netty channel using chunked transfer encoding.
 */
final class NettyBodySubscriber implements Flow.Subscriber<ByteBuffer> {
    private static final NettyLogger LOGGER = NettyLogger.getLogger(NettyBodySubscriber.class);
    private final Channel channel;
    private final CompletableFuture<HttpResponse> responseFuture;
    private Flow.Subscription subscription;
    private volatile boolean completed = false;

    NettyBodySubscriber(Channel channel, CompletableFuture<HttpResponse> responseFuture) {
        this.channel = channel;
        this.responseFuture = responseFuture;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        LOGGER.trace(channel, "onSubscribe, requesting from subscription");
        subscription.request(1);
    }

    @Override
    public void onNext(ByteBuffer item) {
        try {
            LOGGER.trace(channel, "onNext, sending item");
            if (item.hasRemaining()) {
                // Convert ByteBuffer to Netty ByteBuf and send as HTTP chunk
                var content = new DefaultHttpContent(Unpooled.wrappedBuffer(item));
                channel.writeAndFlush(content)
                        .addListener(new ContentWriteListener(channel, subscription, responseFuture));
            } else {
                if (!completed) {
                    subscription.request(1);
                }
            }
        } catch (Exception e) {
            LOGGER.error(channel, "Error processing chunk", e);
            if (!responseFuture.isDone()) {
                responseFuture.completeExceptionally(ClientTransport.remapExceptions(e));
            }
            subscription.cancel();
        }
    }

    @Override
    public void onError(Throwable throwable) {
        LOGGER.warn(channel, "Error in streaming body", throwable);
        if (!responseFuture.isDone()) {
            responseFuture.completeExceptionally(ClientTransport.remapExceptions(throwable));
        }
        channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
    }

    @Override
    public void onComplete() {
        if (completed) {
            return;
        }
        completed = true;
        LOGGER.trace(channel, "onComplete");
        // Send the final chunk to indicate end of stream
        channel.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                .addListener(new LastContentWriteListener(channel, responseFuture));
    }

    static class ContentWriteListener implements GenericFutureListener<Future<? super Void>> {
        private final Channel channel;
        private final Flow.Subscription subscription;
        private final CompletableFuture<HttpResponse> responseFuture;

        ContentWriteListener(
                Channel channel,
                Flow.Subscription subscription,
                CompletableFuture<HttpResponse> responseFuture
        ) {
            this.channel = channel;
            this.subscription = subscription;
            this.responseFuture = responseFuture;
        }

        @Override
        public void operationComplete(Future<? super Void> writeFuture) {
            if (writeFuture.isSuccess()) {
                subscription.request(1);
            } else {
                var cause = writeFuture.cause();
                LOGGER.warn(channel, "Failed to write chunk", cause);
                if (!responseFuture.isDone()) {
                    responseFuture.completeExceptionally(ClientTransport.remapExceptions(cause));
                }
                subscription.cancel();
            }
        }
    }

    static class LastContentWriteListener implements GenericFutureListener<Future<? super Void>> {
        private final Channel channel;
        private final CompletableFuture<HttpResponse> responseFuture;

        LastContentWriteListener(Channel channel, CompletableFuture<HttpResponse> responseFuture) {
            this.channel = channel;
            this.responseFuture = responseFuture;
        }

        @Override
        public void operationComplete(Future<? super Void> writeFuture) {
            if (writeFuture.isSuccess()) {
                LOGGER.trace(channel, "last content sent");
            } else {
                var cause = writeFuture.cause();
                LOGGER.warn(channel, "Failed to write final chunk", cause);
                if (!responseFuture.isDone()) {
                    responseFuture.completeExceptionally(ClientTransport.remapExceptions(cause));
                }
            }
        }
    }
}
