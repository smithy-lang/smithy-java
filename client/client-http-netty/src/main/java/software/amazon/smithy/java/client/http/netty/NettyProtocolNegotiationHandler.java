/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import static software.amazon.smithy.java.client.http.netty.NettyConstants.ALPN;
import static software.amazon.smithy.java.client.http.netty.NettyConstants.HTTP11_CODEC;
import static software.amazon.smithy.java.client.http.netty.NettyConstants.HTTP_VERSION_FUTURE;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Utils.configureHttp2Pipeline;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler;
import java.util.concurrent.atomic.AtomicReference;
import software.amazon.smithy.java.client.core.error.TransportProtocolException;
import software.amazon.smithy.java.http.api.HttpVersion;

/**
 * Application Layer Protocol Negotiation (ALPN) handler.
 */
final class NettyProtocolNegotiationHandler extends ApplicationProtocolNegotiationHandler {
    private static final NettyLogger LOGGER =
            NettyLogger.getLogger(NettyProtocolNegotiationHandler.class);

    private final AtomicReference<ChannelPool> channelPoolRef;
    private final NettyHttpClientTransport.Configuration config;

    NettyProtocolNegotiationHandler(
            NettyHttpClientTransport.Configuration config,
            AtomicReference<ChannelPool> channelPoolRef
    ) {
        super(ApplicationProtocolNames.HTTP_2);
        this.config = config;
        this.channelPoolRef = channelPoolRef;
    }

    @Override
    protected void configurePipeline(ChannelHandlerContext ctx, String protocol) {
        LOGGER.info(ctx.channel(), "Configuring pipeline for protocol {}", protocol);
        if (ApplicationProtocolNames.HTTP_2.equals(protocol)) {
            configureHttp2Pipeline(ctx, config, channelPoolRef);
        } else if (ApplicationProtocolNames.HTTP_1_1.equals(protocol)) {
            configureHttp1(ctx);
        } else {
            LOGGER.warn(ctx.channel(), "Unsupported protocol {}", protocol);
            var ex = new TransportProtocolException("unknown protocol: " + protocol);
            ctx.channel().attr(HTTP_VERSION_FUTURE).get().completeExceptionally(ex);
            ctx.fireExceptionCaught(ex);
            ctx.close();
            return;
        }
        var pipeline = ctx.pipeline();
        if (pipeline.get(ALPN) != null) {
            pipeline.remove(ALPN);
        }
    }

    private void configureHttp1(ChannelHandlerContext ctx) {
        var versionFuture = ctx.channel().attr(NettyConstants.HTTP_VERSION_FUTURE).get();
        versionFuture.complete(HttpVersion.HTTP_1_1);
        ctx.pipeline().addLast(HTTP11_CODEC, new HttpClientCodec());
    }
}
