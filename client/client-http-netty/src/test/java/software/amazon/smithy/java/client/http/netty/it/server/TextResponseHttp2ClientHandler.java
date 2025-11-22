/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.it.server;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import io.netty.util.CharsetUtil;

public class TextResponseHttp2ClientHandler implements Http2ClientHandler {
    private final String message;

    public TextResponseHttp2ClientHandler(String message) {
        this.message = message;
    }

    @Override
    public void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {
        var responseHeaders = new DefaultHttp2Headers();
        responseHeaders.status("200");
        responseHeaders.set("content-type", "text/plain");
        ctx.write(new DefaultHttp2HeadersFrame(responseHeaders, false));
        var content = Unpooled.copiedBuffer(message, CharsetUtil.UTF_8);
        ctx.writeAndFlush(new DefaultHttp2DataFrame(content, true));
    }

    @Override
    public void onDataFrame(ChannelHandlerContext ctx, Http2DataFrame frame) {

    }

    @Override
    public void onException(ChannelHandlerContext ctx, Throwable cause) {

    }
}
