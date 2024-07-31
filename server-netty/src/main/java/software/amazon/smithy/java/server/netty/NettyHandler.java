/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.netty;

import io.netty.channel.ChannelDuplexHandler;
import software.amazon.smithy.java.server.core.Orchestrator;
import software.amazon.smithy.java.server.core.ProtocolResolver;

final class NettyHandler extends ChannelDuplexHandler {

    private final Orchestrator orchestrator;
    private final ProtocolResolver resolver;

    NettyHandler(Orchestrator orchestrator, ProtocolResolver resolver) {
        this.orchestrator = orchestrator;
        this.resolver = resolver;
    }
}
