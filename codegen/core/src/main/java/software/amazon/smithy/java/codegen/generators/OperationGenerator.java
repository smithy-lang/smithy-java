/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.Model;
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
                writer.pushState(new ClassSection(shape));
                var template = """
                    public final class ${shape:T} implements ${sdkOperation:T}<${inputType:T}, ${outputType:T}> {

                        static final ${sdkSchema:T} SCHEMA = ${schema:C}

                        ${typeRegistrySection:C|}

                        @Override
                        public ${sdkShapeBuilder:T}<${inputType:T}> inputBuilder() {
                            return ${inputType:T}.builder();
                        }

                        @Override
                        public ${sdkShapeBuilder:T}<${outputType:T}> outputBuilder() {
                            return ${outputType:T}.builder();
                        }

                        @Override
                        public ${sdkSchema:T} schema() {
                            return SCHEMA;
                        }

                        @Override
                        public ${sdkSchema:T} inputSchema() {
                            return ${inputType:T}.SCHEMA;
                        }

                        @Override
                        public ${sdkSchema:T} outputSchema() {
                            return ${outputType:T}.SCHEMA;
                        }

                        @Override
                        public ${typeRegistry:T} typeRegistry() {
                            return typeRegistry;
                        }
                    }
                    """;
                writer.putContext("shape", symbol);
                writer.putContext("sdkOperation", ApiOperation.class);
                writer.putContext("inputType", input);
                writer.putContext("outputType", output);
                writer.putContext("sdkSchema", Schema.class);
                writer.putContext("sdkShapeBuilder", ShapeBuilder.class);
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
}
