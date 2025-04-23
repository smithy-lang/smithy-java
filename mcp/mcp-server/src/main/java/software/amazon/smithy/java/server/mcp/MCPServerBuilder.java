/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.mcp;

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
public final class MCPServerBuilder {

    InputStream is;
    OutputStream os;
    List<Service> serviceList = new ArrayList<>();
    String name;

    MCPServerBuilder() {}

    public MCPServerBuilder stdio() {
        this.is = System.in;
        this.os = System.out;
        return this;
    }

    public MCPServerBuilder input(InputStream is) {
        this.is = is;
        return this;
    }

    public MCPServerBuilder output(OutputStream os) {
        this.os = os;
        return this;
    }

    public MCPServerBuilder name(String name) {
        this.name = name;
        return this;
    }

    public Server build() {
        validate();
        return new MCPServer(this);
    }

    public MCPServerBuilder addService(Service... service) {
        serviceList.addAll(Arrays.asList(service));
        return this;
    }

    private void validate() {
        Objects.requireNonNull(is, "MCP server input stream is required");
        Objects.requireNonNull(is, "MCP server output stream is required");
        if (serviceList.isEmpty()) {
            throw new IllegalArgumentException("MCP server requires at least one service");
        }
    }
}
