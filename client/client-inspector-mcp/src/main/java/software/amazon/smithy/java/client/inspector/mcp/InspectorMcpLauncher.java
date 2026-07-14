/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp;

import java.io.InputStream;
import java.io.OutputStream;
import software.amazon.smithy.java.mcp.server.McpServer;
import software.amazon.smithy.java.server.Server;

/**
 * Builds an MCP server that exposes the {@link InspectorService} tools for a given
 * {@link InspectorState}.
 *
 * <p>stdio is the default transport: a coding agent spawns the SDK-under-test process and speaks
 * MCP over its stdin/stdout. Because the MCP server and the {@link InspectorInterceptor} share one
 * JVM and one {@link InspectorState}, the {@code Resume} tool wakes a parked client thread by
 * counting down a latch directly — no cross-process IPC. For a shared or networked deployment, wire
 * the same {@code InspectorService} into an HTTP MCP server instead (breakpoints should stay stdio).
 */
public final class InspectorMcpLauncher {

    private InspectorMcpLauncher() {}

    /** Build a stdio MCP server exposing the inspector for the given state. Call {@link Server#start()}. */
    public static Server stdio(InspectorState state) {
        return McpServer.builder()
                .name("smithy-sdk-inspector")
                .stdio()
                .addService("sdk-inspector", new InspectorService(state))
                .build();
    }

    /** Build an MCP server over explicit streams (for tests or custom transports). */
    public static Server streams(InspectorState state, InputStream in, OutputStream out) {
        return McpServer.builder()
                .name("smithy-sdk-inspector")
                .input(in)
                .output(out)
                .addService("sdk-inspector", new InspectorService(state))
                .build();
    }

    /**
     * Convenience entrypoint: attach JUL log capture to the shared state and serve it over stdio.
     * Point this at a process whose clients use the flag-gated {@link InspectorPlugin}.
     */
    public static void main(String[] args) {
        var state = InspectorRegistry.shared();
        InspectorLogHandler.attachToRoot(state);
        stdio(state).start();
    }
}
