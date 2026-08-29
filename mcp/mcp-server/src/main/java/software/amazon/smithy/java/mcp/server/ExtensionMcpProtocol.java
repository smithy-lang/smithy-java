/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Open SPI branch for externally implemented MCP protocols.
 */
@SmithyUnstableApi
public non-sealed interface ExtensionMcpProtocol extends McpProtocol {}
