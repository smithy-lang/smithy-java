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
import io.netty.handler.codec.http2.Http2HeadersFrame;
import java.nio.charset.StandardCharsets;

/**
 * HTTP/2 handler that sends partial response then closes connection.
 */
public class PartialResponseHttp2ClientHandler implements Http2ClientHandler {

    @Override
    public void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {
        var headers = new DefaultHttp2Headers();
        headers.status("200");
        headers.setInt("content-length", 1000); // Claim 1000 bytes
        ctx.write(new DefaultHttp2HeadersFrame(headers));

        // Send only partial data (100 bytes), don't set endStream
        byte[] partial = "partial".repeat(14).getBytes(StandardCharsets.UTF_8);
        ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(partial), false));

        // Close connection abruptly
        ctx.channel().parent().close();
    }
}
