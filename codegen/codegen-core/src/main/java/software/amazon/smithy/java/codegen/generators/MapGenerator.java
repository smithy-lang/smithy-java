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
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class MapGenerator
        implements Consumer<GenerateMapDirective<CodeGenerationContext, JavaCodegenSettings>> {

    @Override
    public void accept(GenerateMapDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var serdeClass = SharedSerdeGenerator.getClassName(directive.context(), directive.shape());
        directive.context()
                .writerDelegator()
                .useFileWriter(
                        CodegenUtils.getJavaFilePath(directive.settings(), "model", serdeClass),
                        CodegenUtils.getModelNamespace(directive.settings()),
                        writer -> writer.onSection("sharedSerde", t -> {

                            var value = directive.model().expectShape(directive.shape().getValue().getTarget());
                            var valueSymbol = directive.symbolProvider().toSymbol(value);
                            var valueSchema = writer.format(
                                    "$L.mapValueMember()",
                                    directive.context()
                                            .schemaFieldOrder()
                                            .getSchemaFieldName(directive.shape(), writer));
                            var keySymbol = directive.symbolProvider().toSymbol(directive.shape().getKey());
                            var keySchema =
                                    directive.context()
                                            .schemaFieldOrder()
                                            .getSchemaFieldName(directive.shape(), writer);
                            var name = CodegenUtils.getDefaultName(directive.shape(), directive.getRenames());

                            writer.pushState();
                            var template =
                                    """
                                            ${?generatedSerializer}static final class ${name:U}Serializer implements ${biConsumer:T}<${shape:B}, ${mapSerializer:T}> {
                                                static final ${name:U}Serializer INSTANCE = new ${name:U}Serializer();

                                                @Override
                                                public void accept(${shape:T} values, ${mapSerializer:T} serializer) {
                                                    var $$k = ${keySchema:L}.mapKeyMember();
                                                    values.forEach((k, v) -> serializer.writeEntry(
                                                        $$k,
                                                        k${?enumKey}.getValue()${/enumKey},
                                                        v,
                                                        ${name:U}$$ValueSerializer.INSTANCE
                                                    ));
                                                }
                                            }

                                            private static final class ${name:U}$$ValueSerializer implements ${biConsumer:T}<${value:B}, ${shapeSerializer:T}> {
                                                private static final ${name:U}$$ValueSerializer INSTANCE = new ${name:U}$$ValueSerializer();

                                                @Override
                                                public void accept(${value:B} values, ${shapeSerializer:T} serializer) {
                                                    ${?sparse}if (values == null) {
                                                        serializer.writeNull(${valueSchema:L});
                                                        return;
                                                    }
                                                    ${/sparse}${memberSerializer:C|};
                                                }
                                            }

                                            ${/generatedSerializer}static void deserialize${name:U}Value(
                                                    ${shape:B} state,
                                                    ${string:T} key,
                                                    ${shapeDeserializer:T} deserializer
                                            ) {
                                                if (deserializer.isNull()) {
                                                    ${?sparse}state.put(${?enumKey}${key:T}.from(${/enumKey}key${?enumKey})${/enumKey}, deserializer.readNull());
                                                    return;${/sparse}${^sparse}throw new ${serdeException:T}("Null value found in dense map");${/sparse}
                                                }
                                                state.put(${?enumKey}${key:T}.from(${/enumKey}key${?enumKey})${/enumKey}, $memberDeserializer:C);
                                            }

                                            static ${shape:T} deserialize${name:U}(
                                                    ${schema:T} schema,
                                                    ${shapeDeserializer:T} deserializer
                                            ) {
                                                var size = Math.min(
                                                        deserializer.containerSize(),
                                                        deserializer.containerPreAllocationLimit());
                                                ${shape:T} result = size == -1
                                                        ? new ${collectionImpl:T}<>()
                                                        : ${collectionImpl:T}.${newMap:L}(size);
                                                deserializer.readStringMap(
                                                        schema,
                                                        result,
                                                        ${serdeClass:L}::deserialize${name:U}Value);
                                                return result;
                                            }
                                            """;
                            writer.putContext("shape", directive.symbol());
                            writer.putContext("name", name);
                            writer.putContext(
                                    "generatedSerializer",
                                    !CodegenUtils.usesTypedMapWriter(directive.model(), directive.shape()));
                            writer.putContext("key", keySymbol);
                            writer.putContext("value", valueSymbol);
                            writer.putContext("serdeClass", serdeClass);
                            writer.putContext("keySchema", keySchema);
                            writer.putContext("valueSchema", valueSchema);
                            var collectionImpl = (Class<?>) directive.symbol()
                                    .expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS);
                            writer.putContext("collectionImpl", collectionImpl);
                            writer.putContext(
                                    "newMap",
                                    "new" + collectionImpl.getSimpleName());
                            writer.putContext("schema", Schema.class);
                            writer.putContext("biConsumer", BiConsumer.class);
                            writer.putContext("mapSerializer", MapSerializer.class);
                            writer.putContext("shapeSerializer", ShapeSerializer.class);
                            writer.putContext("shapeDeserializer", ShapeDeserializer.class);
                            writer.putContext("string", String.class);
                            writer.putContext(
                                    "memberSerializer",
                                    new SerializerMemberGenerator(
                                            directive,
                                            writer,
                                            directive.shape().getValue(),
                                            "values"));
                            writer.putContext(
                                    "memberDeserializer",
                                    new DeserializerGenerator(
                                            writer,
                                            value,
                                            directive.symbolProvider(),
                                            directive.model(),
                                            directive.getRenames(),
                                            directive.context().schemaFieldOrder(),
                                            "deserializer",
                                            valueSchema));
                            writer.putContext(
                                    "enumKey",
                                    directive.model()
                                            .expectShape(directive.shape().getKey().getTarget())
                                            .isEnumShape());
                            writer.putContext("sparse", directive.shape().hasTrait(SparseTrait.class));
                            writer.putContext("serdeException", SerializationException.class);
                            writer.write(template);
                            writer.popState();

                            // Writes any existing text
                            writer.writeInlineWithNoFormatting(t);
                        }));
    }
}
