/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.commands;

import software.amazon.smithy.java.cli.CLIClient;
import software.amazon.smithy.java.cli.arguments.Arguments;
import software.amazon.smithy.java.cli.formatting.CliPrinter;
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
     * TODO: Docs
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
}
