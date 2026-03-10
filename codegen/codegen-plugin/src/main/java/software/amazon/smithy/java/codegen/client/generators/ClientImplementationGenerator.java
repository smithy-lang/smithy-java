/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.generators;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.framework.knowledge.ImplicitErrorIndex;
import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.codegen.ClientSymbolProperties;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SyntheticServiceTransform;
import software.amazon.smithy.java.codegen.client.sections.ClientImplAdditionalMethodsSection;
import software.amazon.smithy.java.codegen.client.waiters.WaiterCodegenUtils;
import software.amazon.smithy.java.codegen.generators.TypeRegistryGenerator;
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

    private static final ShapeId WAITABLE_TRAIT = ShapeId.from("smithy.waiters#waitable");

    @Override
    public void accept(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        writeForSymbol(directive.symbol(), directive);
    }

    public static void writeForSymbol(
            Symbol symbol,
            GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        var impl = symbol.expectProperty(ClientSymbolProperties.CLIENT_IMPL);
        directive.context().writerDelegator().useFileWriter(impl.getDefinitionFile(), impl.getNamespace(), writer -> {
            writer.pushState(new ClassSection(directive.shape(), ApplyDocumentation.NONE));
            var template = """
                    final class ${impl:T} extends ${client:T} implements ${interface:T} {${?implicitErrors}
                        ${typeRegistry:C|}${/implicitErrors}

                        ${impl:T}(${interface:T}.Builder builder) {
                            super(builder);
                        }

                        ${operations:C|}

                        ${?implicitErrors}@Override
                        protected ${typeRegistryClass:T} typeRegistry() {
                            return TYPE_REGISTRY;
                        }${/implicitErrors}
                    }
                    """;
            writer.putContext("client", Client.class);
            writer.putContext("interface", symbol);
            writer.putContext("impl", impl);
            writer.putContext("future", CompletableFuture.class);
            writer.putContext("typeRegistryClass", TypeRegistry.class);
            writer.putContext("completionException", CompletionException.class);
            var errorSymbols = getImplicitErrorSymbols(
                    directive.symbolProvider(),
                    directive.model(),
                    directive.service());
            writer.putContext("implicitErrors", !errorSymbols.isEmpty());
            writer.putContext(
                    "typeRegistry",
                    new TypeRegistryGenerator(writer, errorSymbols));
            writer.putContext(
                    "operations",
                    new OperationMethodGenerator(
                            writer,
                            directive.shape(),
                            directive.symbolProvider(),
                            directive.model(),
                            directive.settings()));
            writer.write(template);
            writer.popState();
        });
    }

    private record OperationMethodGenerator(
            JavaWriter writer,
            ServiceShape service,
            SymbolProvider symbolProvider,
            Model model,
            JavaCodegenSettings settings) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            var template =
                    """
                            @Override
                            public ${output:T} ${name:L}(${input:T} input, ${overrideConfig:T} overrideConfig) {
                                try {
                                    return call(input, ${operation:T}.instance(), overrideConfig);
                                } catch (${completionException:T} e) {
                                    throw unwrapAndThrow(e);
                                }
                            }
                            """;
            writer.putContext("overrideConfig", RequestOverrideConfig.class);
            var opIndex = OperationIndex.of(model);
            for (var operation : TopDownIndex.of(model).getContainedOperations(service)) {
                if (operation.getId().getNamespace().equals(SyntheticServiceTransform.SYNTHETIC_NAMESPACE)) {
                    continue;
                }
                writer.pushState();
                writer.putContext("name", StringUtils.uncapitalize(CodegenUtils.getDefaultName(operation, service)));
                writer.putContext("operation", symbolProvider.toSymbol(operation));
                writer.putContext("input", symbolProvider.toSymbol(opIndex.expectInputShape(operation)));
                writer.putContext("output", symbolProvider.toSymbol(opIndex.expectOutputShape(operation)));
                writer.write(template);
                writer.popState();
            }

            // Generate waiter() implementation for clients with waitable operations
            if (!model.getShapesWithTrait(WAITABLE_TRAIT).isEmpty()) {
                var clientSymbol = symbolProvider.toSymbol(service);
                writer.pushState();
                writer.putContext("container", WaiterCodegenUtils.getWaiterSymbol(clientSymbol, settings));
                writer.write("""
                        @Override
                        public ${container:T} waiter() {
                            return new ${container:T}(this);
                        }
                        """);
                writer.popState();
            }

            writer.injectSection(new ClientImplAdditionalMethodsSection(service));
            writer.popState();
        }
    }

    // TODO: Move into common CodegenUtils once ImplicitError index is available from smithy-model
    private static List<Symbol> getImplicitErrorSymbols(
            SymbolProvider symbolProvider,
            Model model,
            ServiceShape service
    ) {
        var implicitIndex = ImplicitErrorIndex.of(model);
        List<Symbol> symbols = new ArrayList<>();
        for (var errorId : implicitIndex.getImplicitErrorsForService(service)) {
            var shape = model.expectShape(errorId);
            symbols.add(symbolProvider.toSymbol(shape));
        }
        return symbols;
    }
}
