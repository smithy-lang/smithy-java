/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import java.util.List;
import java.util.ServiceLoader;
import picocli.CommandLine;
import picocli.CommandLine.HelpCommand;
import software.amazon.smithy.java.mcp.cli.commands.Configure;
import software.amazon.smithy.java.mcp.cli.commands.CreateBundle;
import software.amazon.smithy.java.mcp.cli.commands.CreateGenericBundle;
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
        var createCommand = new CommandLine(new CreateBundle())
                .addSubcommand(new CreateGenericBundle());
        discoverCreateBundleCommands().forEach(createCommand::addSubcommand);
        var commandLine = new CommandLine(new McpRegistry())
                .addSubcommand(new HelpCommand())
                .addSubcommand(new StartServer())
                .addSubcommand(new ListBundles())
                .addSubcommand(new InstallBundle())
                .addSubcommand(new UninstallBundle())
                .addSubcommand(configureCommand)
                .addSubcommand(createCommand);
        discoverSmithyMcpCommands().forEach(commandLine::addSubcommand);
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

    /**
     * Discovers and loads all AbstractCreateBundle implementations using the Java ServiceLoader.
     *
     * @return A list of discovered configuration commands
     */
    private static List<AbstractCreateBundle> discoverCreateBundleCommands() {
        return ServiceLoader.load(AbstractCreateBundle.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }

    /**
     * Discovers and loads all AbstractCreateBundle implementations using the Java ServiceLoader.
     *
     * @return A list of discovered configuration commands
     */
    private static List<SmithyMcpCommand> discoverSmithyMcpCommands() {
        return ServiceLoader.load(SmithyMcpCommand.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }
}
