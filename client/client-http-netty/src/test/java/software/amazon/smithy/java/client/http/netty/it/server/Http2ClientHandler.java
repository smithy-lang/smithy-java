/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.it.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;

public interface Http2ClientHandler {

    void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame);

    void onDataFrame(ChannelHandlerContext ctx, Http2DataFrame frame);

    void onException(ChannelHandlerContext ctx, Throwable cause);

}
