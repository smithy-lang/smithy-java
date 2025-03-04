/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import software.amazon.smithy.java.cli.arguments.ArgumentReceiver;
import software.amazon.smithy.java.cli.arguments.Arguments;
import software.amazon.smithy.java.cli.commands.Command;
import software.amazon.smithy.java.cli.formatting.AnsiColorFormatter;
import software.amazon.smithy.java.cli.formatting.ColorBuffer;
import software.amazon.smithy.java.cli.formatting.OutputShapeFormatter;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;

/**
 * Common Options available to all commands.
 */
public final class StandardOptions implements ArgumentReceiver {

    // TODO: Do we need all of these?
    public static final String DEBUG = "--debug";
    public static final String QUIET = "--quiet";
    public static final String NO_COLOR = "--no-color";
    public static final String FORCE_COLOR = "--force-color";
    public static final String LOGGING = "--logging";
    public static final String OUTPUT_FORMAT = "--output";
    public static final String OUTPUT_FORMAT_SHORT = "-o";
    private static final String HELP_LONG = "--help";
    private static final String HELP_SHORT = "-h";
    private static final String VERSION_LONG = "--version";
    private static final String VERSION_SHORT = "-v";
    private static final String ENDPOINT_LONG = "--endpoint";
    private static final String ENDPOINT_SHORT = "-e";

    private Level logging = Level.WARNING;
    private boolean quiet;
    private boolean debug;
    private AnsiColorFormatter colorSetting = AnsiColorFormatter.AUTO;
    private OutputShapeFormatter outputFormatter = OutputShapeFormatter.JSON;
    private boolean help;
    private boolean version;

    @Override
    public boolean testOption(String name, Arguments.Env env) {
        switch (name) {
            case DEBUG:
                debug = true;
                quiet = false;
                // Automatically set logging level to ALL.
                logging = Level.ALL;
                return true;
            case QUIET:
                quiet = true;
                debug = false;
                // Automatically set logging level to SEVERE.
                logging = Level.SEVERE;
                return true;
            case NO_COLOR:
                colorSetting = AnsiColorFormatter.NO_COLOR;
                return true;
            case FORCE_COLOR:
                colorSetting = AnsiColorFormatter.FORCE_COLOR;
                return true;
            case HELP_SHORT, HELP_LONG:
                this.help = true;
                return true;
            case VERSION_SHORT, VERSION_LONG:
                this.version = true;
                return true;
            default:
                return false;
        }
    }

    @Override
    public Consumer<List<String>> testParameter(String name, Arguments.Env env) {
        return switch (name) {
            case LOGGING -> args -> {
                if (args.size() != 1) {
                    throw new IllegalArgumentException("Expected exactly one argument for `--logging` parameter");
                }
                var value = args.get(0);
                try {
                    logging = Level.parse(value);
                } catch (IllegalArgumentException e) {
                    throw new CliError("Invalid logging level: " + value);
                }
            };
            case OUTPUT_FORMAT, OUTPUT_FORMAT_SHORT -> args -> {
                if (args.size() != 1) {
                    throw new IllegalArgumentException("Expected exactly one argument for `--output` parameter");
                }
                outputFormatter = OutputShapeFormatter.fromString(args.get(0));
            };
            case ENDPOINT_SHORT, ENDPOINT_LONG -> (args) -> {
                if (args.size() != 1) {
                    throw new IllegalArgumentException("Only one endpoint argument is allowed");
                }
                env.clientBuilder().endpointResolver(EndpointResolver.staticEndpoint(args.get(0)));
            };
            default -> null;
        };
    }

    @Override
    public Map<Flag, String> flags() {
        // TODO: Add all
        return Map.of(
                new Flag(ENDPOINT_LONG, ENDPOINT_SHORT),
                " Override commands default URL with the given URL");
    }

    public Level logging() {
        return logging;
    }

    public boolean quiet() {
        return quiet;
    }

    public boolean debug() {
        return debug;
    }

    public AnsiColorFormatter colorSetting() {
        return colorSetting;
    }

    public OutputShapeFormatter outputFormatter() {
        return outputFormatter;
    }

    public boolean help() {
        return help;
    }

    public boolean version() {
        return version;
    }

    public static void printVersion(Command.Env env) {
        try (ColorBuffer buffer = ColorBuffer.of(env.formatter(), env.stdout())) {
            buffer.println(
                    String.format(
                            "version: %s",
                            env.formatter().style(env.version(), env.theme().literal())));
        }
    }
}
