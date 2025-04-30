/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import java.util.List;
import java.util.ServiceLoader;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import software.amazon.smithy.java.mcp.cli.commands.Configure;
import software.amazon.smithy.java.mcp.cli.commands.StartServer;

/**
 * Main entry point for the Smithy MCP Command Line Interface.
 * <p>
 * This class configures and launches the CLI application using Picocli.
 * It discovers and registers all available configuration commands and
 * sets up the command hierarchy.
 */
@Command(name = "smithy-mcp", versionProvider = VersionProvider.class, mixinStandardHelpOptions = true,
        scope = CommandLine.ScopeType.INHERIT)
public class McpCli {

    public static void main(String[] args) {
        var configureCommand = new CommandLine(new Configure());
        discoverConfigurationCommands().forEach(configureCommand::addSubcommand);
        var commandLine = new CommandLine(new McpCli())
                .addSubcommand(new StartServer())
                .addSubcommand(configureCommand);
        commandLine.execute(args);
    }

    /**
     * Discovers and loads all ConfigurationCommand implementations using the Java ServiceLoader.
     *
     * @return A list of discovered configuration commands
     */
    private static List<ConfigurationCommand> discoverConfigurationCommands() {
        return ServiceLoader.load(ConfigurationCommand.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }
}
