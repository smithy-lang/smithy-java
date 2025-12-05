/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.server;

import io.netty.channel.ChannelHandlerContext;

@FunctionalInterface
public interface Http11ClientHandlerFactory {

    Http11ClientHandler create(ChannelHandlerContext ctx);
}
