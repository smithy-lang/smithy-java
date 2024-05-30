/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.GenerateMapDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Generates serializers and deserializers for Map shapes.
 */
@SmithyInternalApi
public class MapGenerator
    implements Consumer<GenerateMapDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateMapDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        directive.context()
            .writerDelegator()
            .useFileWriter(
                CodegenUtils.getSerdeFileName(directive.settings()),
                CodegenUtils.getModelNamespace(directive.settings()),
                writer -> writer.onSection("sharedSerde", t -> {

                    var value = directive.model().expectShape(directive.shape().getValue().getTarget());
                    var valueSymbol = directive.symbolProvider().toSymbol(value);
                    var valueSchema = CodegenUtils.getSchemaType(writer, directive.symbolProvider(), value);
                    var keySymbol = directive.symbolProvider().toSymbol(directive.shape().getKey());
                    var name = CodegenUtils.getDefaultName(directive.shape(), directive.service());

                    writer.pushState();
                    writer.putContext("shape", directive.symbol());
                    writer.putContext("name", name);
                    writer.putContext("value", valueSymbol);
                    writer.putContext("valueSchema", valueSchema);
                    writer.putContext("key", keySymbol);
                    writer.putContext(
                        "collectionImpl",
                        directive.symbol().expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS)
                    );
                    writer.putContext("schema", SdkSchema.class);
                    writer.putContext("biConsumer", BiConsumer.class);
                    writer.putContext("mapSerializer", MapSerializer.class);
                    writer.putContext("shapeSerializer", ShapeSerializer.class);
                    writer.putContext("shapeDeserializer", ShapeDeserializer.class);
                    writer.putContext(
                        "memberSerializer",
                        new SerializerMemberGenerator(
                            writer,
                            directive.symbolProvider(),
                            directive.model(),
                            directive.service(),
                            directive.shape().getValue(),
                            "values"
                        )
                    );
                    writer.putContext(
                        "memberDeserializer",
                        new DeserializerGenerator(
                            writer,
                            value,
                            directive.symbolProvider(),
                            directive.model(),
                            directive.service(),
                            "deserializer",
                            valueSchema
                        )
                    );
                    writer.write(
                        """
                            static final class ${name:U}Serializer implements ${biConsumer:T}<${shape:T}, ${mapSerializer:T}> {
                                static final ${name:U}Serializer INSTANCE = new ${name:U}Serializer();

                                @Override
                                public void accept(${shape:T} values, ${mapSerializer:T} serializer) {
                                    for (var valueEntry : values.entrySet()) {
                                        serializer.writeEntry(
                                            ${valueSchema:L},
                                            valueEntry.getKey(),
                                            valueEntry.getValue(),
                                            ${name:U}ValueSerializer.INSTANCE
                                        );
                                    }
                                }
                            }

                            private static final class ${name:U}ValueSerializer implements ${biConsumer:T}<${value:T}, ${shapeSerializer:T}> {
                                private static final ${name:U}ValueSerializer INSTANCE = new ${name:U}ValueSerializer();

                                @Override
                                public void accept(${value:T} values, ${shapeSerializer:T} serializer) {
                                    ${memberSerializer:C|};
                                }
                            }

                            static ${shape:T} deserialize${name:U}(${schema:T} schema, ${shapeDeserializer:T} deserializer) {
                                ${shape:T} result = new ${collectionImpl:T}<>();
                                deserializer.readStringMap(schema, result, ${name:U}ValueDeserializer.INSTANCE);
                                return result;
                            }

                            private static final class ${name:U}ValueDeserializer implements ${shapeDeserializer:T}.MapMemberConsumer<${key:T}, ${shape:T}> {
                                static final ${name:U}ValueDeserializer INSTANCE = new ${name:U}ValueDeserializer();

                                @Override
                                public void accept(${shape:T} state, ${key:T} key, ${shapeDeserializer:T} deserializer) {
                                    state.put(key, $memberDeserializer:C);
                                }
                            }
                            """
                    );
                    writer.popState();

                    // Writes any existing text
                    writer.writeInlineWithNoFormatting(t);
                })
            );
    }
}
