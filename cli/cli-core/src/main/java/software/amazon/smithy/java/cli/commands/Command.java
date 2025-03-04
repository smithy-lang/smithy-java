/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.commands;

import software.amazon.smithy.java.cli.CLIClient;
import software.amazon.smithy.java.cli.arguments.ArgumentReceiver;
import software.amazon.smithy.java.cli.arguments.Arguments;
import software.amazon.smithy.java.cli.formatting.CliPrinter;
import software.amazon.smithy.java.cli.formatting.ColorBuffer;
import software.amazon.smithy.java.cli.formatting.ColorFormatter;
import software.amazon.smithy.java.cli.formatting.ColorTheme;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Represents a CLI command.
 */
public interface Command {

    /**
     * Gets the name of the command.
     *
     * <p>The returned name should contain no spaces or special characters.
     *
     * @return Returns the command name.
     */
    String name();

    /**
     * @return parent command if this is a nested command, otherwise null.
     */
    default String parent() {
        return null;
    };

    /**
     * @return Description of Command to use for help text.
     */
    default String description() {
        return null;
    }

    /**
     * Executes the command using the provided arguments.
     *
     * @param arguments CLI arguments.
     * @param env CLI environment settings like stdout, stderr, etc.
     * @return Returns the exit code.
     */
    int execute(Arguments arguments, Env env);

    /**
     * Environment information such as stdout, stderr available to commands.
     */
    @SmithyInternalApi
    record Env(
            ColorFormatter formatter,
            CliPrinter stdout,
            CliPrinter stderr,
            ColorTheme theme,
            String version,
            CLIClient.Builder clientBuilder) {}

    static void printFlags(ColorBuffer buffer, ColorFormatter formatter, ColorTheme theme, Arguments args) {
        buffer.println("Flags:", theme.title());
        var shortOffset = shortFlagOffset(args);
        var longestFlag = longestFlagLength(args);
        for (ArgumentReceiver receiver : args.getReceivers()) {
            for (var flagEntry : receiver.flags().entrySet()) {
                var flag = flagEntry.getKey();
                var template = flag.shortFlag() == null
                        ? "    %" + shortOffset + "s   %-" + longestFlag + "s  %s"
                        : "    %" + shortOffset + "s, %-" + longestFlag + "s %s";
                buffer.println(
                        String.format(
                                template,
                                formatter.style(flag.shortFlag() != null ? flag.shortFlag() : "", theme.literal()),
                                formatter.style(flag.longFlag(), theme.literal()),
                                formatter.style(flagEntry.getValue(), theme.description())));
            }
        }
    }

    private static int shortFlagOffset(Arguments args) {
        int longestFlag = 0;
        for (ArgumentReceiver receiver : args.getReceivers()) {
            for (var flag : receiver.flags().keySet()) {
                if (flag.shortFlag() != null && flag.shortFlag().length() + 8 > longestFlag) {
                    longestFlag = flag.shortFlag().length() + 8;
                }
            }
        }
        return longestFlag;
    }

    private static int longestFlagLength(Arguments args) {
        int longestFlag = 0;
        for (ArgumentReceiver receiver : args.getReceivers()) {
            for (var flag : receiver.flags().keySet()) {
                if (flag.longFlag().length() + 10 > longestFlag) {
                    longestFlag = flag.longFlag().length() + 10;
                }
            }
        }
        return longestFlag;
    }
}
