/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2ResetFrame;
import io.netty.handler.codec.http2.Http2Error;
import io.netty.handler.codec.http2.Http2HeadersFrame;

/**
 * HTTP/2 handler that sends RST_STREAM after receiving headers.
 */
public class RstStreamHttp2ClientHandler implements Http2ClientHandler {

    private final Http2Error errorCode;

    public RstStreamHttp2ClientHandler(Http2Error errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {
        // Send RST_STREAM instead of response
        ctx.writeAndFlush(new DefaultHttp2ResetFrame(errorCode));
    }
}
