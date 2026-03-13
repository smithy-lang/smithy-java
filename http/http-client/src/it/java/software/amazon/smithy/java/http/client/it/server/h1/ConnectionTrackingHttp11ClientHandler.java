/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h1;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP/1.1 handler that tracks unique connections and request counts.
 */
public class ConnectionTrackingHttp11ClientHandler implements Http11ClientHandler {

    private final Http11ClientHandler delegate;
    private final Set<ChannelId> seenConnections = ConcurrentHashMap.newKeySet();
    private final AtomicInteger requestCount = new AtomicInteger();

    public ConnectionTrackingHttp11ClientHandler(Http11ClientHandler delegate) {
        this.delegate = delegate;
    }

    public int connectionCount() {
        return seenConnections.size();
    }

    public int requestCount() {
        return requestCount.get();
    }

    @Override
    public void onFullRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        seenConnections.add(ctx.channel().id());
        requestCount.incrementAndGet();
        delegate.onFullRequest(ctx, request);
    }

    @Override
    public void onRequest(ChannelHandlerContext ctx, HttpRequest request) {
        seenConnections.add(ctx.channel().id());
        requestCount.incrementAndGet();
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
