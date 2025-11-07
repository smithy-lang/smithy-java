/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.ssl.SslCloseCompletionEvent;

/**
 * Closes the channel upon receiving a SslCloseCompletion event.
 */
class NettySslCloseCompletionHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        // TODO: check if the channel is still in use before closing it
        if (evt instanceof SslCloseCompletionEvent) {
            ctx.channel().close();
        }
        super.userEventTriggered(ctx, evt);
    }
}
