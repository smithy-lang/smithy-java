/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.generators;

import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.generators.SchemaGenerator;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.SdkOperation;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;


public class OperationGenerator
    implements Consumer<GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var shape = directive.shape();

        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            var input = directive.symbolProvider().toSymbol(directive.model().expectShape(shape.getInputShape()));
            var output = directive.symbolProvider().toSymbol(directive.model().expectShape(shape.getOutputShape()));
            writer.pushState(new ClassSection(shape));
            writer.putContext("shape", directive.symbol());
            writer.putContext("sdkOperation", SdkOperation.class);
            writer.putContext("inputType", input);
            writer.putContext("outputType", output);
            writer.putContext("sdkSchema", SdkSchema.class);
            writer.putContext("sdkShapeBuilder", SdkShapeBuilder.class);
            writer.putContext("typeRegistry", TypeRegistry.class);
            writer.putContext(
                "schema",
                new SchemaGenerator(writer, shape, directive.symbolProvider(), directive.model(), directive.context())
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
            writer.write(
                """
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
                    """
            );
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
