/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http2.Http2GoAwayFrame;
import io.netty.handler.codec.http2.Http2PingFrame;
import io.netty.handler.codec.http2.Http2SettingsAckFrame;
import io.netty.handler.codec.http2.Http2SettingsFrame;
import software.amazon.smithy.java.http.client.it.server.NettyTestLogger;

public class Http2ConnectionFrameHandler extends ChannelInboundHandlerAdapter {
    private static final NettyTestLogger LOGGER = NettyTestLogger.getLogger(Http2ConnectionFrameHandler.class);

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Http2SettingsFrame settingsFrame) {
            // SETTINGS are automatically acknowledged by Http2FrameCodec
            LOGGER.info(ctx.channel(), "Received SETTINGS frame: {}", settingsFrame.settings());
        } else if (msg instanceof Http2PingFrame) {
            // PING responses are automatically handled by Http2FrameCodec
            LOGGER.info(ctx.channel(), "Received PING frame");
        } else if (msg instanceof Http2GoAwayFrame goAwayFrame) {
            LOGGER.info(ctx.channel(),
                    "Received GOAWAY frame, error code: {}, last streamId: {}",
                    goAwayFrame.errorCode(),
                    goAwayFrame.lastStreamId());
        } else if (msg instanceof Http2SettingsAckFrame) {
            LOGGER.info(ctx.channel(), "Received settings ack frame");
        } else {
            // Unknown connection-level frame
            LOGGER.warn(ctx.channel(), "Received an unknown message: {}", msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.warn(ctx.channel(), "Exception caught, closing context", cause);
        ctx.close();
    }
}
