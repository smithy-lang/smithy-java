/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.SdkOperation;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.TypeRegistry;
import software.amazon.smithy.model.shapes.OperationShape;

public class OperationGenerator
    implements Consumer<GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var shape = directive.shape();

        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            var input = directive.symbolProvider().toSymbol(directive.model().expectShape(shape.getInputShape()));
            var output = directive.symbolProvider().toSymbol(directive.model().expectShape(shape.getOutputShape()));
            writer.pushState(new ClassSection(shape));
            writer.putContext("inputType", input);
            writer.putContext("outputType", output);
            writer.putContext("sdkSchema", SdkSchema.class);
            writer.putContext("sdkShapeBuilder", SdkShapeBuilder.class);
            writer.putContext("typeRegistry", TypeRegistry.class);
            writer.write(
                """
                    public final class $T implements $T<${inputType:T}, ${outputType:T}> {

                        ${C|}

                        ${C|}

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
                    """,
                directive.symbol(),
                SdkOperation.class,
                new SchemaGenerator(writer, shape, directive.symbolProvider(), directive.model()),
                writer.consumer(w -> writeTypeRegistry(w, shape, directive))
            );
            writer.popState();
        });
    }

    private static void writeTypeRegistry(
        JavaWriter writer,
        OperationShape shape,
        GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive
    ) {
        writer.write("private final ${typeRegistry:T} typeRegistry = ${typeRegistry:T}.builder()");
        writer.indent();
        writer.write(".putType(${inputType:T}.ID, ${inputType:T}.class, ${inputType:T}::builder)");
        writer.write(".putType(${outputType:T}.ID, ${outputType:T}.class, ${outputType:T}::builder)");
        for (var errorId : shape.getErrors(directive.service())) {
            var errorShape = directive.model().expectShape(errorId);
            writer.write(".putType($1T.ID, $1T.class, $1T::builder)", directive.symbolProvider().toSymbol(errorShape));
        }
        writer.writeWithNoFormatting(".build();");
        writer.dedent();
    }
}
