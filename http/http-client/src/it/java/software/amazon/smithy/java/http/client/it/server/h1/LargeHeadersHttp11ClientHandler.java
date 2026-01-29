/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h1;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;

/**
 * HTTP/1.1 handler that sends response with large headers.
 */
public class LargeHeadersHttp11ClientHandler implements Http11ClientHandler {

    private final String body;
    private final int headerCount;
    private final int headerValueSize;

    public LargeHeadersHttp11ClientHandler(String body, int headerCount, int headerValueSize) {
        this.body = body;
        this.headerCount = headerCount;
        this.headerValueSize = headerValueSize;
    }

    @Override
    public void onFullRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        var response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(bodyBytes));
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, bodyBytes.length);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");

        // Add large headers
        String largeValue = "x".repeat(headerValueSize);
        for (int i = 0; i < headerCount; i++) {
            response.headers().set("x-large-header-" + i, largeValue);
        }

        ctx.writeAndFlush(response);
    }
}
