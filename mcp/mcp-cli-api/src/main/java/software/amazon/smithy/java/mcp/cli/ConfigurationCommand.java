/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

/**
 * Base class for all Smithy MCP CLI configuration commands.
 * <p>
 * This class extends SmithyMcpCommand to provide a common base for all commands
 * that modify the MCP configuration. Subclasses should implement the execute method.
 */
public abstract class ConfigurationCommand extends SmithyMcpCommand {

}
