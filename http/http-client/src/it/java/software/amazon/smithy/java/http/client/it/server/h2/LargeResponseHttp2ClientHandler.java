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

/**
 * HTTP/2 handler that sends a large response body in chunks.
 */
public class LargeResponseHttp2ClientHandler implements Http2ClientHandler {

    private final int responseSize;
    private final int chunkSize;

    public LargeResponseHttp2ClientHandler(int responseSize, int chunkSize) {
        this.responseSize = responseSize;
        this.chunkSize = chunkSize;
    }

    @Override
    public void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {
        // Send response headers
        var headers = new DefaultHttp2Headers();
        headers.status("200");
        headers.set("content-type", "application/octet-stream");
        headers.setInt("content-length", responseSize);
        ctx.write(new DefaultHttp2HeadersFrame(headers));

        // Send body in chunks
        int remaining = responseSize;
        while (remaining > 0) {
            int size = Math.min(chunkSize, remaining);
            byte[] chunk = new byte[size];
            // Fill with predictable pattern for verification
            for (int i = 0; i < size; i++) {
                chunk[i] = (byte) ((responseSize - remaining + i) & 0xFF);
            }
            boolean endStream = (remaining - size) == 0;
            ctx.write(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(chunk), endStream));
            remaining -= size;
        }
        ctx.flush();
    }

    @Override
    public void onException(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
