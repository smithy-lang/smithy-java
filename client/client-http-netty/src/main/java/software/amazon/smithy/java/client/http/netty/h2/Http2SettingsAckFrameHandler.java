/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import software.amazon.smithy.java.client.http.netty.NettyLogger;

/**
 * A no-op handler for H2 settings ack frame.
 */
@ChannelHandler.Sharable
final class Http2SettingsAckFrameHandler extends SimpleChannelInboundHandler<Http2SettingsAckFrame> {
    private static final NettyLogger LOGGER = NettyLogger.getLogger(Http2SettingsAckFrameHandler.class);
    private static final Http2SettingsAckFrameHandler INSTANCE = new Http2SettingsAckFrameHandler();

    private Http2SettingsAckFrameHandler() {}

    public static Http2SettingsAckFrameHandler getInstance() {
        return INSTANCE;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Http2SettingsAckFrame msg) {
        LOGGER.trace(ctx.channel(), "Received http2 settings ack frame: {}", msg);
        // ignored
    }
}
