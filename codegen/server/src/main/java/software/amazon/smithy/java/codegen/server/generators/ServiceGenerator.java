/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.server.generators;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.generators.IdStringGenerator;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.server.ServerSymbolProperties;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.server.core.Operation;
import software.amazon.smithy.java.server.core.Service;
import software.amazon.smithy.java.server.core.exceptions.UnknownOperationException;
import software.amazon.smithy.model.knowledge.TopDownIndex;
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
        List<Symbol> operations = index.getContainedOperations(shape)
            .stream()
            .map(directive.symbolProvider()::toSymbol)
            .toList();
        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));
            var template = """
                public final class ${service:T} implements ${serviceType:T} {
                    ${id:C|}

                    ${properties:C|}

                    ${constructor:C|}

                    ${builder:C|}

                    @Override
                    public <I, O> ${operationHolder:T}<I, O> getOperation(String operationName) {
                        ${getOperation:C|}
                    }
                }
                """;
            writer.putContext("operationHolder", Operation.class);
            writer.putContext("serviceType", Service.class);
            writer.putContext("service", directive.symbol());
            writer.putContext("id", new IdStringGenerator(writer, shape));
            writer.putContext(
                "properties",
                new PropertyGenerator(writer, shape, directive.symbolProvider(), operations, false)
            );
            writer.putContext(
                "constructor",
                new ConstructorGenerator(writer, shape, directive.symbolProvider(), operations)
            );
            writer.putContext("builder", new BuilderGenerator(writer, shape, directive.symbolProvider(), operations));
            writer.putContext(
                "getOperation",
                new GetOperationGenerator(writer, shape, directive.symbolProvider(), operations)
            );
            writer.write(template);
            writer.popState();
        });


    }

    private record PropertyGenerator(
        JavaWriter writer, ServiceShape serviceShape, SymbolProvider symbolProvider,
        List<Symbol> operations, boolean notFinal
    ) implements Runnable {

        @Override
        public void run() {
            for (Symbol operation : operations) {
                var operationName = operation.getProperty(ServerSymbolProperties.OPERATION_FIELD_NAME);
                writer.pushState();
                writer.putContext("notFinal", notFinal);
                writer.write("private${^notFinal} final${/notFinal} ${operationHolder:T} $L;", operationName);
                writer.popState();
            }
        }
    }

    private record ConstructorGenerator(
        JavaWriter writer, ServiceShape serviceShape, SymbolProvider symbolProvider,
        List<Symbol> operations
    ) implements Runnable {
        @Override
        public void run() {
            writer.write(
                """
                    private ${service:T}(Builder builder) {
                        ${C|}
                    }
                    """,
                writer.consumer(w -> {
                    for (Symbol operation : operations) {
                        var operationName = operation.getProperty(ServerSymbolProperties.OPERATION_FIELD_NAME);
                        w.write("this.$1L = builder.$1L;", operationName);
                    }
                })
            );
        }
    }

    private record BuilderGenerator(
        JavaWriter writer, ServiceShape serviceShape, SymbolProvider symbolProvider,
        List<Symbol> operations
    ) implements Runnable {

        @Override
        public void run() {
            List<String> stages = operations.stream()
                .map(symbol -> symbol.getName() + "Stage")
                .collect(Collectors.toList());
            stages.add("BuildStage");
            writer.pushState();
            writer.putContext("stages", stages);
            for (int i = 0; i < stages.size() - 1; i++) {
                writer.pushState();
                writer.putContext("curStage", stages.get(i));
                writer.putContext("nextStage", stages.get(i + 1));
                Symbol operation = operations.get(i);
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
            var template = """
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
                new PropertyGenerator(writer, serviceShape, symbolProvider, operations, true)
            );
            writer.putContext("builderStages", writer.consumer(w -> this.generateStages(w, stages)));
            writer.write(template);
            writer.popState();
        }

        private void generateStages(JavaWriter writer, List<String> stages) {
            for (int i = 0; i < operations.size(); i++) {
                Symbol operation = operations.get(i);
                String operationFieldName = operation.expectProperty(
                    ServerSymbolProperties.OPERATION_FIELD_NAME
                );
                String nextStage = stages.get(i + 1);
                Symbol syncOperation = operation.expectProperty(ServerSymbolProperties.STUB_OPERATION);
                Symbol asyncOperation = operation.expectProperty(ServerSymbolProperties.ASYNC_STUB_OPERATION);
                writer.pushState();
                var template = """
                    @Override
                    public ${nextStage:L} add${operation:T}Operation(${asyncOperationType:T} operation) {
                        this.${operationFieldName:L} = new ${operationClass:T}<>("${operation:T}", true, operation::${operationFieldName:L});
                        return this;
                    }

                    @Override
                    public ${nextStage:L} add${operation:T}Operation(${syncOperationType:T} operation) {
                        this.${operationFieldName:L} = new ${operationClass:T}<>("${operation:T}", false, operation::${operationFieldName:L});
                        return this;
                    }
                    """;
                writer.putContext("operationFieldName", operationFieldName);
                writer.putContext("nextStage", nextStage);
                writer.putContext("operation", operation);
                writer.putContext("asyncOperationType", asyncOperation);
                writer.putContext("syncOperationType", syncOperation);
                writer.putContext("operationClass", Operation.class);
                writer.write(template);
                writer.popState();
            }
        }
    }

    private record GetOperationGenerator(
        JavaWriter writer, ServiceShape serviceShape, SymbolProvider symbolProvider,
        List<Symbol> operations
    ) implements Runnable {

        @Override
        public void run() {
            writer.openBlock("return switch (operationName) {", "};", () -> {
                for (Symbol operation : operations) {
                    writer.write(
                        "case $S -> $L;",
                        operation.getName(),
                        operation.expectProperty(ServerSymbolProperties.OPERATION_FIELD_NAME)
                    );
                }
                writer.write(
                    "default -> throw new $T(\"Unknown operation name: \" + operationName);",
                    UnknownOperationException.class
                );
            });

        }
    }
}
