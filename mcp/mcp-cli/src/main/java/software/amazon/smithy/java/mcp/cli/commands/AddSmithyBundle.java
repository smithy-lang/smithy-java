/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import software.amazon.smithy.java.mcp.cli.AbstractAddBundle;
import software.amazon.smithy.java.mcp.cli.CliBundle;

/**
 * Command to add a Smithy tool bundle to the MCP configuration.
 * <p>
 * This command allows users to add a new Smithy tool bundle to their MCP configuration.
 * Currently under development and hidden from the CLI help.
 */
@Command(name = "add-smithy-bundle", description = "Add a smithy bundle.", hidden = true)
//TODO implement and unhide
public class AddSmithyBundle extends AbstractAddBundle {

    @CommandLine.Option(names = "--overwrite",
            description = "Overwrite existing config",
            defaultValue = "false")
    protected boolean overwrite;

    @CommandLine.Option(names = {"-n", "--name"}, description = "Name of the tool bundle.", required = true)
    protected String name;

    @Override
    protected CliBundle getNewToolConfig() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    protected String getToolBundleName() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    @Override
    protected boolean canOverwrite() {
        throw new UnsupportedOperationException("Not implemented yet.");
    }
}
