/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import software.amazon.smithy.java.server.core.Orchestrator;
import software.amazon.smithy.java.server.core.ProtocolResolver;

public class NettyChannelInitializer extends ChannelInitializer<Channel> {

    private final Orchestrator orchestrator;
    private final ProtocolResolver protocolResolver;

    public NettyChannelInitializer(Orchestrator orchestrator, ProtocolResolver protocolResolver) {
        this.orchestrator = orchestrator;
        this.protocolResolver = protocolResolver;
    }

    @Override
    protected void initChannel(Channel channel) throws Exception {

    }
}
