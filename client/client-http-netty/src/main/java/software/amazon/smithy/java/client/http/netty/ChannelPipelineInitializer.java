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
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Utils.configureHttp2Pipeline;

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
    private static final NettyLogger LOGGER = NettyLogger.getLogger(ChannelPipelineInitializer.class);
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
        LOGGER.trace(channel, "Channel created, isHttps: {}, httpVersion: {}", poolKey.isHttps(), config.httpVersion());
        configureAttributes(channel);
        var pipeline = channel.pipeline();
        if (poolKey.isHttps()) {
            configureSsl(channel);
        }
        if (config.httpVersion() == HttpVersion.HTTP_2) {
            configureHttp2(channel);
        } else {
            configureHttp11(channel);
        }
        pipeline.addLast(new LoggingHandler(LogLevel.TRACE));
    }

    private void configureAttributes(Channel channel) {
        channel.attr(HTTP_VERSION_FUTURE).set(new CompletableFuture<>());
    }

    private void configureSsl(Channel channel) {
        var pipeline = channel.pipeline();
        var host = poolKey.host();
        pipeline.addLast(SSL, sslContext.newHandler(channel.alloc(), host, poolKey.port()));
        pipeline.addLast(SSL_HANDSHAKE, new NettySslHandshakeHandler(config.httpVersion()));
        pipeline.addLast(SSL_CLOSE_COMPLETE, new NettySslCloseCompletionHandler());
    }

    private void configureHttp11(Channel channel) {
        var pipeline = channel.pipeline();
        pipeline.addLast(HTTP11_CODEC, new HttpClientCodec());
        channel.attr(HTTP_VERSION_FUTURE).get().complete(HttpVersion.HTTP_1_1);
        channel.attr(CHANNEL_POOL).set(channelPoolRef.get());
    }

    private void configureHttp2(Channel channel) {
        var pipeline = channel.pipeline();
        var connectionMode = config.h2Configuration().connectionMode();
        if (connectionMode == NettyHttpClientTransport.H2ConnectionMode.AUTO) {
            if (poolKey.isHttps()) {
                connectionMode = NettyHttpClientTransport.H2ConnectionMode.ALPN;
            } else {
                connectionMode = NettyHttpClientTransport.H2ConnectionMode.PRIOR_KNOWLEDGE;
            }
        }
        LOGGER.trace(channel, "Configuring HTTP/2 pipeline, with H2 connection mode {}", connectionMode);
        if (connectionMode == NettyHttpClientTransport.H2ConnectionMode.ALPN) {
            if (!poolKey.isHttps()) {
                // FIXME, validate this upstream to avoid failing at this point.
                var message = "Only HTTPS connections are supported when using ALPN connection mode";
                NettyUtils.Asserts.shouldNotBeReached(channel, message);
                channel.pipeline().fireExceptionCaught(new IllegalStateException(message));
                return;
            }
            pipeline.addLast(ALPN, new NettyProtocolNegotiationHandler(config, channelPoolRef));
        } else {
            configureHttp2Pipeline(channel, pipeline, config, channelPoolRef);
            channel.read();
            channel.attr(CHANNEL_POOL).set(channelPoolRef.get());
        }
    }
}
