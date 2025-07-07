/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import java.util.Set;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.amazon.smithy.java.mcp.cli.ConfigUtils;
import software.amazon.smithy.java.mcp.cli.ExecutionContext;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;

@Command(name = "uninstall", description = "Uninstall a MCP server.")
public class UninstallBundle extends SmithyMcpCommand {

    @Parameters(description = "Id of the MCP server to uninstall.")
    String id;

    @Option(names = {"--clients"},
            description = "Names of client configs to update. If not specified all client configs registered would be updated.")
    Set<String> clients = Set.of();

    @Override
    protected void execute(ExecutionContext context) throws Exception {
        var config = context.config();
        if (!config.getToolBundles().containsKey(id)) {
            System.out.println("No such MCP server is installed. Nothing to do.");
            return;
        }
        ConfigUtils.removeMcpBundle(config, id);
        System.out.println("Uninstalled MCP bundle: " + id);
        ConfigUtils.removeFromClientConfigs(config, id, clients);
        System.out.println("Updated client configs");
    }
}
