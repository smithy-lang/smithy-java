/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h2;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2DataFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;

/**
 * Handler interface for HTTP/2 requests in the test server.
 */
public interface Http2ClientHandler {

    /**
     * Called when HTTP/2 HEADERS frame is received.
     *
     * @param ctx the channel handler context
     * @param frame the headers frame
     */
    default void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {}

    /**
     * Called when HTTP/2 DATA frame is received.
     *
     * @param ctx the channel handler context
     * @param frame the data frame
     */
    default void onDataFrame(ChannelHandlerContext ctx, Http2DataFrame frame) {}

    /**
     * Called when an exception occurs during request processing.
     *
     * @param ctx the channel handler context
     * @param cause the exception
     */
    default void onException(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
