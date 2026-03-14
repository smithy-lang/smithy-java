/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h1;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import java.nio.charset.StandardCharsets;

/**
 * Handler that sends partial response then closes connection.
 * Used to test client handling of server closing mid-response.
 */
public class PartialResponseHttp11ClientHandler implements Http11ClientHandler {
    private final String partialBody;
    private final int advertisedLength;

    public PartialResponseHttp11ClientHandler(String partialBody, int advertisedLength) {
        this.partialBody = partialBody;
        this.advertisedLength = advertisedLength;
    }

    @Override
    public void onFullRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
        var response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers().set("content-length", advertisedLength);
        response.headers().set("content-type", "text/plain");
        ctx.write(response);

        // Send partial body (less than advertised)
        ctx.write(new DefaultHttpContent(Unpooled.copiedBuffer(partialBody, StandardCharsets.UTF_8)));
        ctx.flush();

        // Close connection without sending full body
        ctx.close();
    }
}
