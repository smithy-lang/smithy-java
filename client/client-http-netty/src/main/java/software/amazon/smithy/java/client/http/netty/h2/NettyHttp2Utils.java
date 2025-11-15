/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_ACK_SETTINGS;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_CONNECTION;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_FRAME_CODEC;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_INITIAL_WINDOW_SIZE;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_MULTIPLEX;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_SETTINGS;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http2.Http2FrameCodecBuilder;
import io.netty.handler.codec.http2.Http2FrameLogger;
import io.netty.handler.codec.http2.Http2MultiplexHandler;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.logging.LogLevel;
import java.util.concurrent.atomic.AtomicReference;
import software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport;

/**
 * Utility methods to work with HTTP/2.
 */
public final class NettyHttp2Utils {
    private NettyHttp2Utils() {}

    /**
     * Creates a new channel pool specialized for multiplexed HTTP/2 connections.
     *
     * @param channelPool    The channel pool to create HTTP/2 connected channels
     * @param eventLoopGroup The event loop to be used with Netty
     * @return The channel pool
     */
    public static ChannelPool createChannelPool(ChannelPool channelPool, EventLoopGroup eventLoopGroup) {
        return new Http2MultiplexedConnectionPool(channelPool, eventLoopGroup);
    }

    /**
     * Configures the channel handler context with handlers for HTTP/2 .
     *
     * @param handlerContext The handler context to configure
     * @param config         The configuration settings for H2
     * @param channelPoolRef A reference to the channel pool that will pool the channel
     */
    public static void configureHttp2Pipeline(
            ChannelHandlerContext handlerContext,
            NettyHttpClientTransport.Configuration config,
            AtomicReference<ChannelPool> channelPoolRef
    ) {
        var settings = createHttp2Settings(config);
        var codec = Http2FrameCodecBuilder.forClient()
                .initialSettings(settings)
                .headerSensitivityDetector((name, value) -> name.toString().equalsIgnoreCase("authorization"))
                .frameLogger(new Http2FrameLogger(LogLevel.TRACE))
                .build();

        var channel = handlerContext.channel();
        codec.connection().addListener(new Http2GoAwayEventListener(channel));
        channel.attr(HTTP2_CONNECTION).set(codec.connection());
        channel.attr(HTTP2_INITIAL_WINDOW_SIZE).set(settings.initialWindowSize());
        var pipeline = handlerContext.pipeline();
        pipeline.addLast(HTTP2_FRAME_CODEC, codec);
        pipeline.addLast(HTTP2_MULTIPLEX, new Http2MultiplexHandler(NoOpChannelInitializer.getInstance()));
        pipeline.addLast(HTTP2_SETTINGS,
                new Http2SettingsFrameHandler(handlerContext.channel(), config.h2Configuration(), channelPoolRef));
        pipeline.addLast(HTTP2_ACK_SETTINGS, Http2SettingsAckFrameHandler.getInstance());
    }

    private static Http2Settings createHttp2Settings(NettyHttpClientTransport.Configuration config) {
        var h2Configuration = config.h2Configuration();
        return Http2Settings.defaultSettings()
                .maxConcurrentStreams(h2Configuration.maxConcurrentStreams())
                .initialWindowSize(h2Configuration.initialWindowSize())
                .maxFrameSize(h2Configuration.maxFrameSize());
    }

    @ChannelHandler.Sharable
    static class NoOpChannelInitializer extends ChannelInitializer<Channel> {
        private static final NoOpChannelInitializer INSTANCE =
                new NoOpChannelInitializer();

        private NoOpChannelInitializer() {}

        public static NoOpChannelInitializer getInstance() {
            return INSTANCE;
        }

        @Override
        protected void initChannel(Channel ch) {}
    }
}
