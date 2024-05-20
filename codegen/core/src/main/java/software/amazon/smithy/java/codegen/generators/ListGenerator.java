/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.GenerateListDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Generates serializers and deserializers for List shapes.
 */
@SmithyInternalApi
public final class ListGenerator
    implements Consumer<GenerateListDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateListDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        directive.context()
            .writerDelegator()
            .useFileWriter(
                CodegenUtils.getSerdeFileName(directive.settings()),
                CodegenUtils.getModelNamespace(directive.settings()),
                writer -> writer.onSection("sharedSerde", t -> {

                    var name = CodegenUtils.getDefaultName(directive.shape(), directive.service());
                    var target = directive.model().expectShape(directive.shape().getMember().getTarget());
                    writer.pushState();
                    writer.putContext("shape", directive.symbol());
                    writer.putContext("name", name);
                    writer.putContext(
                        "collectionImpl",
                        directive.symbol().expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS)
                    );
                    writer.putContext("schema", SdkSchema.class);
                    writer.putContext("biConsumer", BiConsumer.class);
                    writer.putContext("shapeSerializer", ShapeSerializer.class);
                    writer.putContext("shapeDeserializer", ShapeDeserializer.class);

                    writer.write(
                        """
                            static final class ${name:U}Serializer implements ${biConsumer:T}<${shape:T}, ${shapeSerializer:T}> {
                                static final ${name:U}Serializer INSTANCE = new ${name:U}Serializer();

                                @Override
                                public void accept(${shape:T} values, ${shapeSerializer:T} serializer) {
                                    for (var value : values) {
                                        ${C|};
                                    }
                                }
                            }

                            static ${shape:T} deserialize${name:U}(${schema:T} schema, ${shapeDeserializer:T} deserializer) {
                                ${shape:T} result = new ${collectionImpl:T}<>();
                                deserializer.readList(schema, result, ${name:U}MemberDeserializer.INSTANCE);
                                return result;
                            }

                            private static final class ${name:U}MemberDeserializer implements ${shapeDeserializer:T}.ListMemberConsumer<${shape:T}> {
                                static final ${name:U}MemberDeserializer INSTANCE = new ${name:U}MemberDeserializer();

                                @Override
                                public void accept(${shape:T} state, ${shapeDeserializer:T} deserializer) {
                                    state.add($C);
                                }
                            }
                            """,
                        new SerializerMemberGenerator(
                            writer,
                            directive.symbolProvider(),
                            directive.model(),
                            directive.service(),
                            directive.shape().getMember(),
                            "value"
                        ),
                        new DeserializerGenerator(
                            writer,
                            target,
                            directive.symbolProvider(),
                            directive.model(),
                            directive.service(),
                            "deserializer",
                            CodegenUtils.getSchemaType(writer, directive.symbolProvider(), target)
                        )
                    );
                    writer.popState();
                    writer.writeInlineWithNoFormatting(t);
                })
            );
    }
}
