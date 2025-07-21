/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.cli;

import static picocli.CommandLine.*;
import static software.amazon.smithy.java.mcp.cli.ConfigUtils.loadOrCreateConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.cli.model.Config;
import software.amazon.smithy.java.mcp.cli.model.TelemetryData;

/**
 * Base class for all Smithy MCP CLI commands.
 * <p>
 * This class implements the Callable interface to provide a consistent execution pattern
 * for all MCP CLI commands. It handles loading the configuration, executing the command,
 * and providing appropriate error handling.
 */
@Command(name = "dummy")
public abstract class SmithyMcpCommand implements Callable<Integer> {

    InternalLogger LOG = InternalLogger.getLogger(SmithyMcpCommand.class);

    private static final TelemetryPublisher TELEMETRY_PUBLISHER = findTelemetryPublisher();

    @Spec
    CommandSpec spec;

    @Override
    public final Integer call() {
        TelemetryData.Builder telemetry = TelemetryData.builder();
        String commandName = getCommandName(spec.commandLine());
        telemetry.command(commandName)
                .cliVersion(spec.version()[0]);

        try (var metrics = new CliMetrics(TELEMETRY_PUBLISHER, telemetry)) {
            var config = loadOrCreateConfig();
            execute(new ExecutionContext(config, RegistryUtils.getRegistry(registryToUse(config), config), metrics));
            return 0;
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid input : [" + e.getMessage() + "]");
            return 2;
        } catch (Exception e) {
            e.printStackTrace(System.out);
            return 1;
        }
    }

    private static String getCommandName(CommandLine commandLine) {
        List<String> commands = new ArrayList<>();
        while (commandLine != null) {
            commands.add(commandLine.getCommandName());
            commandLine = commandLine.getParent();
        }

        StringBuilder commandName = new StringBuilder();
        for (int i = commands.size() - 2; i >= 0; i--) {
            commandName.append(commands.get(i));
            if (i > 0)
                commandName.append(":");
        }
        return commandName.toString();
    }

    /**
     * Execute the command with the provided configuration.
     * <p>
     * Subclasses must implement this method to provide command-specific functionality.
     *
     * @param context {@link ExecutionContext}
     * @throws Exception If an error occurs during execution
     */
    protected abstract void execute(ExecutionContext context) throws Exception;

    protected String registryToUse(Config config) {
        return config.getDefaultRegistry();
    }

    private static TelemetryPublisher findTelemetryPublisher() {
        return ServiceLoader.load(TelemetryPublisherProvider.class)
                .findFirst()
                .map(TelemetryPublisherProvider::get)
                .orElse(d -> {});
    }
}
