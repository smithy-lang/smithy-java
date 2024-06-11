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
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.OperationIndex;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.StringUtils;

@SmithyInternalApi
public final class ClientInterfaceGenerator
    implements Consumer<GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        // Write synchronous interface
        writeForSymbol(directive.symbol(), directive);
        // Write async interface
        writeForSymbol(directive.symbol().expectProperty(ClientSymbolProperties.ASYNC_SYMBOL), directive);
    }

    private static void writeForSymbol(
        Symbol symbol,
        GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        directive.context()
            .writerDelegator()
            .useFileWriter(symbol.getDeclarationFile(), symbol.getNamespace(), writer -> {
                writer.pushState(new ClassSection(directive.shape()));
                var template = """
                    public interface ${interface:T} {

                        ${operations:C|}
                    }
                    """;
                writer.putContext("interface", symbol);
                writer.putContext(
                    "operations",
                    new OperationMethodGenerator(
                        writer,
                        directive.shape(),
                        directive.symbolProvider(),
                        symbol,
                        directive.model()
                    )
                );
                writer.write(template);
                writer.popState();
            });
    }

    private record OperationMethodGenerator(
        JavaWriter writer, ServiceShape service, SymbolProvider symbolProvider, Symbol symbol, Model model
    ) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            var template = """
                default ${?async}${future:T}<${/async}${output:T}${?async}>${/async} ${name:L}(${input:T} input) {
                    return ${name:L}(input, ${context:T}.create());
                }

                ${?async}${future:T}<${/async}${output:T}${?async}>${/async} ${name:L}(${input:T} input, ${context:T} context);
                """;
            writer.putContext("async", symbol.expectProperty(ClientSymbolProperties.ASYNC));
            writer.putContext("context", Context.class);
            writer.putContext("future", CompletableFuture.class);

            var opIndex = OperationIndex.of(model);
            for (var operation : TopDownIndex.of(model).getContainedOperations(service)) {
                writer.pushState();
                writer.putContext("name", StringUtils.uncapitalize(CodegenUtils.getDefaultName(operation, service)));
                writer.putContext("input", symbolProvider.toSymbol(opIndex.expectInputShape(operation)));
                writer.putContext("output", symbolProvider.toSymbol(opIndex.expectOutputShape(operation)));
                writer.write(template);
                writer.popState();
            }
            writer.popState();
        }
    }
}
