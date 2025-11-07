/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import static software.amazon.smithy.java.client.http.netty.NettyConstants.HTTP_VERSION_FUTURE;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_MAX_CONCURRENT_STREAMS;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.pool.ChannelPool;
import io.netty.handler.codec.http2.Http2Settings;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import java.util.concurrent.atomic.AtomicReference;
import software.amazon.smithy.java.client.core.error.TransportException;
import software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport;
import software.amazon.smithy.java.client.http.netty.NettyLogger;
import software.amazon.smithy.java.http.api.HttpVersion;

/**
 * A handler for the {@link Http2SettingsFrame}. Configures the  channel based on the values received from server
 */
final class Http2SettingsFrameHandler extends SimpleChannelInboundHandler<Http2SettingsFrame> {
    private static final NettyLogger LOGGER =
            NettyLogger.getLogger(Http2SettingsFrameHandler.class);
    // Unsigned 32-bit int max value, 2^32 -1
    private static final long MAX_STREAMS_ALLOWED = (1L << 32) - 1;

    private final Channel channel;
    private final NettyHttpClientTransport.H2Configuration config;
    private final AtomicReference<ChannelPool> channelPoolRef;

    Http2SettingsFrameHandler(
            Channel channel,
            NettyHttpClientTransport.H2Configuration config,
            AtomicReference<ChannelPool> channelPoolRef
    ) {
        this.channel = channel;
        this.config = config;
        this.channelPoolRef = channelPoolRef;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Http2SettingsFrame msg) {
        var settings = msg.settings();
        var actualMaxConcurrentStreams = maxConcurrentStreams(settings);
        channel.attr(HTTP2_MAX_CONCURRENT_STREAMS).set(actualMaxConcurrentStreams);
        // The protocol negotiation is complete and the channel is ready.
        channel.attr(HTTP_VERSION_FUTURE).get().complete(HttpVersion.HTTP_2);
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        if (!channel.attr(HTTP_VERSION_FUTURE).get().isDone()) {
            channelError(new TransportException("The channel was closed before the protocol could be determined."),
                    channel,
                    ctx);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        channelError(cause, channel, ctx);
    }

    private long maxConcurrentStreams(Http2Settings settings) {
        var maxConcurrentStreams = settings.maxConcurrentStreams();
        if (maxConcurrentStreams == null) {
            maxConcurrentStreams = MAX_STREAMS_ALLOWED;
        }
        var actualMaxConcurrentStreams = Math.min(config.maxConcurrentStreams(), maxConcurrentStreams);
        LOGGER.debug(channel,
                "Received settings frame, maxConcurrentStreams: {}, initialWindowSize: {}," +
                        " maxFrameSize: {}. Using maxConcurrentStreams: {}",
                settings.maxConcurrentStreams(),
                settings.initialWindowSize(),
                settings.maxFrameSize(),
                actualMaxConcurrentStreams);
        return actualMaxConcurrentStreams;
    }

    private void channelError(Throwable cause, Channel ch, ChannelHandlerContext ctx) {
        ch.attr(HTTP_VERSION_FUTURE).get().completeExceptionally(cause);
        ctx.fireExceptionCaught(cause);
        try {
            if (ch.isActive()) {
                ch.close();
            }
        } finally {
            channelPoolRef.get().release(ch);
        }
    }
}
