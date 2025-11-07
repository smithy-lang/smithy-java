/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.h2;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Frame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.handler.codec.http2.Http2ResetFrame;
import io.netty.handler.codec.http2.HttpConversionUtil;
import software.amazon.smithy.java.client.core.error.TransportException;
import software.amazon.smithy.java.client.http.netty.NettyLogger;

/**
 * Converts {@link Http2Frame}s to {@link HttpObject}s. Ignores the majority of {@link Http2Frame}s like PING
 * or SETTINGS.
 */
@ChannelHandler.Sharable
final class Http2ToHttpInboundAdapter extends SimpleChannelInboundHandler<Http2Frame> {
    private static final NettyLogger LOGGER = NettyLogger.getLogger(Http2ToHttpInboundAdapter.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Http2Frame frame) throws Exception {
        switch (frame) {
            case Http2DataFrame dataFrame -> onDataRead(dataFrame, ctx);
            case Http2HeadersFrame headersFrame -> onHeadersRead(headersFrame, ctx);
            case Http2ResetFrame resetFrame -> onRstStreamRead(resetFrame, ctx);
            default -> ctx.channel().parent().read();
        }
    }

    private void onHeadersRead(Http2HeadersFrame headersFrame, ChannelHandlerContext ctx) throws Http2Exception {
        var httpResponse =
                HttpConversionUtil.toHttpResponse(headersFrame.stream().id(), headersFrame.headers(), true);
        ctx.fireChannelRead(httpResponse);
        ctx.channel().read();
    }

    private void onDataRead(Http2DataFrame dataFrame, ChannelHandlerContext ctx) {
        var data = dataFrame.content();
        data.retain();
        if (dataFrame.isEndStream()) {
            ctx.fireChannelRead(new DefaultLastHttpContent(data));
        } else {
            ctx.fireChannelRead(new DefaultHttpContent(data));
        }
    }

    private void onRstStreamRead(Http2ResetFrame resetFrame, ChannelHandlerContext ctx) {
        ctx.fireExceptionCaught(new Http2ResetException(resetFrame.errorCode()));
    }

    public static final class Http2ResetException extends TransportException {
        Http2ResetException(long errorCode) {
            super(String.format("Connection reset. Error - %s(%d)", Http2Error.valueOf(errorCode).name(), errorCode));
        }
    }
}
