/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Map;
import java.util.function.Consumer;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.server.Service;

/**
 * Aggregates local services and remote MCP peers behind one immutable-snapshot source.
 */
interface McpSources extends AutoCloseable {
    McpSourceSnapshot snapshot();

    McpToolDescriptor tool(String name);

    McpPromptDescriptor prompt(String normalizedName);

    Map<String, McpRemoteClient> remoteClients();

    boolean containsServer(String id);

    void bindTransport(
            Consumer<JsonRpcRequest> notificationWriter,
            Consumer<JsonRpcResponse> responseWriter
    );

    void initializeRemoteClients(JsonRpcRequest request, McpProtocol protocol);

    void ensureRemoteCatalogLoaded();

    void addService(String id, Service service);

    void addRemoteClient(McpRemoteClient client);

    Map<String, String> headerParameters(String toolName);

    @Override
    void close();
}
