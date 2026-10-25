/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Map;
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.java.server.Operation;

record McpToolDescriptor(
        ToolInfo info,
        String serverId,
        Target target,
        Map<String, String> headerParameters) {
    McpToolDescriptor {
        headerParameters = Map.copyOf(headerParameters);
    }

    sealed interface Target permits LocalTarget, RemoteTarget {}

    record LocalTarget(Operation operation) implements Target {}

    record RemoteTarget(McpRemoteClient client) implements Target {}
}
