/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpServerCodec;
import software.amazon.smithy.java.server.core.OrchestratorGroup;
import software.amazon.smithy.java.server.core.ProtocolResolver;

final class ServerChannelInitializer extends ChannelInitializer<Channel> {

    private final OrchestratorGroup orchestratorGroup;
    private final ProtocolResolver protocolResolver;

    public ServerChannelInitializer(OrchestratorGroup selector, ProtocolResolver protocolResolver) {
        this.orchestratorGroup = selector;
        this.protocolResolver = protocolResolver;
    }

    @Override
    protected void initChannel(Channel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        configureHttp1Pipeline(pipeline);
        pipeline.addLast(new HttpRequestHandler(orchestratorGroup.next(), protocolResolver));
        pipeline.read();
    }

    private void configureHttp1Pipeline(ChannelPipeline pipeline) {
        pipeline.addLast("http1Codec", new HttpServerCodec());
    }
}
