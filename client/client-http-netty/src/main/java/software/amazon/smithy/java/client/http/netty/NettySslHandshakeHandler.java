/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;
import software.amazon.smithy.java.http.api.HttpVersion;

/**
 * Netty handler to that listens for the SSL handshake completion event to activate the channel.
 */
final class NettySslHandshakeHandler extends ChannelInboundHandlerAdapter {

    private static final NettyLogger LOGGER = NettyLogger.getLogger(NettySslHandshakeHandler.class);
    private final HttpVersion httpVersion;
    private boolean sslHandshakeComplete;

    NettySslHandshakeHandler(HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.read();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        if (!sslHandshakeComplete) {
            // Continue reading until the SSL handshake completes.
            ctx.read();
        }
        ctx.fireChannelReadComplete();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof SslHandshakeCompletionEvent handshake) {
            sslHandshakeComplete = true;
            ctx.pipeline().remove(this);
            var versionFuture = ctx.channel().attr(NettyConstants.HTTP_VERSION_FUTURE).get();
            if (handshake.isSuccess()) {
                LOGGER.trace(ctx.channel(), "SSL handshake completed successfully");
                if (httpVersion == HttpVersion.HTTP_1_1) {
                    // For HTTP 1.1, when using TLS, the channel is considered ready after completing
                    // the handshake, we complete the future to signal it.
                    versionFuture.complete(HttpVersion.HTTP_1_1);
                }
                ctx.fireChannelActive();
            } else {
                var cause = handshake.cause();
                LOGGER.warn(ctx.channel(), "SSL handshake failed", cause);
                versionFuture.completeExceptionally(cause);
                ctx.fireExceptionCaught(cause);
            }
        }
        ctx.fireUserEventTriggered(evt);
    }
}
