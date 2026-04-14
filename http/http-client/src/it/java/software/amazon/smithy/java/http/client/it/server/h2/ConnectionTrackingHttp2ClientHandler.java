/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP/2 handler that tracks unique connections and request counts.
 */
public class ConnectionTrackingHttp2ClientHandler implements Http2ClientHandler {

    private final Http2ClientHandler delegate;
    private final Set<ChannelId> seenConnections = ConcurrentHashMap.newKeySet();
    private final AtomicInteger requestCount = new AtomicInteger();

    public ConnectionTrackingHttp2ClientHandler(Http2ClientHandler delegate) {
        this.delegate = delegate;
    }

    public int connectionCount() {
        return seenConnections.size();
    }

    public int requestCount() {
        return requestCount.get();
    }

    @Override
    public void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {
        // parent() gives us the connection channel (stream channels have the connection as parent)
        seenConnections.add(ctx.channel().parent().id());
        requestCount.incrementAndGet();
        delegate.onHeadersFrame(ctx, frame);
    }

    @Override
    public void onDataFrame(ChannelHandlerContext ctx, Http2DataFrame frame) {
        delegate.onDataFrame(ctx, frame);
    }

    @Override
    public void onException(ChannelHandlerContext ctx, Throwable cause) {
        delegate.onException(ctx, cause);
    }
}
