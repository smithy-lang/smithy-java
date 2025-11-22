/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.it.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import java.util.Arrays;
import java.util.List;

public class MultiplexingHttp11ClientHandler implements Http11ClientHandler {
    private final List<Http11ClientHandler> handlers;

    public MultiplexingHttp11ClientHandler(Http11ClientHandler... handlers) {
        this.handlers = Arrays.asList(handlers);
    }

    @Override
    public void onFullRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        for (var handler : handlers) {
            handler.onFullRequest(ctx, request);
        }
    }

    @Override
    public void onRequest(ChannelHandlerContext ctx, HttpRequest request) {
        for (var handler : handlers) {
            handler.onRequest(ctx, request);
        }
    }

    @Override
    public void onContent(ChannelHandlerContext ctx, HttpContent content) {
        for (var handler : handlers) {
            handler.onContent(ctx, content);
        }
    }

    @Override
    public void onLastContent(ChannelHandlerContext ctx, LastHttpContent content) {
        for (var handler : handlers) {
            handler.onLastContent(ctx, content);
        }
    }

    @Override
    public void onException(ChannelHandlerContext ctx, Throwable cause) {
        for (var handler : handlers) {
            handler.onException(ctx, cause);
        }
    }
}
