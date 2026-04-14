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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP/2 handler that delays response and tracks concurrent streams.
 */
public class DelayedResponseHttp2ClientHandler implements Http2ClientHandler {

    private final String responseBody;
    private final long delayMs;
    private final AtomicInteger concurrentStreams = new AtomicInteger();
    private volatile int maxObservedConcurrent = 0;

    public DelayedResponseHttp2ClientHandler(String responseBody, long delayMs) {
        this.responseBody = responseBody;
        this.delayMs = delayMs;
    }

    public int maxObservedConcurrent() {
        return maxObservedConcurrent;
    }

    @Override
    public void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {
        int current = concurrentStreams.incrementAndGet();
        synchronized (this) {
            if (current > maxObservedConcurrent) {
                maxObservedConcurrent = current;
            }
        }

        ctx.executor().schedule(() -> {
            concurrentStreams.decrementAndGet();

            var headers = new DefaultHttp2Headers();
            headers.status("200");
            headers.set("content-type", "text/plain");
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            headers.setInt("content-length", body.length);
            ctx.write(new DefaultHttp2HeadersFrame(headers));
            ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(body), true));
        }, delayMs, TimeUnit.MILLISECONDS);
    }
}
