/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server.h2;

import io.netty.channel.ChannelHandlerContext;

@FunctionalInterface
public interface Http2ClientHandlerFactory {
    Http2ClientHandler create(ChannelHandlerContext ctx);
}
