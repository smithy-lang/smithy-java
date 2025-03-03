/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import software.amazon.smithy.java.cli.CliError;
import software.amazon.smithy.java.cli.CliUtils;
import software.amazon.smithy.java.cli.StandardOptions;
import software.amazon.smithy.java.cli.arguments.Arguments;
import software.amazon.smithy.java.cli.formatting.CliPrinter;
import software.amazon.smithy.java.cli.formatting.ColorBuffer;
import software.amazon.smithy.java.cli.formatting.ColorFormatter;
import software.amazon.smithy.java.cli.formatting.ColorTheme;

/**
 * Command with subcommands.
 */
public abstract class AggregateCommand implements Command {
    private final List<Command> commands = new ArrayList<>();

    /**
     * @return Commands that this aggregate command can call.
     */
    public List<Command> commands() {
        return commands;
    };

    /**
     * Add a command to this aggregate command.
     */
    public void addCommand(Command command) {
        commands.add(command);
    }

    /**
     * Allows aggregate commands to update command environment before delegating to subcommands.
     *
     * @param env Command environment to modify.
     */
    protected void modifyEnv(Command.Env env) {}

    @Override
    public int execute(Arguments arguments, Env env) {
        var command = arguments.shift();

        // If no command was given, then finish parsing to check if -h, --help was given.
        if (command == null) {
            arguments.complete();
            var standardOptions = arguments.expectReceiver(StandardOptions.class);
            // TODO: Can this version and help parsing be consolidated for all commands?
            if (standardOptions.version()) {
                StandardOptions.printVersion(env);
                return 0;
            }
            if (standardOptions.help()) {
                printHelp(arguments, env.formatter(), env.stdout(), env.theme());
                return 0;
            } else {
                printHelp(arguments, env.formatter(), env.stdout(), env.theme());
                return 1;
            }
        }

        // Find and execute commands
        for (Command c : commands()) {
            if (c.name().equals(command)) {
                modifyEnv(env);
                return c.execute(arguments, env);
            }
        }
        throw new CliError("Unknown argument or command: " + command);
    }

    // TODO: Support Levenstien distance search for likely command
    private void printHelp(Arguments args, ColorFormatter formatter, CliPrinter printer, ColorTheme theme) {
        // Get a clean output from the command.
        printer.append(System.lineSeparator()).flush();
        try (ColorBuffer buffer = ColorBuffer.of(formatter, printer)) {
            // Title
            buffer.println(name(), theme.commandTitle());

            // Description
            if (description() != null && !description().isEmpty()) {
                buffer.println("Description:", theme.title());
                buffer.println("    " + description(), theme.description());
            }

            // Usage
            buffer.println("Usage:", theme.title());
            if (parent() != null) {
                buffer.println(
                        String.format(
                                "    %s %s [-h | --help] [-v | --version] <subcommand> [<args>]",
                                formatter.style(parent(), theme.rootCommandUsage()),
                                formatter.style(name(), theme.subcommandUsage())));
            } else {
                buffer.println(
                        String.format(
                                "    %s [-h | --help] [-v | --version] <command> <subcommand> [<args>]",
                                formatter.style(name(), theme.rootCommandUsage())));
            }

            // Commands
            buffer.println("Commands:", theme.title());
            var longestName = longestCommandLength();
            for (Command command : commands()) {
                buffer.println(
                        String.format(
                                "    %-" + longestName + "s %s",
                                formatter.style(command.name(), theme.literal()),
                                command.description() != null ? command.description() : ""));
            }

            // Flags
            CliUtils.printFlags(buffer, formatter, theme, args);
        }
    }

    private int longestCommandLength() {
        int longestName = 0;
        for (Command command : commands()) {
            if (command.name().length() + 12 > longestName) {
                longestName = command.name().length() + 12;
            }
        }
        return longestName;
    }

    public static AggregateCommand of(String name, String description, Command... commands) {
        return new CommandRouter(name, description, Arrays.asList(commands));
    }

    private static final class CommandRouter extends AggregateCommand {
        private final String name;
        private final String description;

        public CommandRouter(String name, String description, List<Command> commands) {
            this.name = name;
            this.description = description;
            for (Command command : commands) {
                addCommand(command);
            }
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }
    }
}
