/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cli.commands;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import software.amazon.smithy.java.cli.CliUtils;
import software.amazon.smithy.java.cli.StandardOptions;
import software.amazon.smithy.java.cli.arguments.ArgumentReceiver;
import software.amazon.smithy.java.cli.arguments.Arguments;
import software.amazon.smithy.java.cli.formatting.CliPrinter;
import software.amazon.smithy.java.cli.formatting.ColorBuffer;
import software.amazon.smithy.java.cli.formatting.ColorFormatter;
import software.amazon.smithy.java.cli.formatting.ColorTheme;
import software.amazon.smithy.java.cli.serde.CliArgumentDeserializer;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.TraitKey;
import software.amazon.smithy.model.traits.DeprecatedTrait;

// TODO: How to handle potential member name conflicts?
public final class OperationCommand<I extends SerializableStruct, O extends SerializableStruct>
        implements Command, ArgumentReceiver {
    private final String parent;
    private final String name;
    private final ApiOperation<I, O> operation;
    private CliArgumentDeserializer deserializer;

    public OperationCommand(String parent, String name, ApiOperation<I, O> operation) {
        this.parent = parent;
        this.name = name;
        this.operation = operation;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String parent() {
        return parent;
    }

    @Override
    public int execute(Arguments arguments, Env env) {
        // Add this operation as a receiver to get all input args.
        arguments.addReceiver(this);
        // Force parsing to complete
        arguments.complete();
        var standardOptions = arguments.expectReceiver(StandardOptions.class);
        if (standardOptions.version()) {
            StandardOptions.printVersion(env);
            return 0;
        }
        if (standardOptions.help()) {
            printHelp(arguments, env.formatter(), env.stdout(), env.theme());
            return 0;
        }
        var input = operation.inputBuilder().deserialize(deserializer()).build();
        if (standardOptions.debug()) {
            env.clientBuilder().addInterceptor(new DebuggingInterceptor(env.formatter(), env.stdout()));
        }
        var client = env.clientBuilder().build();
        var output = client.call(input, operation);
        standardOptions.outputFormatter().writeShape(output, env.stdout());
        return 0;
    }

    private CliArgumentDeserializer deserializer() {
        if (deserializer == null) {
            deserializer = new CliArgumentDeserializer();
        }
        return deserializer;
    }

    private void printHelp(Arguments args, ColorFormatter formatter, CliPrinter printer, ColorTheme theme) {
        // Get a clean output from the command.
        printer.append(System.lineSeparator()).flush();
        try (ColorBuffer buffer = ColorBuffer.of(formatter, printer)) {
            // Title
            buffer.println(name(), theme.commandTitle());
            buffer.println("Usage:", theme.title());
            buffer.println(
                    String.format(
                            "    %s %s [-h | --help] [-v | --version] [<args>]",
                            formatter.style(parent(), theme.rootCommandUsage()),
                            formatter.style(name(), theme.subcommandUsage())));

            // Top-level inputs
            buffer.println("Inputs:", theme.title());
            for (var memberSchema : operation.inputSchema().members()) {
                // TODO: nicely space these
                var memberStyle = memberSchema.hasTrait(TraitKey.get(DeprecatedTrait.class))
                        ? theme.deprecated()
                        : theme.literal();
                buffer.println(
                        String.format(
                                "    %s (%s)",
                                formatter.style("--" + CliUtils.kebabCase(memberSchema.memberName()), memberStyle),
                                formatter.style(memberSchema.type().toString(), theme.muted())));
            }

            // Flags
            Command.printFlags(buffer, formatter, theme, args);
        }
    }

    @Override
    public Consumer<List<String>> testParameter(String name, Arguments.Env env) {
        for (var memberSchema : operation.inputSchema().members()) {
            var memberCommand = "--" + CliUtils.kebabCase(memberSchema.memberName());
            if (name.equals(memberCommand)) {
                return (args) -> deserializer().putArgs(memberSchema, args);
            }
        }
        return null;
    }

    @Override
    public Map<Flag, String> flags() {
        return Map.of();
    }
}
