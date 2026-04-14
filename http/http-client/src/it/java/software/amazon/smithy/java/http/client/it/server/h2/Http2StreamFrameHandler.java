/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import software.amazon.smithy.java.http.client.it.server.NettyTestLogger;
import software.amazon.smithy.java.http.client.it.server.ServerInitializer;

public class Http2StreamFrameHandler extends SimpleChannelInboundHandler<Http2Frame> {

    private static final NettyTestLogger LOGGER = NettyTestLogger.getLogger(Http2StreamFrameHandler.class);
    private final ServerInitializer.Http2ClientHandlers handlers;

    public Http2StreamFrameHandler(ServerInitializer.Http2ClientHandlers h2ClientHandlers) {
        this.handlers = h2ClientHandlers;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Http2Frame frame) {
        if (frame instanceof Http2HeadersFrame headersFrame) {
            LOGGER.info(ctx.channel(), "received HTTP/2 headers frame");
            onHeadersRead(ctx, headersFrame);
            var handler = handlers.create(ctx);
            handler.onHeadersFrame(ctx, headersFrame);
        } else if (frame instanceof Http2DataFrame dataFrame) {
            LOGGER.info(ctx.channel(), "received HTTP/2 data frame");
            onDataRead(ctx, dataFrame);
            var handler = handlers.get(ctx);
            handler.onDataFrame(ctx, dataFrame);
        } else {
            LOGGER.warn(ctx.channel(), "unexpected frame: {}", frame);
        }
    }

    private void onHeadersRead(ChannelHandlerContext ctx, Http2HeadersFrame headersFrame) {
        var headers = headersFrame.headers();
        var method = headers.method().toString();
        var path = headers.path().toString();

        LOGGER.debug(ctx.channel(), "Received HTTP/2 request for method: {}, path: {}", method, path);
    }

    private void onDataRead(ChannelHandlerContext ctx, Http2DataFrame dataFrame) {
        LOGGER.debug(ctx.channel(), "Received data with {} bytes", dataFrame.content().readableBytes());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        var handler = handlers.get(ctx);
        handler.onException(ctx, cause);
        LOGGER.warn(ctx.channel(), "Exception caught, closing context", cause);
        ctx.close();
    }
}
