/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp.demo;

import java.io.InputStream;
import java.io.OutputStream;
import software.amazon.smithy.java.client.inspector.mcp.InspectorLogHandler;
import software.amazon.smithy.java.client.inspector.mcp.InspectorPlugin;
import software.amazon.smithy.java.client.inspector.mcp.InspectorService;
import software.amazon.smithy.java.client.inspector.mcp.InspectorState;
import software.amazon.smithy.java.mcp.server.McpServer;
import software.amazon.smithy.java.server.Server;

/**
 * A self-contained demo an agent can drive over MCP. Before the server starts serving, a
 * {@link DemoTrafficDriver} makes a fixed, agent-invisible mix of real REST-JSON client calls
 * (standing in for the developer's own app). The stdio MCP server then exposes only the
 * <b>SdkInspector</b> tools ({@code ListCalls}, {@code GetCall}, {@code QueryTelemetry},
 * {@code QueryLogs}, {@code InjectFault}, breakpoints, …).
 *
 * <p>The agent's job is to <em>characterize traffic it did not author</em>: inspect the recorded
 * calls, explain the success / the retried 429 / the persistent 500, and optionally inject a fault
 * or set a breakpoint to probe further. There is deliberately no traffic-generation tool, so the
 * agent cannot script its own answer.
 */
public final class InspectorDemoServer {

    private InspectorDemoServer() {}

    /** Build the combined demo MCP server over the given streams, sharing one InspectorState. */
    public static Server create(InputStream in, OutputStream out) {
        var state = new InspectorState();
        // Capture the SDK's internal (SLF4J→JUL) logs so QueryLogs has something to show.
        InspectorLogHandler.attachToRoot(state);
        // Generate a FIXED, agent-invisible traffic mix up front, standing in for the developer's
        // own application making real SDK calls. The agent cannot influence this — it can only
        // inspect what already happened. There is deliberately no traffic-generation MCP tool.
        new DemoTrafficDriver(new InspectorPlugin(state)).runFixedTraffic();
        return McpServer.builder()
                .name("smithy-sdk-inspector-demo")
                .input(in)
                .output(out)
                .addService("sdk-inspector", new InspectorService(state))
                .build();
    }

    /** stdio entrypoint for wiring into an MCP client such as Claude Code. */
    public static void main(String[] args) throws InterruptedException {
        var server = create(System.in, System.out);
        server.start();
        // start() spawns a listener thread and returns; keep the process alive to serve requests.
        Thread.currentThread().join();
    }
}
