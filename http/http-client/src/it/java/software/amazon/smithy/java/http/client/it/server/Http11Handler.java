/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;

public class Http11Handler extends ChannelInboundHandlerAdapter {
    private final ServerInitializer.Http11ClientHandlers handlers;

    public Http11Handler(ServerInitializer.Http11ClientHandlers handlers) {
        this.handlers = handlers;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof HttpRequest httpRequest) {
            var handler = handlers.create(ctx);
            // Initial request line and headers
            if (httpRequest instanceof FullHttpRequest fullHttpRequest) {
                handler.onFullRequest(ctx, fullHttpRequest);
            } else {
                handler.onRequest(ctx, httpRequest);
            }
        } else if (msg instanceof HttpContent httpContent) {
            var handler = handlers.get(ctx);
            if (httpContent instanceof LastHttpContent httpLastContent) {
                handler.onLastContent(ctx, httpLastContent);
            } else {
                handler.onContent(ctx, httpContent);
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        var handler = handlers.get(ctx);
        handler.onException(ctx, cause);
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        ctx.fireChannelInactive();
    }
}
