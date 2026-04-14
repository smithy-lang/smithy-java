/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import java.util.Arrays;
import java.util.List;

public class MultiplexingHttp2ClientHandler implements Http2ClientHandler {
    private final List<Http2ClientHandler> handlers;

    public MultiplexingHttp2ClientHandler(Http2ClientHandler... handler) {
        this.handlers = Arrays.asList(handler);
    }

    @Override
    public void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {
        for (var handler : handlers) {
            handler.onHeadersFrame(ctx, frame);
        }
    }

    @Override
    public void onDataFrame(ChannelHandlerContext ctx, Http2DataFrame frame) {
        for (var handler : handlers) {
            handler.onDataFrame(ctx, frame);
        }
    }

    @Override
    public void onException(ChannelHandlerContext ctx, Throwable cause) {
        for (var handler : handlers) {
            handler.onException(ctx, cause);
        }
    }
}
