/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.SchemaUtils;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.SymbolUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ByteShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.UniqueItemsTrait;

record DeserializerGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model) implements
    Runnable {

    @Override
    public void run() {
        writer.pushState();
        writer.putContext("hasMembers", !shape.members().isEmpty());
        writer.write(
            """
                @Override
                public Builder deserialize($T decoder) {
                    decoder.readStruct(SCHEMA, (member, de) -> {
                        ${?hasMembers}switch (member.memberIndex()) {
                            ${C|}
                        }${/hasMembers}
                    });
                    return this;
                }""",
            ShapeDeserializer.class,
            (Runnable) this::generateMemberSwitchCases
        );
        writer.popState();
    }

    private void generateMemberSwitchCases() {
        int idx = 0;
        for (var iter = shape.members().iterator(); iter.hasNext(); idx++) {
            var member = iter.next();
            if (SymbolUtils.isStreamingBlob(model.expectShape(member.getTarget()))) {
                // Streaming blobs are not deserialized by the builder class.
                continue;
            }
            writer.write(
                "case $L -> ${C|}",
                idx,
                new SwitchVisitor(member, SchemaUtils.toMemberSchemaName(symbolProvider.toMemberName(member)))
            );
        }
    }

    private final class SwitchVisitor extends ShapeVisitor.Default<Void> implements Runnable {
        private final MemberShape memberShape;
        private final String schemaName;

        private SwitchVisitor(MemberShape memberShape, String schemaName) {
            this.memberShape = memberShape;
            this.schemaName = schemaName;
        }

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("memberName", symbolProvider.toMemberName(memberShape));
            memberShape.accept(this);
            writer.popState();
        }

        @Override
        protected Void getDefault(Shape shape) {
            writer.write(
                "${memberName:L}($C);",
                new ReaderVisitor(memberShape, "de", "member", null)
            );
            return null;
        }

        @Override
        public Void listShape(ListShape shape) {
            if (shape.hasTrait(UniqueItemsTrait.class)) {
                writer.pushState();
                writer.putContext(
                    "collectionImpl",
                    symbolProvider.toSymbol(shape)
                        .expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS, Class.class)
                );
                writer.putContext("sdkSerdeException", SdkSerdeException.class);
                writer.write(
                    """
                        {
                            $T result = new ${collectionImpl:T}<>();
                            de.readList(member, elem -> {
                                if (result.add($C)) {
                                    throw new ${sdkSerdeException:T}("Duplicate item in unique list " + elem);
                                }
                            });
                            ${memberName:L}(result);
                        }""",
                    symbolProvider.toSymbol(shape),
                    new ReaderVisitor(shape.getMember(), "elem", schemaName + "_MEMBER", "result")
                );
                writer.popState();
            } else {
                writer.write(
                    """
                        {
                            de.readList(member, elem ->  ${memberName:L}($C));
                        }""",
                    new ReaderVisitor(shape.getMember(), "elem", schemaName + "_MEMBER", "result")
                );
            }
            return null;
        }

        @Override
        public Void mapShape(MapShape shape) {
            writer.pushState();
            var valueTarget = model.expectShape(shape.getValue().getTarget());
            writer.putContext(
                "collectionImpl",
                symbolProvider.toSymbol(shape)
                    .expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS, Class.class)
            );

            // Special case lists and maps.
            if (valueTarget.isListShape() || valueTarget.isMapShape()) {
                writer.write(
                    """
                        {
                            $T result = new ${collectionImpl:T}<>();
                            de.readStringMap(member, (key, val) -> {
                                ${C|}
                            });
                            ${memberName:L}(result);
                        }""",
                    symbolProvider.toSymbol(shape),
                    new ReaderVisitor(shape.getValue(), "val", schemaName + "_VALUE", "result")
                );
            } else {
                writer.write(
                    """
                        {
                            de.readStringMap(member, (key, val) -> put${memberName:U}(key, $C));
                        }""",
                    new ReaderVisitor(shape.getValue(), "val", schemaName + "_VALUE", "result")
                );
            }
            writer.popState();
            return null;
        }

        @Override
        public Void memberShape(MemberShape shape) {
            return model.expectShape(shape.getTarget()).accept(this);
        }
    }

    private final class ReaderVisitor extends ShapeVisitor.DataShapeVisitor<Void> implements Runnable {
        private final MemberShape memberShape;
        private final String deserializer;
        private final String schemaName;
        private final String result;

        private ReaderVisitor(MemberShape memberShape, String deserializer, String schemaName, String result) {
            this.deserializer = deserializer;
            this.memberShape = memberShape;
            this.schemaName = schemaName;
            this.result = result;
        }

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("deserializer", deserializer);
            writer.putContext("schemaName", schemaName);
            writer.putContext("result", result);
            memberShape.accept(this);
            writer.popState();
        }

        @Override
        public Void blobShape(BlobShape blobShape) {
            writer.write("${deserializer:L}.readBlob(${schemaName:L})");
            return null;
        }

        @Override
        public Void booleanShape(BooleanShape booleanShape) {
            writer.write("${deserializer:L}.readBoolean(${schemaName:L})");
            return null;
        }

        // TODO: Handle multiply nested lists
        @Override
        public Void listShape(ListShape listShape) {
            writer.pushState();
            writer.putContext(
                "collectionImpl",
                symbolProvider.toSymbol(listShape)
                    .expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS, Class.class)
            );
            var container = model.expectShape(memberShape.getContainer());
            if (container.isMapShape()) {
                writer.write(
                    """
                        var ${result:L}Nested = ${result:L}.computeIfAbsent(key, k -> new ${collectionImpl:T}<>());
                        ${deserializer:L}.readList(${schemaName:L}, ${deserializer:L}l -> {
                            ${result:L}Nested.add($C);
                        });""",
                    new ReaderVisitor(
                        listShape.getMember(),
                        deserializer + "l",
                        schemaName + "_MEMBER",
                        result + "Nested"
                    )
                );
            } else if (container.isListShape()) {
                writer.write(
                    """
                        var ${result:L}Nested = ${collectionImpl:T}<>();
                        ${deserializer:L}.readList(${schemaName:L}, ${deserializer:L}l -> {
                            ${result:L}Nested.add($C);
                        });
                        ${result:L}.add(${result:L}Nested);""",
                    new ReaderVisitor(
                        listShape.getMember(),
                        deserializer + "l",
                        schemaName + "_MEMBER",
                        result + "Nested"
                    )
                );
            }
            writer.popState();
            return null;
        }

        @Override
        public Void mapShape(MapShape mapShape) {
            // TODO: handle nested maps
            return null;
        }

        @Override
        public Void byteShape(ByteShape byteShape) {
            writer.write("${deserializer:L}.readByte(${schemaName:L})");
            return null;
        }

        @Override
        public Void shortShape(ShortShape shortShape) {
            writer.write("${deserializer:L}.readShort(${schemaName:L})");
            return null;
        }

        @Override
        public Void integerShape(IntegerShape integerShape) {
            writer.write("${deserializer:L}.readInteger(${schemaName:L})");
            return null;
        }

        @Override
        public Void longShape(LongShape longShape) {
            writer.write("${deserializer:L}.readLong(${schemaName:L})");
            return null;
        }

        @Override
        public Void floatShape(FloatShape floatShape) {
            writer.write("${deserializer:L}.readFloat(${schemaName:L})");
            return null;
        }

        @Override
        public Void documentShape(DocumentShape documentShape) {
            writer.write("${deserializer:L}.readDocument()");
            return null;
        }

        @Override
        public Void doubleShape(DoubleShape doubleShape) {
            writer.write("${deserializer:L}.readDouble(${schemaName:L})");
            return null;
        }

        @Override
        public Void bigIntegerShape(BigIntegerShape bigIntegerShape) {
            writer.write("${deserializer:L}.readBigInteger(${schemaName:L})");
            return null;
        }

        @Override
        public Void bigDecimalShape(BigDecimalShape bigDecimalShape) {
            writer.write("${deserializer:L}.readBigDecimal(${schemaName:L})");
            return null;
        }

        @Override
        public Void stringShape(StringShape stringShape) {
            writer.write("${deserializer:L}.readString(${schemaName:L})");
            return null;
        }

        @Override
        public Void structureShape(StructureShape structureShape) {
            writer.write("$T.builder().deserialize(${deserializer:L}).build()", symbolProvider.toSymbol(memberShape));
            return null;
        }

        @Override
        public Void unionShape(UnionShape unionShape) {
            writer.write("$T.builder().deserialize(${deserializer:L}).build()", symbolProvider.toSymbol(memberShape));
            return null;
        }

        @Override
        public Void timestampShape(TimestampShape timestampShape) {
            writer.write("${deserializer:L}.readTimestamp(${schemaName:L})");
            return null;
        }

        @Override
        public Void memberShape(MemberShape memberShape) {
            return model.expectShape(memberShape.getTarget()).accept(this);
        }
    }
}
