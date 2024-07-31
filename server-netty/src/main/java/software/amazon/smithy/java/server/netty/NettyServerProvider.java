/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.netty;

import software.amazon.smithy.java.server.ServerBuilder;
import software.amazon.smithy.java.server.ServerProvider;

public class NettyServerProvider implements ServerProvider {
    @Override
    public String name() {
        return "smithy-java-netty-server";
    }

    @Override
    public ServerBuilder<?> serverBuilder() {
        return new NettyServerBuilder();
    }

    @Override
    public int priority() {
        return 0;
    }
}
