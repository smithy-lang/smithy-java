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
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HTTP/2 handler that sends response in chunks with delays.
 */
public class StreamingResponseHttp2ClientHandler implements Http2ClientHandler {

    private final List<String> chunks;
    private final long delayBetweenChunksMs;

    public StreamingResponseHttp2ClientHandler(List<String> chunks, long delayBetweenChunksMs) {
        this.chunks = chunks;
        this.delayBetweenChunksMs = delayBetweenChunksMs;
    }

    @Override
    public void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {
        var headers = new DefaultHttp2Headers();
        headers.status("200");
        headers.set("content-type", "text/plain");
        ctx.writeAndFlush(new DefaultHttp2HeadersFrame(headers));

        // Send chunks with delays
        for (int i = 0; i < chunks.size(); i++) {
            final int index = i;
            final boolean isLast = (i == chunks.size() - 1);
            ctx.executor().schedule(() -> {
                byte[] data = chunks.get(index).getBytes(StandardCharsets.UTF_8);
                ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(data), isLast));
            }, delayBetweenChunksMs * i, TimeUnit.MILLISECONDS);
        }
    }
}
