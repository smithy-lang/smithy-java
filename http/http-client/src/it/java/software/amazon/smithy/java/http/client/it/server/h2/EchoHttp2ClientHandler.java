/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h2;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;

/**
 * HTTP/2 handler that echoes each request DATA frame back as a response DATA frame before the request
 * stream ends: response HEADERS on request HEADERS, then every inbound DATA frame mirrored, with END_STREAM
 * on the response only once the request's END_STREAM is seen. Drives full-duplex streaming, since the
 * response body is produced while the request body is still open.
 */
public class EchoHttp2ClientHandler implements Http2ClientHandler {

    @Override
    public void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {
        var headers = new DefaultHttp2Headers();
        headers.status("200");
        headers.set("content-type", "application/octet-stream");
        // Response headers only; the body is streamed back as request DATA frames arrive.
        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(headers, false));

        // An empty-bodied request (END_STREAM on HEADERS) gets an immediate empty, end-of-stream response.
        if (frame.isEndStream()) {
            ctx.writeAndFlush(new DefaultHttp2DataFrame(true));
        }
    }

    @Override
    public void onDataFrame(ChannelHandlerContext ctx, Http2DataFrame frame) {
        // Echo this chunk's payload straight back, retaining the request's END_STREAM flag so the response
        // ends exactly when the request does.
        ByteBuf echoed = Unpooled.copiedBuffer(frame.content());
        ctx.writeAndFlush(new DefaultHttp2DataFrame(echoed, frame.isEndStream()));
    }
}
