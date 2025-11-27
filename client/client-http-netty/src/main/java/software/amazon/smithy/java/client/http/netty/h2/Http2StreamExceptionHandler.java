/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.TimeoutException;
import java.io.IOException;
import software.amazon.smithy.java.client.http.netty.NettyLogger;

/**
 * An exception handler attached to streams. I/O or timeout exceptions caught on a stream
 * will close the parent channel to new streams and eventually release it when all the
 * streams have been closed.
 */
@ChannelHandler.Sharable
final class Http2StreamExceptionHandler extends ChannelInboundHandlerAdapter {
    private static final NettyLogger LOGGER = NettyLogger.getLogger(Http2StreamExceptionHandler.class);
    private static final Http2StreamExceptionHandler INSTANCE = new Http2StreamExceptionHandler();

    private Http2StreamExceptionHandler() {}

    public static Http2StreamExceptionHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        var channel = ctx.channel();
        var parent = channel.parent();
        if (parent != null && (cause instanceof TimeoutException || cause instanceof IOException)) {
            var message = "An I/O error occurred on an HTTP/2 stream, notifying the connection channel";
            LOGGER.info(channel, message, cause);
            parent.pipeline().fireExceptionCaught(new Http2ConnectionTerminatingException(message, cause));
        }
        ctx.fireExceptionCaught(cause);
    }
}
