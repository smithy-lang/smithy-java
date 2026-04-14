/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h2;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.util.CharsetUtil;
import java.util.Map;

/**
 * HTTP/2 handler that sends response with trailer headers.
 */
public class TrailerResponseHttp2ClientHandler implements Http2ClientHandler {

    private final String body;
    private final Map<String, String> trailers;

    public TrailerResponseHttp2ClientHandler(String body, Map<String, String> trailers) {
        this.body = body;
        this.trailers = trailers;
    }

    @Override
    public void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {
        if (frame.isEndStream()) {
            sendResponse(ctx);
        }
    }

    @Override
    public void onDataFrame(ChannelHandlerContext ctx, Http2DataFrame frame) {
        if (frame.isEndStream()) {
            sendResponse(ctx);
        }
    }

    private void sendResponse(ChannelHandlerContext ctx) {
        // Send response headers (not end of stream)
        var responseHeaders = new DefaultHttp2Headers();
        responseHeaders.status("200");
        responseHeaders.set("content-type", "text/plain");
        ctx.write(new DefaultHttp2HeadersFrame(responseHeaders, false));

        // Send body (not end of stream)
        var content = Unpooled.copiedBuffer(body, CharsetUtil.UTF_8);
        ctx.write(new DefaultHttp2DataFrame(content, false));

        // Send trailers (end of stream)
        var trailerHeaders = new DefaultHttp2Headers();
        for (var entry : trailers.entrySet()) {
            trailerHeaders.set(entry.getKey(), entry.getValue());
        }
        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(trailerHeaders, true));
    }
}
