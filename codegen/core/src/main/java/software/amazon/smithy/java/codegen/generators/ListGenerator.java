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
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.UniqueItemsTrait;
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
                    var valueSchema = CodegenUtils.getSchemaType(writer, directive.symbolProvider(), target);
                    writer.pushState();
                    var template = """
                        static final class ${name:U}Serializer implements ${biConsumer:T}<${shape:B}, ${shapeSerializer:T}> {
                            static final ${name:U}Serializer INSTANCE = new ${name:U}Serializer();

                            @Override
                            public void accept(${shape:B} values, ${shapeSerializer:T} serializer) {
                                for (var value : values) {
                                    ${?sparse}if (value == null) {
                                        serializer.writeNull(${valueSchema:L});
                                        continue;
                                    }${/sparse}
                                    ${memberSerializer:C|};
                                }
                            }
                        }

                        static ${shape:T} deserialize${name:U}(${schema:T} schema, ${shapeDeserializer:T} deserializer) {
                            var size = deserializer.containerSize();
                            ${shape:T} result = size == -1 ? new ${collectionImpl:T}<>() : new ${collectionImpl:T}<>(size);
                            deserializer.readList(schema, result, ${name:U}$$MemberDeserializer.INSTANCE);
                            return result;
                        }

                        private static final class ${name:U}$$MemberDeserializer implements ${shapeDeserializer:T}.ListMemberConsumer<${shape:B}> {
                            static final ${name:U}$$MemberDeserializer INSTANCE = new ${name:U}$$MemberDeserializer();

                            @Override
                            public void accept(${shape:B} state, ${shapeDeserializer:T} deserializer) {
                                if (deserializer.isNull()) {
                                    ${?sparse}${?unique}if (!${/unique}state.add(${/sparse}deserializer.readNull()${^sparse};${/sparse}${?sparse})${^unique};${/unique}${?unique}) {
                                        throw new ${serdeException:T}("Member must have unique values");
                                    }${/unique}${/sparse}
                                    return;
                                }
                                ${?unique}if (!${/unique}state.add($memberDeserializer:C)${^unique};${/unique}${?unique}) {
                                    throw new ${serdeException:T}("Member must have unique values");
                                }${/unique}
                            }
                        }
                        """;
                    writer.putContext("shape", directive.symbol());
                    writer.putContext("name", name);
                    writer.putContext(
                        "collectionImpl",
                        directive.symbol().expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS)
                    );
                    writer.putContext("schema", Schema.class);
                    writer.putContext("biConsumer", BiConsumer.class);
                    writer.putContext("shapeSerializer", ShapeSerializer.class);
                    writer.putContext("shapeDeserializer", ShapeDeserializer.class);
                    writer.putContext("unique", directive.shape().hasTrait(UniqueItemsTrait.class));
                    writer.putContext("serdeException", SerializationException.class);
                    writer.putContext("sparse", directive.shape().hasTrait(SparseTrait.class));
                    writer.putContext("valueSchema", valueSchema);
                    writer.putContext(
                        "memberSerializer",
                        new SerializerMemberGenerator(
                            writer,
                            directive.symbolProvider(),
                            directive.model(),
                            directive.service(),
                            directive.shape().getMember(),
                            "value"
                        )
                    );
                    writer.putContext(
                        "memberDeserializer",
                        new DeserializerGenerator(
                            writer,
                            target,
                            directive.symbolProvider(),
                            directive.model(),
                            directive.service(),
                            "deserializer",
                            valueSchema
                        )
                    );
                    writer.write(template);
                    writer.popState();

                    // Writes any existing text
                    writer.writeInlineWithNoFormatting(t);
                })
            );
    }
}
