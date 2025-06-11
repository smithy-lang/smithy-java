/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import picocli.CommandLine;

@CommandLine.Command(name = "mcp-registry", versionProvider = VersionProvider.class, mixinStandardHelpOptions = true,
        scope = CommandLine.ScopeType.INHERIT)
final class McpRegistry {}
