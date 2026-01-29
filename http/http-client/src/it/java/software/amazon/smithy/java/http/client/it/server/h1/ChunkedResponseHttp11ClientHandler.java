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
import java.util.List;

/**
 * HTTP/1.1 handler that sends chunked transfer encoding response.
 */
public class ChunkedResponseHttp11ClientHandler implements Http11ClientHandler {

    private final List<String> chunks;

    public ChunkedResponseHttp11ClientHandler(List<String> chunks) {
        this.chunks = chunks;
    }

    @Override
    public void onFullRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        ctx.write(response);

        for (String chunk : chunks) {
            ctx.write(new DefaultHttpContent(Unpooled.wrappedBuffer(chunk.getBytes(StandardCharsets.UTF_8))));
        }
        ctx.writeAndFlush(new DefaultLastHttpContent());
    }
}
