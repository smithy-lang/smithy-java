/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h1;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * HTTP/1.1 handler that sends chunked response with trailers.
 */
public class TrailerResponseHttp11ClientHandler implements Http11ClientHandler {

    private final String body;
    private final Map<String, String> trailers;

    public TrailerResponseHttp11ClientHandler(String body, Map<String, String> trailers) {
        this.body = body;
        this.trailers = trailers;
    }

    @Override
    public void onFullRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().set("trailer", String.join(", ", trailers.keySet()));
        ctx.write(response);

        // Send body chunk
        ctx.write(new DefaultHttpContent(Unpooled.wrappedBuffer(body.getBytes(StandardCharsets.UTF_8))));

        // Send last chunk with trailers
        var lastContent = new DefaultLastHttpContent();
        for (var entry : trailers.entrySet()) {
            lastContent.trailingHeaders().set(entry.getKey(), entry.getValue());
        }
        ctx.writeAndFlush(lastContent);
    }
}
