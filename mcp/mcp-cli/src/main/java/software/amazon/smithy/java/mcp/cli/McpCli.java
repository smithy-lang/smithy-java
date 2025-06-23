/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import java.util.List;
import java.util.ServiceLoader;
import picocli.CommandLine;
import software.amazon.smithy.java.mcp.cli.commands.Configure;
import software.amazon.smithy.java.mcp.cli.commands.InstallBundle;
import software.amazon.smithy.java.mcp.cli.commands.ListBundles;
import software.amazon.smithy.java.mcp.cli.commands.StartServer;
import software.amazon.smithy.java.mcp.cli.commands.UninstallBundle;

/**
 * Main entry point for the Smithy MCP Command Line Interface.
 * <p>
 * This class configures and launches the CLI application using Picocli.
 * It discovers and registers all available configuration commands and
 * sets up the command hierarchy.
 */
public final class McpCli {

    private McpCli() {
        throw new UnsupportedOperationException();
    }

    public static void main(String[] args) {
        addSystemProperties();
        var configureCommand = new CommandLine(new Configure());
        discoverConfigurationCommands().forEach(configureCommand::addSubcommand);
        var commandLine = new CommandLine(new McpRegistry())
                .addSubcommand(new StartServer())
                .addSubcommand(new ListBundles())
                .addSubcommand(new InstallBundle())
                .addSubcommand(new UninstallBundle())
                .addSubcommand(configureCommand);
        System.exit(commandLine.execute(args));
    }

    private static void addSystemProperties() {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host"); //This is required for JavaHttpClientTransport.
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
