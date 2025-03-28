/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.server.generators;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.framework.knowledge.ImplicitErrorIndex;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.generators.IdStringGenerator;
import software.amazon.smithy.java.codegen.generators.SchemaFieldGenerator;
import software.amazon.smithy.java.codegen.generators.TypeRegistryGenerator;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.server.ServerSymbolProperties;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.java.framework.model.UnknownOperationException;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class ServiceGenerator implements
        Consumer<GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(
            GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        ServiceShape shape = directive.shape();
        TopDownIndex index = TopDownIndex.of(directive.model());
        List<OperationInfo> operationsInfo = index.getContainedOperations(shape)
                .stream()
                .map(o -> {
                    var inputSymbol =
                            directive.symbolProvider().toSymbol(directive.model().expectShape(o.getInputShape()));
                    var outputSymbol = directive.symbolProvider()
                            .toSymbol(directive.model().expectShape(o.getOutputShape()));
                    return new OperationInfo(directive.symbolProvider().toSymbol(o), o, inputSymbol, outputSymbol);
                })
                .toList();
        var operations = operationsInfo.stream().map(OperationInfo::symbol).toList();
        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));
            var template =
                    """
                            public final class ${service:T} implements ${serviceType:T} {

                                ${schema:C}

                                ${id:C|}

                                ${typeRegistry:C|}

                                ${properties:C|}

                                ${constructor:C|}

                                ${builder:C|}

                                @Override
                                @SuppressWarnings("unchecked")
                                public <I extends ${serializableStruct:T}, O extends ${serializableStruct:T}> ${operationHolder:T}<I, O> getOperation(String operationName) {
                                    ${getOperation:C|}
                                }

                                @Override
                                public ${operationList:T}<${operationHolder:T}<? extends ${serializableStruct:T}, ? extends ${serializableStruct:T}>> getAllOperations() {
                                     return allOperations;
                                }

                                @Override
                                public ${schemaClass:T} schema() {
                                     return $$SCHEMA;
                                }

                                @Override
                                public ${typeRegistryClass:T} typeRegistry() {
                                    return TYPE_REGISTRY;
                                }
                            }
                            """;
            writer.putContext("operationHolder", Operation.class);
            writer.putContext("serviceType", Service.class);
            writer.putContext("serializableStruct", SerializableStruct.class);
            writer.putContext("schemaClass", Schema.class);
            writer.putContext("service", directive.symbol());
            writer.putContext("id", new IdStringGenerator(writer, shape));
            writer.putContext("typeRegistryClass", TypeRegistry.class);
            var errorSymbols = getImplicitErrorSymbols(
                    directive.symbolProvider(),
                    directive.model(),
                    directive.service());
            writer.putContext(
                    "typeRegistry",
                    new TypeRegistryGenerator(writer, errorSymbols));
            writer.putContext(
                    "properties",
                    new PropertyGenerator(writer, shape, directive.symbolProvider(), operationsInfo, false));
            writer.putContext(
                    "constructor",
                    new ConstructorGenerator(writer, shape, directive.symbolProvider(), operations));
            writer.putContext(
                    "builder",
                    new BuilderGenerator(writer, shape, directive.symbolProvider(), operationsInfo));
            writer.putContext(
                    "getOperation",
                    new GetOperationGenerator(writer, shape, directive.symbolProvider(), operations));
            writer.putContext(
                    "schema",
                    new SchemaFieldGenerator(
                            directive,
                            writer,
                            shape));
            writer.putContext("operationList", List.class);
            writer.write(template);
            writer.popState();
        });

    }

    private record PropertyGenerator(
            JavaWriter writer,
            ServiceShape serviceShape,
            SymbolProvider symbolProvider,
            List<OperationInfo> operations,
            boolean forBuilder) implements Runnable {

        @Override
        public void run() {
            for (OperationInfo operation : operations) {
                var operationName = operation.symbol.getProperty(ServerSymbolProperties.OPERATION_FIELD_NAME);
                writer.pushState();
                writer.putContext("forBuilder", forBuilder);
                writer.putContext("input", operation.inputSymbol);
                writer.putContext("output", operation.outputSymbol);
                writer.putContext("fn", Function.class);
                writer.putContext("serviceType", Service.class);
                if (forBuilder) {
                    writer.write(
                            "private ${fn:T}<${serviceType:T}, ${operationHolder:T}<${input:T}, ${output:T}>> $L;",
                            operationName);
                } else {
                    writer.write(
                            "private final ${operationHolder:T}<${input:T}, ${output:T}> $L;",
                            operationName);
                }
                writer.popState();
            }
            if (!forBuilder) {
                writer.write(
                        "private final $T<$T<? extends ${serializableStruct:T}, ? extends ${serializableStruct:T}>> allOperations;",
                        List.class,
                        Operation.class);
            }
        }
    }

    private record ConstructorGenerator(
            JavaWriter writer,
            ServiceShape serviceShape,
            SymbolProvider symbolProvider,
            List<Symbol> operations) implements Runnable {
        @Override
        public void run() {
            writer.write(
                    """
                            private ${service:T}(Builder builder) {
                                ${C|}
                            }
                            """,
                    writer.consumer(w -> {
                        List<String> operationNames = new ArrayList<>();
                        for (Symbol operation : operations) {
                            var operationName = operation.expectProperty(ServerSymbolProperties.OPERATION_FIELD_NAME);
                            w.write("this.$1L = builder.$1L.apply(this);", operationName);
                            operationNames.add(operationName);
                        }
                        writer.pushState();
                        writer.putContext("operations", operationNames);
                        w.write(
                                "this.allOperations = $T.of(${#operations}${value:L}${^key.last}, ${/key.last}${/operations});",
                                List.class);
                        writer.popState();
                    }));
        }
    }

    private record BuilderGenerator(
            JavaWriter writer,
            ServiceShape serviceShape,
            SymbolProvider symbolProvider,
            List<OperationInfo> operations) implements Runnable {

        @Override
        public void run() {
            List<String> stages = operations.stream()
                    .map(OperationInfo::symbol)
                    .map(symbol -> symbol.getName() + "Stage")
                    .collect(Collectors.toList());
            stages.add("BuildStage");
            writer.pushState();
            writer.putContext("stages", stages);
            for (int i = 0; i < stages.size() - 1; i++) {
                writer.pushState();
                writer.putContext("curStage", stages.get(i));
                writer.putContext("nextStage", stages.get(i + 1));
                Symbol operation = operations.get(i).symbol;
                Symbol syncOperation = operation.expectProperty(ServerSymbolProperties.STUB_OPERATION);
                Symbol asyncOperation = operation.expectProperty(ServerSymbolProperties.ASYNC_STUB_OPERATION);
                writer.putContext("operation", operation);
                writer.write("""
                        public interface ${curStage:L} {
                            ${nextStage:L} add${operation:T}Operation($T operation);
                            ${nextStage:L} add${operation:T}Operation($T operation);
                        }
                        """, syncOperation, asyncOperation);
                writer.popState();
            }
            var template =
                    """
                            public interface BuildStage {
                                ${service:T} build();
                            }

                            public static ${lastStage:L} builder() {
                                return new Builder();
                            }

                            private final static class Builder implements ${#stages}${value:L}${^key.last}, ${/key.last}${/stages} {

                                ${builderProperties:C|}

                                ${builderStages:C|}

                                public ${service:T} build() {
                                    return new ${service:T}(this);
                                }
                            }
                            """;
            writer.putContext("lastStage", stages.get(0));
            writer.putContext(
                    "builderProperties",
                    new PropertyGenerator(writer, serviceShape, symbolProvider, operations, true));
            writer.putContext("builderStages", writer.consumer(w -> this.generateStages(w, stages)));
            writer.write(template);
            writer.popState();
        }

        private void generateStages(JavaWriter writer, List<String> stages) {
            for (int i = 0; i < operations.size(); i++) {
                Symbol operation = operations.get(i).symbol;
                String operationFieldName = operation.expectProperty(
                        ServerSymbolProperties.OPERATION_FIELD_NAME);
                String nextStage = stages.get(i + 1);
                Symbol syncOperation = operation.expectProperty(ServerSymbolProperties.STUB_OPERATION);
                Symbol asyncOperation = operation.expectProperty(ServerSymbolProperties.ASYNC_STUB_OPERATION);
                Symbol apiOperation = operation.expectProperty(ServerSymbolProperties.API_OPERATION);
                writer.pushState();
                var template =
                        """
                                @Override
                                public ${nextStage:L} add${operation:T}Operation(${asyncOperationType:T} operation) {
                                    this.${operationFieldName:L} = s -> ${operationClass:T}.ofAsync("${operation:T}", operation::${operationFieldName:L}, ${apiOperationClass:T}.instance(), s);
                                    return this;
                                }

                                @Override
                                public ${nextStage:L} add${operation:T}Operation(${syncOperationType:T} operation) {
                                    this.${operationFieldName:L} = s -> ${operationClass:T}.of("${operation:T}", operation::${operationFieldName:L}, ${apiOperationClass:T}.instance(), s);
                                    return this;
                                }
                                """;
                writer.putContext("operationFieldName", operationFieldName);
                writer.putContext("nextStage", nextStage);
                writer.putContext("operation", operation);
                writer.putContext("asyncOperationType", asyncOperation);
                writer.putContext("syncOperationType", syncOperation);
                writer.putContext("apiOperationClass", apiOperation);
                writer.putContext("operationClass", Operation.class);
                writer.write(template);
                writer.popState();
            }
        }
    }

    private record GetOperationGenerator(
            JavaWriter writer,
            ServiceShape serviceShape,
            SymbolProvider symbolProvider,
            List<Symbol> operations) implements Runnable {

        @Override
        public void run() {
            writer.openBlock("return switch (operationName) {", "};", () -> {
                for (Symbol operation : operations) {
                    writer.write(
                            "case $S -> (Operation<I, O>) $L;",
                            operation.getName(),
                            operation.expectProperty(ServerSymbolProperties.OPERATION_FIELD_NAME));
                }
                writer.write(
                        "default -> throw $T.builder().message(\"Unknown operation name: \" + operationName).build();",
                        UnknownOperationException.class);
            });

        }
    }

    private record OperationInfo(
            Symbol symbol,
            OperationShape operationShape,
            Symbol inputSymbol,
            Symbol outputSymbol) {}

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
