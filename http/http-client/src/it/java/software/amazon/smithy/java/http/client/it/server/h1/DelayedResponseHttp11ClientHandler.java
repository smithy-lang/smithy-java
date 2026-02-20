/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h1;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import java.util.concurrent.TimeUnit;

/**
 * HTTP/1.1 handler that delays response.
 */
public class DelayedResponseHttp11ClientHandler implements Http11ClientHandler {

    private final Http11ClientHandler delegate;
    private final long delayMs;

    public DelayedResponseHttp11ClientHandler(Http11ClientHandler delegate, long delayMs) {
        this.delegate = delegate;
        this.delayMs = delayMs;
    }

    @Override
    public void onFullRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        ctx.executor().schedule(() -> delegate.onFullRequest(ctx, request), delayMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onRequest(ChannelHandlerContext ctx, HttpRequest request) {
        delegate.onRequest(ctx, request);
    }

    @Override
    public void onContent(ChannelHandlerContext ctx, HttpContent content) {
        delegate.onContent(ctx, content);
    }

    @Override
    public void onLastContent(ChannelHandlerContext ctx, LastHttpContent content) {
        delegate.onLastContent(ctx, content);
    }

    @Override
    public void onException(ChannelHandlerContext ctx, Throwable cause) {
        delegate.onException(ctx, cause);
    }
}
