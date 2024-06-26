/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.generators;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.client.ClientSymbolProperties;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.client.core.Client;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.OperationIndex;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.StringUtils;

@SmithyInternalApi
public final class ClientImplementationGenerator
    implements Consumer<GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        // Write Synchronous implementation
        writeForSymbol(directive.symbol(), directive);
        // Write Async implementation
        writeForSymbol(directive.symbol().expectProperty(ClientSymbolProperties.ASYNC_SYMBOL), directive);
    }

    public static void writeForSymbol(
        Symbol symbol,
        GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        var impl = symbol.expectProperty(ClientSymbolProperties.CLIENT_IMPL);
        directive.context().writerDelegator().useFileWriter(impl.getDefinitionFile(), impl.getNamespace(), writer -> {
            writer.pushState(new ClassSection(directive.shape()));
            var template = """
                final class ${impl:T} extends ${client:T} implements ${interface:T} {

                    ${impl:T}(${interface:T}.Builder builder) {
                        super(builder);
                    }

                    ${operations:C|}
                }
                """;
            writer.putContext("client", Client.class);
            writer.putContext("interface", symbol);
            writer.putContext("impl", impl);
            writer.putContext("future", CompletableFuture.class);
            writer.putContext(
                "operations",
                new OperationMethodGenerator(
                    writer,
                    directive.shape(),
                    directive.symbolProvider(),
                    directive.model(),
                    symbol.expectProperty(ClientSymbolProperties.ASYNC)
                )
            );
            writer.write(template);
            writer.popState();
        });
    }

    private record OperationMethodGenerator(
        JavaWriter writer, ServiceShape service, SymbolProvider symbolProvider, Model model, boolean async
    ) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("async", async);
            writer.putContext("context", Context.class);
            var opIndex = OperationIndex.of(model);
            for (var operation : TopDownIndex.of(model).getContainedOperations(service)) {
                writer.pushState();
                writer.putContext("name", StringUtils.uncapitalize(CodegenUtils.getDefaultName(operation, service)));
                writer.putContext("operation", symbolProvider.toSymbol(operation));
                writer.putContext("input", symbolProvider.toSymbol(opIndex.expectInputShape(operation)));
                writer.putContext("output", symbolProvider.toSymbol(opIndex.expectOutputShape(operation)));
                writer.write(
                    """
                        @Override
                        public ${?async}${future:T}<${/async}${output:T}${?async}>${/async} ${name:L}(${input:T} input, ${context:T} context) {
                            return call(input, new ${operation:T}(), context)${^async}.join()${/async};
                        }
                        """
                );
                writer.popState();
            }

            writer.popState();
        }
    }
}
