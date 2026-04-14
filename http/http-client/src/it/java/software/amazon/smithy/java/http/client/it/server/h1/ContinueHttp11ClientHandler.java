/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h1;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HTTP/1.1 handler that handles 100-continue correctly.
 */
public class ContinueHttp11ClientHandler implements Http11ClientHandler {

    private final String responseBody;
    private final AtomicReference<ByteBuf> capturedBody = new AtomicReference<>(Unpooled.buffer());

    public ContinueHttp11ClientHandler(String responseBody) {
        this.responseBody = responseBody;
    }

    public ByteBuf capturedBody() {
        return capturedBody.get();
    }

    @Override
    public void onFullRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        // Check for Expect: 100-continue and send 100 Continue when seen
        if (request.headers().contains(HttpHeaderNames.EXPECT, "100-continue", true)) {
            ctx.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE));
        }

        // Capture request body
        capturedBody.get().writeBytes(request.content());

        // Send final response
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        var response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.wrappedBuffer(body));
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.length);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        ctx.writeAndFlush(response);
    }
}
