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

    McpCursorPage<McpToolDescriptor> listTools(String cursor);

    McpCursorPage<McpPromptDescriptor> listPrompts(String cursor);

    Map<String, McpRemoteClient> remoteClients();

    boolean containsServer(String id);

    void bindTransport(
            Consumer<JsonRpcRequest> notificationWriter,
            Consumer<JsonRpcResponse> responseWriter
    );

    /**
     * Binds a transport that applies its own delivery policy to catalog change events.
     */
    void bindTransport(CatalogListener listener, Consumer<JsonRpcResponse> responseWriter);

    /**
     * Receives catalog changes and other notifications relayed from remote peers.
     */
    interface CatalogListener {
        /**
         * A tools or prompts list changed. {@code original} is the remote notification that
         * reported it, or {@code null} for a local change.
         */
        void onListChanged(McpMethod.Standard method, JsonRpcRequest original);

        void onNotification(JsonRpcRequest notification);
    }

    void initializeRemoteClients(McpProtocol protocol);

    default void ensureRemoteCatalogLoaded() {
        ensureRemoteCatalogLoaded(BuiltInProtocols.protocol(ProtocolVersion.defaultVersion()));
    }

    void ensureRemoteCatalogLoaded(McpProtocol protocol);

    void addService(String id, Service service);

    void addRemoteClient(McpRemoteClient client);

    Map<String, String> headerParameters(String toolName);

    @Override
    void close();
}
