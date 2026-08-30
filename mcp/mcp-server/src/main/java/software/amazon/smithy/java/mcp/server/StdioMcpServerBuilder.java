/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public final class StdioMcpServerBuilder {
    InputStream input;
    OutputStream output;
    McpEngine engine;

    private final Map<String, Service> services = new HashMap<>();
    private final List<McpRemoteClient> remoteClients = new ArrayList<>();
    private final Map<McpProtocolId, ExtensionMcpProtocol> protocols = new LinkedHashMap<>();
    private final Map<McpProtocolId, ExtensionMcpProtocol> protocolOverrides = new LinkedHashMap<>();
    private McpInterceptor interceptor = McpInterceptor.NOOP;
    private String name = "mcp-server";
    private String version = "1.0.0";
    private ToolFilter toolFilter = (server, tool) -> true;
    private McpMetricsObserver metricsObserver;
    private boolean discoverProtocols = true;

    StdioMcpServerBuilder() {}

    public StdioMcpServerBuilder stdio() {
        input = System.in;
        output = System.out;
        return this;
    }

    public StdioMcpServerBuilder input(InputStream input) {
        this.input = input;
        return this;
    }

    public StdioMcpServerBuilder output(OutputStream output) {
        this.output = output;
        return this;
    }

    /**
     * Uses a prebuilt engine instead of constructing one from this builder's source options.
     */
    public StdioMcpServerBuilder engine(McpEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine");
        return this;
    }

    public StdioMcpServerBuilder name(String name) {
        this.name = Objects.requireNonNull(name, "name");
        return this;
    }

    public StdioMcpServerBuilder version(String version) {
        this.version = Objects.requireNonNull(version, "version");
        return this;
    }

    public StdioMcpServerBuilder addService(String id, Service service) {
        services.put(id, service);
        return this;
    }

    public StdioMcpServerBuilder addServices(Map<String, Service> services) {
        this.services.putAll(services);
        return this;
    }

    public StdioMcpServerBuilder addRemoteClient(McpRemoteClient... clients) {
        remoteClients.addAll(Arrays.asList(clients));
        return this;
    }

    public StdioMcpServerBuilder toolFilter(ToolFilter filter) {
        toolFilter = Objects.requireNonNull(filter, "filter");
        return this;
    }

    public StdioMcpServerBuilder metricsObserver(McpMetricsObserver observer) {
        metricsObserver = observer;
        return this;
    }

    public StdioMcpServerBuilder interceptor(McpInterceptor interceptor) {
        this.interceptor = Objects.requireNonNull(interceptor, "interceptor");
        return this;
    }

    public StdioMcpServerBuilder addProtocol(ExtensionMcpProtocol protocol) {
        putProtocol(protocols, protocol, "protocol");
        return this;
    }

    public StdioMcpServerBuilder overrideProtocol(ExtensionMcpProtocol protocol) {
        putProtocol(protocolOverrides, protocol, "protocol override");
        return this;
    }

    public StdioMcpServerBuilder discoverProtocols(boolean discoverProtocols) {
        this.discoverProtocols = discoverProtocols;
        return this;
    }

    public StdioMcpServer build() {
        Objects.requireNonNull(input, "MCP server input stream is required");
        Objects.requireNonNull(output, "MCP server output stream is required");
        if (engine == null) {
            if (services.isEmpty() && remoteClients.isEmpty()) {
                throw new IllegalArgumentException("MCP server requires an engine, service, or remote client");
            }

            var engineBuilder = McpEngine.builder()
                    .services(services)
                    .remoteClients(remoteClients)
                    .name(name)
                    .version(version)
                    .toolFilter(toolFilter)
                    .metricsObserver(metricsObserver)
                    .interceptor(interceptor)
                    .discoverProtocols(discoverProtocols);
            protocols.values().forEach(engineBuilder::addProtocol);
            protocolOverrides.values().forEach(engineBuilder::overrideProtocol);
            engine = engineBuilder.build();
        } else if (!services.isEmpty()
                || !remoteClients.isEmpty()
                || !protocols.isEmpty()
                || !protocolOverrides.isEmpty()) {
            throw new IllegalStateException("Cannot combine a prebuilt engine with builder-managed sources");
        }
        return new StdioMcpServer(this);
    }

    private void putProtocol(
            Map<McpProtocolId, ExtensionMcpProtocol> destination,
            ExtensionMcpProtocol protocol,
            String kind
    ) {
        Objects.requireNonNull(protocol, kind);
        Objects.requireNonNull(protocol.id(), kind + " id");
        if (destination.put(protocol.id(), protocol) != null) {
            throw new IllegalArgumentException(
                    "Duplicate MCP " + kind + ": " + protocol.id().identifier());
        }
    }
}
