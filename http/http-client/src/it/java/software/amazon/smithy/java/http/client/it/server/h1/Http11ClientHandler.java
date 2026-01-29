/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h1;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;

/**
 * Handler interface for HTTP/1.1 requests in the test server.
 */
public interface Http11ClientHandler {

    /**
     * Called when a complete HTTP request is received (headers + body aggregated).
     *
     * @param ctx the channel handler context
     * @param request the complete HTTP request
     */
    default void onFullRequest(ChannelHandlerContext ctx, FullHttpRequest request) {}

    /**
     * Called when HTTP request headers are received (streaming mode).
     *
     * @param ctx the channel handler context
     * @param request the HTTP request headers
     */
    default void onRequest(ChannelHandlerContext ctx, HttpRequest request) {}

    /**
     * Called when HTTP request body content is received (streaming mode).
     *
     * @param ctx the channel handler context
     * @param content the HTTP content chunk
     */
    default void onContent(ChannelHandlerContext ctx, HttpContent content) {}

    /**
     * Called when the last HTTP request body content is received (streaming mode).
     *
     * @param ctx the channel handler context
     * @param content the last HTTP content chunk
     */
    default void onLastContent(ChannelHandlerContext ctx, LastHttpContent content) {}

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
