/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h2;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2GoAwayFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP/2 handler that sends GOAWAY after first request on each connection.
 */
public class GoawayAfterFirstRequestHandler implements Http2ClientHandler {

    private final Set<ChannelId> seenConnections = ConcurrentHashMap.newKeySet();
    private final String responseBody;

    public GoawayAfterFirstRequestHandler(String responseBody) {
        this.responseBody = responseBody;
    }

    public int connectionCount() {
        return seenConnections.size();
    }

    @Override
    public void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {
        var connectionChannel = ctx.channel().parent();
        boolean firstRequest = seenConnections.add(connectionChannel.id());

        // Send normal response
        var headers = new DefaultHttp2Headers();
        headers.status("200");
        headers.set("content-type", "text/plain");
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        headers.setInt("content-length", body.length);
        ctx.write(new DefaultHttp2HeadersFrame(headers));
        ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(body), true));

        // Send GOAWAY after first request (with delay to let response complete)
        if (firstRequest) {
            ctx.executor()
                    .schedule(
                            () -> connectionChannel.writeAndFlush(new DefaultHttp2GoAwayFrame(Http2Error.NO_ERROR)),
                            100,
                            java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }
}
