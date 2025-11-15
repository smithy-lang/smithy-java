/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import static software.amazon.smithy.java.client.http.netty.NettyConstants.ALPN;
import static software.amazon.smithy.java.client.http.netty.NettyConstants.CHANNEL_POOL;
import static software.amazon.smithy.java.client.http.netty.NettyConstants.HTTP11_CODEC;
import static software.amazon.smithy.java.client.http.netty.NettyConstants.HTTP_VERSION_FUTURE;
import static software.amazon.smithy.java.client.http.netty.NettyConstants.SSL;
import static software.amazon.smithy.java.client.http.netty.NettyConstants.SSL_CLOSE_COMPLETE;
import static software.amazon.smithy.java.client.http.netty.NettyConstants.SSL_HANDSHAKE;

import io.netty.channel.Channel;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import software.amazon.smithy.java.http.api.HttpVersion;

/**
 * Initializes the channel pipeline adding the handlers needed according with the
 * configuration and the URI.
 */
final class ChannelPipelineInitializer extends AbstractChannelPoolHandler {
    private final NettyHttpClientTransport.Configuration config;
    private final SslContext sslContext;
    private final ChannelPoolMap.ChannelPoolKey poolKey;
    private final AtomicReference<ChannelPool> channelPoolRef;

    ChannelPipelineInitializer(
            NettyHttpClientTransport.Configuration config,
            ChannelPoolMap.ChannelPoolKey poolKey,
            SslContext sslContext,
            AtomicReference<ChannelPool> channelPoolRef
    ) {
        this.config = config;
        this.poolKey = poolKey;
        this.sslContext = sslContext;
        this.channelPoolRef = channelPoolRef;
    }

    @Override
    public void channelCreated(Channel channel) {
        channel.attr(HTTP_VERSION_FUTURE).set(new CompletableFuture<>());
        var host = poolKey.host();
        var pipeline = channel.pipeline();
        if (poolKey.isHttps()) {
            pipeline.addLast(SSL, sslContext.newHandler(channel.alloc(), host, poolKey.port()));
            pipeline.addLast(SSL_HANDSHAKE, new NettySslHandshakeHandler(config.httpVersion()));
            pipeline.addLast(SSL_CLOSE_COMPLETE, new NettySslCloseCompletionHandler());
            if (config.httpVersion() == HttpVersion.HTTP_2) {
                pipeline.addLast(ALPN, new NettyProtocolNegotiationHandler(config, channelPoolRef));
            } else {
                channel.attr(CHANNEL_POOL).set(channelPoolRef.get());
            }
        } else {
            pipeline.addLast(HTTP11_CODEC, new HttpClientCodec());
            channel.attr(HTTP_VERSION_FUTURE).get().complete(HttpVersion.HTTP_1_1);
            channel.attr(CHANNEL_POOL).set(channelPoolRef.get());
        }
        pipeline.addLast(new LoggingHandler(LogLevel.TRACE));
    }
}
