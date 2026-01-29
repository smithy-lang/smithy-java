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

/**
 * HTTP/1.1 handler that sends configurable status code.
 */
public class StatusCodeHttp11ClientHandler implements Http11ClientHandler {

    private final int statusCode;

    public StatusCodeHttp11ClientHandler(int statusCode) {
        this.statusCode = statusCode;
    }

    @Override
    public void onFullRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        var status = HttpResponseStatus.valueOf(statusCode);
        var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, Unpooled.EMPTY_BUFFER);

        // Some status codes must not have body
        if (statusCode != 204 && statusCode != 304) {
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        }

        ctx.writeAndFlush(response);
    }
}
