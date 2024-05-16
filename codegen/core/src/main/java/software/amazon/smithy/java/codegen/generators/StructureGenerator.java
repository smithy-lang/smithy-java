/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class StructureGenerator
    implements Consumer<GenerateStructureDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateStructureDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var shape = directive.shape();

        directive.context().writerDelegator().useShapeWriter(shape, writer -> {
            writer.pushState(new ClassSection(shape));
            writer.putContext("shape", directive.symbol());
            writer.putContext("serializable", SerializableShape.class);
            writer.putContext("biConsumer", BiConsumer.class);
            writer.putContext("serializer", ShapeSerializer.class);
            writer.write(
                """
                    public final class ${shape:T} implements ${serializable:T} {
                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        ${C|}

                        @Override
                        public void serialize(${serializer:T} serializer) {
                            serializer.writeStruct(SCHEMA, this, InnerSerializer.INSTANCE);
                        }

                        static final class InnerSerializer implements ${biConsumer:T}<${shape:T}, ${serializer:T}> {
                            static final InnerSerializer INSTANCE = new InnerSerializer();

                            @Override
                            public void accept(${shape:T} shape, ${serializer:T} serializer) {
                                ${C|}
                            }
                        }

                        ${C|}
                    }
                    """,
                writer.consumer(w -> w.writeIdString(shape)),
                new SchemaGenerator(
                    writer,
                    directive.shape(),
                    directive.symbolProvider(),
                    directive.model(),
                    directive.context()
                ),
                new PropertyGenerator(writer, shape, directive.symbolProvider()),
                new ConstructorGenerator(writer, shape, directive.symbolProvider(), directive.model()),
                new GetterGenerator(writer, shape, directive.symbolProvider(), directive.model()),
                writer.consumer(JavaWriter::writeToString),
                new EqualsGenerator(writer, directive.shape(), directive.symbolProvider()),
                new HashCodeGenerator(writer, directive.shape(), directive.symbolProvider()),
                new StructureSerializerGenerator(
                    writer,
                    directive.symbolProvider(),
                    directive.model(),
                    directive.shape(),
                    directive.service()
                ),
                new BuilderGenerator(writer, shape, directive.symbolProvider(), directive.model(), directive.service())
            );
            writer.popState();
        });
    }
}
