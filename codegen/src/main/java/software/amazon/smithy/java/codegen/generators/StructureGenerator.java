/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.ShapeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.ModeledSdkException;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class StructureGenerator<T extends ShapeDirective<StructureShape, CodeGenerationContext, JavaCodegenSettings>>
    implements Consumer<T> {

    @Override
    public void accept(T directive) {
        var shape = directive.shape();

        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));

            // Error Structures extend ModeledSdkException
            if (shape.hasTrait(ErrorTrait.class)) {
                writer.putContext("isError", true);
                writer.putContext("baseClass", ModeledSdkException.class);
            } else {
                writer.putContext("isError", false);
                writer.putContext("baseClass", SerializableShape.class);
            }

            writer.write(
                """
                    public final class $T ${?isError}extends${/isError}${^isError}implements${/isError} ${baseClass:T} {
                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}
                    }
                    """,
                directive.symbol(),
                writer.consumer(w -> writeIdString(w, shape)),
                new SchemaGenerator(writer, directive.shape(), directive.symbolProvider(), directive.model()),
                new PropertyGenerator(writer, shape, directive.symbolProvider()),
                new ConstructorGenerator(writer, shape, directive.symbolProvider()),
                new GetterGenerator(writer, shape, directive.symbolProvider(), directive.model()),
                writer.consumer(StructureGenerator::writeToString),
                new SerializerGenerator(writer),
                new BuilderGenerator(writer, shape, directive.symbolProvider(), directive.model())
            );
            writer.popState();
        });

    }

    private static void writeIdString(JavaWriter writer, Shape shape) {
        writer.write(
            "public static final $1T ID = $1T.from($2S);",
            ShapeId.class,
            shape.getId()
        );
    }

    private static void writeToString(JavaWriter writer) {
        writer.write("""
            @Override
            public $T toString() {
                return $T.serialize(this);
            }
            """, String.class, ToStringSerializer.class);
    }
}
