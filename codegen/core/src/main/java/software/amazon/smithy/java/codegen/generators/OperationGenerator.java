/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.function.Consumer;
import java.util.function.Supplier;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.InputEventStreamingApiOperation;
import software.amazon.smithy.java.runtime.core.schema.OutputEventStreamingApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.EventStreamIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;


public class OperationGenerator
    implements Consumer<GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        generate(directive, directive.symbol());
    }

    protected final void generate(
        GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive,
        Symbol symbol
    ) {
        var shape = directive.shape();

        directive.context()
            .writerDelegator()
            .useFileWriter(symbol.getDeclarationFile(), symbol.getNamespace(), writer -> {
                var input = directive.symbolProvider().toSymbol(directive.model().expectShape(shape.getInputShape()));
                var output = directive.symbolProvider().toSymbol(directive.model().expectShape(shape.getOutputShape()));
                var eventStreamIndex = EventStreamIndex.of(directive.model());
                writer.pushState(new ClassSection(shape));
                var template = """
                    public final class ${shape:T} implements ${operationType:C} {
                        ${id:C|}

                        ${schema:C|}

                        ${typeRegistrySection:C|}

                        @Override
                        public ${sdkShapeBuilder:N}<${inputType:T}> inputBuilder() {
                            return ${inputType:T}.builder();
                        }

                        ${?hasInputEventStream}
                        @Override
                        public ${supplier:T}<${sdkShapeBuilder:T}<${inputType:T}>> inputEventBuilderSupplier() {
                            return () -> ${inputEventType:T}.builder();
                        }
                        ${/hasInputEventStream}

                        @Override
                        public ${sdkShapeBuilder:N}<${outputType:T}> outputBuilder() {
                            return ${outputType:T}.builder();
                        }

                        @Override
                        public ${sdkSchema:N} schema() {
                            return SCHEMA;
                        }

                        @Override
                        public ${sdkSchema:N} inputSchema() {
                            return ${inputType:T}.SCHEMA;
                        }

                        ${?hasInputEventStream}
                        @Override
                        public ${sdkSchema:T} inputEventSchema() {
                            return ${inputEventType:T}.SCHEMA;
                        }
                        ${/hasInputEventStream}

                        ${?hasOutputEventStream}
                        @Override
                        public ${sdkSchema:T} outputEventSchema() {
                            return ${outputEventType:T}.SCHEMA;
                        }
                        ${/hasOutputEventStream}

                        @Override
                        public ${sdkSchema:N} outputSchema() {
                            return ${outputType:T}.SCHEMA;
                        }

                        @Override
                        public ${typeRegistry:N} typeRegistry() {
                            return typeRegistry;
                        }
                    }
                    """;
                writer.putContext("shape", symbol);
                writer.putContext("inputType", input);
                writer.putContext("outputType", output);
                writer.putContext("id", new IdStringGenerator(writer, shape));
                writer.putContext("sdkSchema", Schema.class);
                writer.putContext("sdkShapeBuilder", ShapeBuilder.class);

                writer.putContext(
                    "operationType",
                    new OperationTypeGenerator(
                        writer,
                        shape,
                        directive.symbolProvider(),
                        directive.model(),
                        eventStreamIndex,
                        directive.context()
                    )
                );

                writer.putContext("typeRegistry", TypeRegistry.class);
                writer.putContext(
                    "schema",
                    new SchemaGenerator(
                        writer,
                        shape,
                        directive.symbolProvider(),
                        directive.model(),
                        directive.context()
                    )
                );
                writer.putContext(
                    "typeRegistrySection",
                    new TypeRegistryGenerator(
                        writer,
                        shape,
                        directive.symbolProvider(),
                        directive.model(),
                        directive.service()
                    )
                );
                eventStreamIndex.getInputInfo(shape).ifPresent(info -> {
                    writer.putContext("supplier", Supplier.class);
                    writer.putContext("hasInputEventStream", true);
                    writer.putContext(
                        "inputEventType",
                        directive.symbolProvider().toSymbol(info.getEventStreamTarget())
                    );
                });
                eventStreamIndex.getOutputInfo(shape).ifPresent(info -> {
                    writer.putContext("hasOutputEventStream", true);
                    writer.putContext(
                        "outputEventType",
                        directive.symbolProvider().toSymbol(info.getEventStreamTarget())
                    );
                });
                writer.write(template);
                writer.popState();
            });
    }


    private record TypeRegistryGenerator(
        JavaWriter writer,
        OperationShape shape,
        SymbolProvider symbolProvider,
        Model model,
        ServiceShape service
    ) implements Runnable {

        @Override
        public void run() {
            writer.write("private final ${typeRegistry:T} typeRegistry = ${typeRegistry:T}.builder()");
            writer.indent();
            writer.write(".putType(${inputType:T}.ID, ${inputType:T}.class, ${inputType:T}::builder)");
            writer.write(".putType(${outputType:T}.ID, ${outputType:T}.class, ${outputType:T}::builder)");
            for (var errorId : shape.getErrors(service)) {
                var errorShape = model.expectShape(errorId);
                writer.write(".putType($1T.ID, $1T.class, $1T::builder)", symbolProvider.toSymbol(errorShape));
            }
            writer.writeWithNoFormatting(".build();");
            writer.dedent();
        }
    }

    private record OperationTypeGenerator(
        JavaWriter writer, OperationShape shape, SymbolProvider symbolProvider,
        Model model, EventStreamIndex index, CodeGenerationContext context
    ) implements Runnable {
        @Override
        public void run() {
            var inputShape = model.expectShape(shape.getInputShape());
            var input = symbolProvider.toSymbol(inputShape);
            var outputShape = model.expectShape(shape.getOutputShape());
            var output = symbolProvider.toSymbol(outputShape);

            var inputInfo = index.getInputInfo(shape);
            var outputInfo = index.getOutputInfo(shape);
            inputInfo.ifPresent(
                info -> writer.writeInline(
                    "$1T<$2T, $3T, $4T>",
                    InputEventStreamingApiOperation.class,
                    input,
                    output,
                    symbolProvider.toSymbol(info.getEventStreamTarget())
                )
            );
            outputInfo.ifPresent(info -> {
                if (inputInfo.isPresent()) {
                    writer.writeInline(", ");
                }
                writer.writeInline(
                    "$1T<$2T, $3T, $4T>",
                    OutputEventStreamingApiOperation.class,
                    input,
                    output,
                    symbolProvider.toSymbol(info.getEventStreamTarget())
                );
            });

            if (inputInfo.isEmpty() && outputInfo.isEmpty()) {
                writer.writeInline("$1T<$2T, $3T>", ApiOperation.class, input, output);
            }
        }
    }
}
