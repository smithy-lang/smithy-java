/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import picocli.CommandLine.Command;

/**
 * Command group for configuration-related commands in the Smithy MCP CLI.
 * <p>
 * This class serves as a container for all subcommands related to configuring
 * the MCP CLI, such as adding tool bundles.
 */
@Command(name = "configure", description = "Configure the Smithy MCP CLI", subcommands = {
        AddSmithyBundle.class,
        ListBundles.class,
        AddBundle.class
})
public final class Configure {

}
