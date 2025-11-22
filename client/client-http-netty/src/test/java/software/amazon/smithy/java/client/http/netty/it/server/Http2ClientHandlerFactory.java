/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.it.server;

import io.netty.channel.ChannelHandlerContext;

@FunctionalInterface
public interface Http2ClientHandlerFactory {

    Http2ClientHandler create(ChannelHandlerContext ctx);
}
