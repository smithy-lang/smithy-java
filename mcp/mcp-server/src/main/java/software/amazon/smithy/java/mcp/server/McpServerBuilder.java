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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public final class McpServerBuilder {

    InputStream is;
    OutputStream os;
    Map<String, Service> services = new HashMap<>();
    List<McpServerProxy> proxyList = new ArrayList<>();
    String name;
    ToolFilter toolFilter = (server, tool) -> true;
    McpService mcpService;

    McpServerBuilder() {}

    public McpServerBuilder stdio() {
        this.is = System.in;
        this.os = System.out;
        return this;
    }

    public McpServerBuilder input(InputStream is) {
        this.is = is;
        return this;
    }

    public McpServerBuilder output(OutputStream os) {
        this.os = os;
        return this;
    }

    public McpServerBuilder name(String name) {
        this.name = name;
        return this;
    }

    public Server build() {
        validate();
        // Create McpService before building McpServer
        this.mcpService = McpService.builder()
                .services(services)
                .proxyList(proxyList)
                .name(name != null ? name : "mcp-server")
                .toolFilter(toolFilter)
                .build();
        return new McpServer(this);
    }

    public McpServerBuilder addService(String id, Service service) {
        services.put(id, service);
        return this;
    }

    public McpServerBuilder addService(Map<String, Service> services) {
        this.services.putAll(services);
        return this;
    }

    public McpServerBuilder addService(McpServerProxy... proxy) {
        proxyList.addAll(Arrays.asList(proxy));
        return this;
    }

    public McpServerBuilder toolFilter(ToolFilter filter) {
        this.toolFilter = filter;
        return this;
    }

    private void validate() {
        Objects.requireNonNull(is, "MCP server input stream is required");
        Objects.requireNonNull(os, "MCP server output stream is required");
        if (services.isEmpty() && proxyList.isEmpty()) {
            throw new IllegalArgumentException("MCP server requires at least one service or proxy");
        }
    }
}
