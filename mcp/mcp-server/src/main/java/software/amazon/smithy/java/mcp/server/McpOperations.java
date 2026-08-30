/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Domain operations available to protocol implementations.
 *
 * <p>Protocol implementations decide whether an operation exists and how its result is
 * projected. Implementations delegate supported operations to this interface.
 */
@SmithyUnstableApi
public interface McpOperations {
    McpOutcome initialize(McpCall.Initialize call, McpRequestContext context);

    McpOutcome ping(McpCall.Ping call, McpRequestContext context);

    McpOutcome discover(McpCall.Discover call, McpRequestContext context);

    McpOutcome listTools(McpCall.ListTools call, McpRequestContext context);

    McpOutcome callTool(McpCall.CallTool call, McpRequestContext context);

    McpOutcome listPrompts(McpCall.ListPrompts call, McpRequestContext context);

    McpOutcome getPrompt(McpCall.GetPrompt call, McpRequestContext context);

    McpOutcome complete(McpCall.Complete call, McpRequestContext context);

    McpOutcome setLogLevel(McpCall.SetLogLevel call, McpRequestContext context);

    McpOutcome readResource(McpCall.ReadResource call, McpRequestContext context);
}
