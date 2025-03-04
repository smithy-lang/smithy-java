/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import software.amazon.smithy.java.cli.arguments.Arguments;
import software.amazon.smithy.java.cli.commands.AggregateCommand;
import software.amazon.smithy.java.cli.commands.Command;
import software.amazon.smithy.java.cli.formatting.CliPrinter;
import software.amazon.smithy.java.cli.formatting.ColorFormatter;
import software.amazon.smithy.java.cli.formatting.ColorTheme;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Base class for all Smithy Java CLIs.
 */
@SmithyInternalApi
public final class CLI {
    private final StandardOptions standardOptions = new StandardOptions();
    private final AggregateCommand command;
    private final CliConfig config;
    private final String version;

    private CLI(Builder builder) {
        // Apply any customer-provided plugins
        var cliConfigBuilder = builder.cliConfigBuilder;
        for (var plugin : builder.plugins) {
            plugin.configureCli(cliConfigBuilder);
        }
        this.config = cliConfigBuilder.build();
        this.command = Objects.requireNonNull(builder.command, "Command cannot be null");
        this.version = Objects.requireNonNullElseGet(builder.version, CLI::getCurrentDateVersion);
    }

    private static String getCurrentDateVersion() {
        return DateTimeFormatter.ISO_DATE.format(LocalDate.now());
    }

    /**
     * Used to execute a CLI instance within a `main` method in an entrypoint.
     */
    public static void execute(String[] args, Supplier<CLI> cliSupplier) {
        try {
            int exitCode = cliSupplier.get().run(args);
            // Only exit with a non-zero status on error since 0 is the default exit code.
            if (exitCode != 0) {
                System.exit(exitCode);
            }
        } catch (CliError e) {
            System.exit(e.code);
        } catch (Exception e) {
            System.exit(1);
        }
    }

    public int run(String[] args) {
        // CLI arguments haven't been parsed yet, so the CLI doesn't know if --force-color or --no-color
        // was passed. Defer the color setting implementation by asking StandardOptions before each write.
        var colorFormatter = ColorFormatter.createDelegated(standardOptions::colorSetting);

        // Create a client builder that can be configured by argument receivers or used by commands
        // to create a callable client
        var clientBuilder = CLIClient.builder();

        // If the customer added any top-level Client Plugins, add them here
        config.clientPlugins().forEach(clientBuilder::addPlugin);

        // If a static endpoint is specified add to client.
        if (config.endpoint() != null) {
            clientBuilder.endpointResolver(EndpointResolver.staticEndpoint(config.endpoint()));
        }

        // Initialize arguments container.
        // TODO: Do we need logging args wrapper like in Smithy CLI?
        var argEnv = new Arguments.Env(clientBuilder);
        var arguments = Arguments.of(args, argEnv);
        // Add defaults available to all commands
        arguments.addReceiver(standardOptions);

        // Add any custom argument receivers
        config.argumentReceivers().forEach(arguments::addReceiver);

        // Add any custom commands
        config.commands().forEach(command::addCommand);

        var theme = Objects.requireNonNullElse(config.theme(), ColorTheme.defaultTheme());
        try (
                var stdoutPrinter = CliPrinter.fromOutputStream(System.out);
                var stderrPrinter = CliPrinter.fromOutputStream(System.err)) {
            try {
                var cmdEnv = new Command.Env(
                        colorFormatter,
                        stdoutPrinter,
                        stderrPrinter,
                        theme,
                        version,
                        clientBuilder);
                return command.execute(arguments, cmdEnv);
            } catch (ModeledException exc) {
                // Flush all printers before writing exception
                stdoutPrinter.flush();
                stderrPrinter.flush();
                standardOptions.outputFormatter().writeShape(exc, stderrPrinter);
                throw CliError.wrap(exc);
            } catch (Exception exc) {
                // Flush all printers before writing exception
                stdoutPrinter.flush();
                stderrPrinter.flush();
                colorFormatter.printException(stderrPrinter, exc, theme.errorDescription());
                throw CliError.wrap(exc);
            }
        } catch (Exception exc) {
            throw CliError.wrap(exc);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private AggregateCommand command;
        private final List<CliPlugin> plugins = new ArrayList<>();
        private final CliConfig.Builder cliConfigBuilder = CliConfig.builder();
        private String version;

        public Builder command(AggregateCommand command) {
            this.command = command;
            return this;
        }

        public Builder addPlugin(CliPlugin plugin) {
            this.plugins.add(plugin);
            return this;
        }

        public Builder endpoint(String string) {
            this.cliConfigBuilder.endpoint(string);
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public CLI build() {
            return new CLI(this);
        }
    }
}
