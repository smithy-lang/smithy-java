/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_MULTIPLEXED_CHANNEL;
import static software.amazon.smithy.java.client.http.netty.h2.NettyHttp2Constants.HTTP2_MULTIPLEXED_CONNECTION_POOL;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import java.nio.channels.ClosedChannelException;

/**
 * Handler to handle exceptions. It will close the parent channel to new streams if
 * an {@link Http2ConnectionTerminatingException}, or will close and release the
 * parent if any other exception is caught during the processing.
 */
@ChannelHandler.Sharable
final class ReleaseOnExceptionHandler extends ChannelDuplexHandler {
    private static final ClosedChannelException CLOSED_CHANNEL_EXCEPTION = new ClosedChannelException();
    private static final ReleaseOnExceptionHandler INSTANCE = new ReleaseOnExceptionHandler();

    private ReleaseOnExceptionHandler() {}

    public static ReleaseOnExceptionHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closeAndReleaseParent(ctx, CLOSED_CHANNEL_EXCEPTION);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof Http2ConnectionTerminatingException e) {
            closeConnectionToNewStreams(ctx, e);
        } else {
            closeAndReleaseParent(ctx, cause);
        }
    }

    void closeConnectionToNewStreams(ChannelHandlerContext ctx, Http2ConnectionTerminatingException cause) {
        var multiplexedChannel = ctx.channel().attr(HTTP2_MULTIPLEXED_CHANNEL).get();
        if (multiplexedChannel != null) {
            multiplexedChannel.closeToNewStreams();
        } else {
            closeAndReleaseParent(ctx, cause);
        }
    }

    private void closeAndReleaseParent(ChannelHandlerContext ctx, Throwable cause) {
        var pool = ctx.channel().attr(HTTP2_MULTIPLEXED_CONNECTION_POOL).get();
        pool.closeAndReleaseParent(ctx.channel(), cause);
    }
}
