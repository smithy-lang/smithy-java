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

@Command(name = "uninstall", description = "Uninstall a MCP bundle.")
public class UninstallBundle extends SmithyMcpCommand {

    @Parameters(description = "Name of the MCP bundle to uninstall.")
    String name;

    @Option(names = {"--clients"},
            description = "Names of client configs to update. If not specified all client configs registered would be updated.")
    Set<String> clients = Set.of();

    @Override
    protected void execute(ExecutionContext context) throws Exception {
        var config = context.config();
        if (!config.getToolBundles().containsKey(name)) {
            System.out.println("No such MCP bundle exists. Nothing to do.");
            return;
        }
        ConfigUtils.removeMcpBundle(config, name);
        System.out.println("Uninstalled MCP bundle: " + name);
        ConfigUtils.removeFromClientConfigs(config, name, clients);
        System.out.println("Updated client configs");
    }
}
