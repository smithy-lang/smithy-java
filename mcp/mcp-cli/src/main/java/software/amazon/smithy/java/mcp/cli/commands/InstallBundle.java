/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli.commands;

import static picocli.CommandLine.Command;

import java.io.IOException;
import java.util.Set;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.amazon.smithy.java.mcp.cli.ClientsInput;
import software.amazon.smithy.java.mcp.cli.ConfigUtils;
import software.amazon.smithy.java.mcp.cli.ExecutionContext;
import software.amazon.smithy.java.mcp.cli.SmithyMcpCommand;
import software.amazon.smithy.java.mcp.cli.model.Config;

@Command(name = "install", description = "Downloads and adds a bundle from the MCP registry.")
public class InstallBundle extends SmithyMcpCommand {

    @Option(names = {"-r", "--registry"},
            description = "Name of the registry to list the bundles from. If not provided it will use the default registry.")
    String registryName;

    @Parameters(description = "Names of the MCP bundles to install.")
    Set<String> names;

    @ArgGroup
    ClientsInput clientsInput;

    @Override
    protected void execute(ExecutionContext context) throws IOException {
        var registry = context.registry();
        var config = context.config();
        for (var name : names) {
            var bundle = registry.getMcpBundle(name);
            ConfigUtils.addMcpBundle(config, name, bundle);
            ConfigUtils.createWrapperAndUpdateClientConfigs(name, bundle, config, clientsInput);
            System.out.println("Successfully installed " + name);
        }
    }

    @Override
    protected String registryToUse(Config config) {
        return registryName;
    }
}
