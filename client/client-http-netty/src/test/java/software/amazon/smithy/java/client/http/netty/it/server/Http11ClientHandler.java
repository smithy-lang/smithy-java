/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.it.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;

public interface Http11ClientHandler {

    void onFullRequest(ChannelHandlerContext ctx, FullHttpRequest request);

    void onRequest(ChannelHandlerContext ctx, HttpRequest request);

    void onContent(ChannelHandlerContext ctx, HttpContent content);

    void onLastContent(ChannelHandlerContext ctx, LastHttpContent content);

    void onException(ChannelHandlerContext ctx, Throwable cause);
}
