/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.cli.generators;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.java.cli.CLI;
import software.amazon.smithy.java.cli.CliPlugin;
import software.amazon.smithy.java.cli.commands.AggregateCommand;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.cli.CliCodegenSettings;
import software.amazon.smithy.java.codegen.cli.EntryPointGenerationContext;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.SmithyGenerated;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class EntryPointGenerator implements Consumer<EntryPointGenerationContext> {

    @Override
    public void accept(EntryPointGenerationContext context) {
        var symbol = getEntrypointSymbol(context.settings());
        context.writerDelegator().useSymbolWriter(symbol, writer -> {
            writer.pushState();
            var template = """
                    @${smithyGenerated:T}
                    public class ${shape:T} {
                        /**
                         * Executes the CLI.
                         *
                         * @param args Arguments to parse and execute.
                         */
                        public static void main(String... args) {
                            ${cli:T}.execute(args, ${shape:T}::create);
                        }

                        /**
                         * Creates a new instance of the CLI.
                         *
                         * @return Returns the {@link ${cli:T}} instance.
                         */
                        public static ${cli:T} create() {
                            return ${cli:T}.builder()
                                .command(${command:C})${#plugins}
                                .addPlugin(new ${value:T}())${/plugins}${?endpoint}
                                .endpoint(${endpoint:S})${/endpoint}
                                .build();
                        }
                    }
                    """;
            writer.putContext("smithyGenerated", SmithyGenerated.class);
            writer.putContext("shape", symbol);
            writer.putContext("cli", CLI.class);
            writer.putContext("command", new CommandGenerator(writer, context.serviceSymbols(), context.settings()));
            writer.putContext("plugins", resolveCliPlugins(context.settings()));
            writer.putContext("endpoint", context.settings().endpoint());
            writer.write(template);
            writer.popState();
        });
    }

    // TODO: Should these be checks in the settings object?
    private static List<Class<? extends CliPlugin>> resolveCliPlugins(CliCodegenSettings settings) {
        List<Class<? extends CliPlugin>> result = new ArrayList<>();
        for (var receiverFqn : settings.plugins()) {
            result.add(CodegenUtils.getImplementationByName(CliPlugin.class, receiverFqn));
        }
        return result;
    }

    private record CommandGenerator(
            JavaWriter writer,
            List<Symbol> services,
            CliCodegenSettings settings) implements Runnable {

        @Override
        public void run() {
            // Single service using service command as root
            if (!settings.multiServiceCli()) {
                writer.writeInline("new $T(null)", services.get(0));
                return;
            }
            writer.pushState();
            var template = """
                    ${aggregate:T}.of(${name:S}, ${description:S}, ${#services}
                            new ${value:T}(${name:S})${^key.last},${/key.last}${/services})
                    """;
            writer.putContext("aggregate", AggregateCommand.class);
            writer.putContext("name", settings.name());
            writer.putContext("description", settings.description() != null ? settings.description() : "");
            writer.putContext("services", services);
            writer.writeInline(template);
            writer.popState();
        }
    }

    private static Symbol getEntrypointSymbol(CliCodegenSettings settings) {
        return Symbol.builder()
                .name("CliEntryPoint")
                .putProperty(SymbolProperties.IS_PRIMITIVE, false)
                .namespace(settings.namespace(), ".")
                .definitionFile(format("./%s/CliEntryPoint.java", settings.namespace().replace(".", "/")))
                .build();
    }
}
