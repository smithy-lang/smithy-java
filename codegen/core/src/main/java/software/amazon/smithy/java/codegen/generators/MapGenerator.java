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
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.traits.SparseTrait;
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
                    var keySchema = CodegenUtils.getSchemaType(writer, directive.symbolProvider(), directive.shape());
                    var name = CodegenUtils.getDefaultName(directive.shape(), directive.service());

                    writer.pushState();
                    var template = """
                        static final class ${name:U}Serializer implements ${biConsumer:T}<${shape:B}, ${mapSerializer:T}> {
                            static final ${name:U}Serializer INSTANCE = new ${name:U}Serializer();

                            @Override
                            public void accept(${shape:T} values, ${mapSerializer:T} serializer) {
                                for (var valueEntry : values.entrySet()) {
                                    serializer.writeEntry(
                                        ${keySchema:L}.mapKeyMember(),
                                        valueEntry.getKey()${?enumKey}.value()${/enumKey},
                                        valueEntry.getValue(),
                                        ${name:U}$$ValueSerializer.INSTANCE
                                    );
                                }
                            }
                        }

                        private static final class ${name:U}$$ValueSerializer implements ${biConsumer:T}<${value:B}, ${shapeSerializer:T}> {
                            private static final ${name:U}$$ValueSerializer INSTANCE = new ${name:U}$$ValueSerializer();

                            @Override
                            public void accept(${value:B} values, ${shapeSerializer:T} serializer) {
                                ${?sparse}if (values == null) {
                                    serializer.writeNull(${valueSchema:L});
                                    return;
                                }${/sparse}
                                ${memberSerializer:C|};
                            }
                        }

                        static ${shape:T} deserialize${name:U}(${schema:T} schema, ${shapeDeserializer:T} deserializer) {
                            var size = deserializer.containerSize();
                            ${shape:T} result = size == -1 ? new ${collectionImpl:T}<>() : new ${collectionImpl:T}<>(size);
                            deserializer.readStringMap(schema, result, ${name:U}$$ValueDeserializer.INSTANCE);
                            return result;
                        }

                        private static final class ${name:U}$$ValueDeserializer implements ${shapeDeserializer:T}.MapMemberConsumer<${string:T}, ${shape:B}> {
                            static final ${name:U}$$ValueDeserializer INSTANCE = new ${name:U}$$ValueDeserializer();

                            @Override
                            public void accept(${shape:B} state, ${string:T} key, ${shapeDeserializer:T} deserializer) {
                                if (deserializer.isNull()) {
                                    ${?sparse}state.put(${?enumKey}${key:T}.from(${/enumKey}key${?enumKey})${/enumKey}, ${/sparse}deserializer.readNull()${?sparse})${/sparse};
                                    return;
                                }
                                state.put(${?enumKey}${key:T}.from(${/enumKey}key${?enumKey})${/enumKey}, $memberDeserializer:C);
                            }
                        }
                        """;
                    writer.putContext("shape", directive.symbol());
                    writer.putContext("name", name);
                    writer.putContext("key", keySymbol);
                    writer.putContext("value", valueSymbol);
                    writer.putContext("keySchema", keySchema);
                    writer.putContext("valueSchema", valueSchema);
                    writer.putContext(
                        "collectionImpl",
                        directive.symbol().expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS)
                    );
                    writer.putContext("schema", Schema.class);
                    writer.putContext("biConsumer", BiConsumer.class);
                    writer.putContext("mapSerializer", MapSerializer.class);
                    writer.putContext("shapeSerializer", ShapeSerializer.class);
                    writer.putContext("shapeDeserializer", ShapeDeserializer.class);
                    writer.putContext("string", String.class);
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
                    writer.putContext(
                        "enumKey",
                        directive.model()
                            .expectShape(directive.shape().getKey().getTarget())
                            .isEnumShape()
                    );
                    writer.putContext("sparse", directive.shape().hasTrait(SparseTrait.class));
                    writer.write(template);
                    writer.popState();

                    // Writes any existing text
                    writer.writeInlineWithNoFormatting(t);
                })
            );
    }
}
