/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.generators;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.framework.knowledge.ImplicitErrorIndex;
import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.client.ClientSymbolProperties;
import software.amazon.smithy.java.codegen.sections.ApplyDocumentation;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.OperationIndex;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
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
            writer.pushState(new ClassSection(directive.shape(), ApplyDocumentation.NONE));
            var template = """
                final class ${impl:T} extends ${client:T} implements ${interface:T} {
                    ${?hasImplicitErrors}${implicitErrorRegistry:C|}

                    ${/hasImplicitErrors}
                    ${impl:T}(${interface:T}.Builder builder) {
                        super(builder);
                    }

                    ${?hasImplicitErrors}
                    @Override
                    protected TypeRegistry implicitErrorRegistry() {
                        return IMPLICIT_ERROR_REGISTRY;
                    }${/hasImplicitErrors}

                    ${operations:C|}
                }
                """;
            writer.putContext("client", Client.class);
            writer.putContext("interface", symbol);
            writer.putContext("impl", impl);
            writer.putContext("future", CompletableFuture.class);
            writer.putContext("completionException", CompletionException.class);
            var implicitErrorIndex = ImplicitErrorIndex.of(directive.model());
            var implicitErrors = implicitErrorIndex.getImplicitErrorsForService(directive.service());
            writer.putContext("hasImplicitErrors", !implicitErrors.isEmpty());
            writer.putContext(
                "implicitErrorRegistry",
                new ImplicitErrorRegistryGenerator(writer, directive.context(), implicitErrors)
            );
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
            var template = """
                @Override
                public ${?async}${future:T}<${/async}${output:T}${?async}>${/async} ${name:L}(${input:T} input, ${overrideConfig:T} overrideConfig) {${^async}
                    try {
                        ${/async}return call(input, new ${operation:T}(), overrideConfig)${^async}.join()${/async};${^async}
                    } catch (${completionException:T} e) {
                        throw unwrapAndThrow(e);
                    }${/async}
                }
                """;
            writer.putContext("async", async);
            writer.putContext("overrideConfig", RequestOverrideConfig.class);
            var opIndex = OperationIndex.of(model);
            for (var operation : TopDownIndex.of(model).getContainedOperations(service)) {
                writer.pushState();
                writer.putContext("name", StringUtils.uncapitalize(CodegenUtils.getDefaultName(operation, service)));
                writer.putContext("operation", symbolProvider.toSymbol(operation));
                writer.putContext("input", symbolProvider.toSymbol(opIndex.expectInputShape(operation)));
                writer.putContext("output", symbolProvider.toSymbol(opIndex.expectOutputShape(operation)));
                writer.write(template);
                writer.popState();
            }
            writer.popState();
        }
    }

    private record ImplicitErrorRegistryGenerator(
        JavaWriter writer,
        CodeGenerationContext context,
        Set<ShapeId> ImplicitErrorIds
    ) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("typeRegistry", TypeRegistry.class);
            writer.write(
                "private static final ${typeRegistry:T} IMPLICIT_ERROR_REGISTRY = ${typeRegistry:T}.builder()"
            );
            writer.indent();
            for (var implicitErrorId : ImplicitErrorIds) {
                writer.pushState();
                writer.putContext("errorType", context.errorMapping(implicitErrorId));
                writer.write(".putType(${errorType:T}.$$ID, ${errorType:T}.class, ${errorType:T}::builder)");
                writer.popState();
            }
            writer.writeWithNoFormatting(".build();");
            writer.dedent();
            writer.popState();
        }
    }
}
