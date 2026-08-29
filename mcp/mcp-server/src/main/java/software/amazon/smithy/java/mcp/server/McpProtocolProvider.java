/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Collection;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Service-provider interface for discovering external MCP protocols.
 *
 * <p>Providers are registered through
 * {@code META-INF/services/software.amazon.smithy.java.mcp.server.McpProtocolProvider}.
 */
@SmithyUnstableApi
public interface McpProtocolProvider {
    Collection<? extends ExtensionMcpProtocol> protocols();
}
