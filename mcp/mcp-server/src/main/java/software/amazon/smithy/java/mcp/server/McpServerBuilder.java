/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.server.Server;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public final class McpServerBuilder {

    InputStream is;
    OutputStream os;
    List<Service> serviceList = new ArrayList<>();
    String name;

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
        return new McpServer(this);
    }

    public McpServerBuilder addService(Service... service) {
        serviceList.addAll(Arrays.asList(service));
        return this;
    }

    public McpServerBuilder addServices(List<Service> services) {
        serviceList.addAll(services);
        return this;
    }

    private void validate() {
        Objects.requireNonNull(is, "MCP server input stream is required");
        Objects.requireNonNull(os, "MCP server output stream is required");
        if (serviceList.isEmpty()) {
            throw new IllegalArgumentException("MCP server requires at least one service");
        }
    }
}
