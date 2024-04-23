/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.SymbolProvider;
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
                new SwitchVisitor(member)
            );
        }
    }

    private final class SwitchVisitor extends ShapeVisitor.Default<Void> implements Runnable {
        private final MemberShape memberShape;

        private SwitchVisitor(MemberShape memberShape) {
            this.memberShape = memberShape;
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
                new DeserReaderVisitor(shape, "de", "member")
            );
            return null;
        }

        @Override
        public Void listShape(ListShape shape) {
            writer.pushState();
            writer.putContext(
                "collectionImpl",
                symbolProvider.toSymbol(shape)
                    .expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS, Class.class)
            );
            if (shape.hasTrait(UniqueItemsTrait.class)) {
                writer.putContext("sdkSerdeException", SdkSerdeException.class);
                writer.write(
                    """
                        {
                            $T result = new ${collectionImpl:T}<>();
                            var elementSchema = member.member("member");
                            de.readList(member, elem -> {
                                if (result.add($C)) {
                                    throw new ${sdkSerdeException:T}("Duplicate item in unique list " + elem);
                                }
                            });
                            ${memberName:L}(result);
                        }""",
                    symbolProvider.toSymbol(shape),
                    new DeserReaderVisitor(shape.getMember(), "de", "elementSchema")
                );
            } else {
                writer.write(
                    """
                        {
                            $T result = new ${collectionImpl:T}<>();
                            var elementSchema = member.member("member");
                            de.readList(member, elem -> result.add($C));
                            ${memberName:L}(result);
                        }""",
                    symbolProvider.toSymbol(shape),
                    new DeserReaderVisitor(shape.getMember(), "de", "elementSchema")
                );
            }
            writer.popState();
            return null;
        }

        @Override
        public Void mapShape(MapShape shape) {
            writer.pushState();
            var keyTarget = model.expectShape(shape.getKey().getTarget());
            var valueTarget = model.expectShape(shape.getValue().getTarget());
            writer.putContext(
                "collectionImpl",
                symbolProvider.toSymbol(shape)
                    .expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS, Class.class)
            );

            // Special case lists and maps.
            if (valueTarget.isListShape()) {
                writer.putContext(
                    "nestedCollectionImpl",
                    symbolProvider.toSymbol(valueTarget)
                        .expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS)
                );
                writer.write(
                    """
                        {
                            $T result = new ${collectionImpl:T}<>();
                            var valueSchema = member.member("value");
                            de.$L(member, (key, v) -> {
                                ${C|}
                            });
                            ${memberName:L}(result);
                        }""",
                    symbolProvider.toSymbol(shape),
                    getMapReadMethod(keyTarget),
                    new DeserReaderVisitor(shape.getValue(), "v", "valueSchema")
                );
            } else if (valueTarget.isMapShape()) {
                // TODO: Implement?
            } else {
                writer.write(
                    """
                        {
                            $T result = new ${collectionImpl:T}<>();
                            var valueSchema = member.member("value");
                            de.$L(member, (key, v) -> result.put(key, $C));
                            ${memberName:L}(result);
                        }""",
                    symbolProvider.toSymbol(shape),
                    getMapReadMethod(keyTarget),
                    new DeserReaderVisitor(valueTarget, "de", "valueSchema")
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

    private static String getMapReadMethod(Shape shape) {
        return switch (shape.getType()) {
            case INTEGER -> "readIntMap";
            case LONG -> "readLongMap";
            case STRING -> "readStringMap";
            default -> throw new CodegenException(
                "Invalid map key type: " + shape.getType() + " for shape "
                    + shape
            );
        };
    }

    private final class DeserReaderVisitor extends ShapeVisitor.DataShapeVisitor<Void> implements Runnable {
        private final Shape memberShape;
        private final String deserVar;
        private final String schemaName;

        private DeserReaderVisitor(Shape memberShape, String deserVar, String schemaName) {
            this.deserVar = deserVar;
            this.memberShape = memberShape;
            this.schemaName = schemaName;
        }

        @Override
        public Void blobShape(BlobShape blobShape) {
            writer.write("$L.readBlob($L)", deserVar, schemaName);
            return null;
        }

        @Override
        public Void booleanShape(BooleanShape booleanShape) {
            writer.write("$L.readBoolean($L)", deserVar, schemaName);
            return null;
        }

        // TODO: Handle multiply nested lists
        @Override
        public Void listShape(ListShape listShape) {
            writer.pushState();
            if (memberShape.isMemberShape()
                && model.expectShape(memberShape.asMemberShape().get().getContainer()).isMapShape()) {
                writer.write(
                    """
                        var nestedSchema = valueSchema.member("member");
                        var resultNested = result.computeIfAbsent(key, k -> new ${nestedCollectionImpl:T}<>());
                        v.readList(valueSchema, nl -> {
                            resultNested.add($C);
                        });
                        """,
                    new DeserReaderVisitor(listShape.getMember(), "nl", "nestedSchema")
                );
            } else if (memberShape.isMemberShape()
                && model.expectShape(memberShape.asMemberShape().get().getContainer()).isListShape()) {
                    writer.write(
                        """
                            var nestedSchema = valueSchema.member("member");
                            var resultNested = ${nestedCollectionImpl:T}<>();
                            v.readList(valueSchema, nl -> {
                                resultNested.add($C);
                            });
                            result.add(resultNested);
                            """,
                        new DeserReaderVisitor(listShape.getMember(), "nl", "nestedSchema")
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
            writer.write("$L.readByte($L)", deserVar, schemaName);
            return null;
        }

        @Override
        public Void shortShape(ShortShape shortShape) {
            writer.write("$L.readShort($L)", deserVar, schemaName);
            return null;
        }

        @Override
        public Void integerShape(IntegerShape integerShape) {
            writer.write("$L.readInteger($L)", deserVar, schemaName);
            return null;
        }

        @Override
        public Void longShape(LongShape longShape) {
            writer.write("$L.readLong($L)", deserVar, schemaName);
            return null;
        }

        @Override
        public Void floatShape(FloatShape floatShape) {
            writer.write("$L.readFloat($L)", deserVar, schemaName);
            return null;
        }

        @Override
        public Void documentShape(DocumentShape documentShape) {
            writer.write("$L.readDocument()", deserVar);
            return null;
        }

        @Override
        public Void doubleShape(DoubleShape doubleShape) {
            writer.write("$L.readDouble($L)", deserVar, schemaName);
            return null;
        }

        @Override
        public Void bigIntegerShape(BigIntegerShape bigIntegerShape) {
            writer.write("$L.readBigInteger($L)", deserVar, schemaName);
            return null;
        }

        @Override
        public Void bigDecimalShape(BigDecimalShape bigDecimalShape) {
            writer.write("$L.readBigDecimal($L)", deserVar, schemaName);
            return null;
        }

        @Override
        public Void stringShape(StringShape stringShape) {
            writer.write("$L.readString($L)", deserVar, schemaName);
            return null;
        }

        @Override
        public Void structureShape(StructureShape structureShape) {
            writer.write("$T.builder().deserialize($L).build()", symbolProvider.toSymbol(memberShape), deserVar);
            return null;
        }

        @Override
        public Void unionShape(UnionShape unionShape) {
            // TODO: Implement
            return null;
        }

        @Override
        public Void memberShape(MemberShape memberShape) {
            return model.expectShape(memberShape.getTarget()).accept(this);
        }

        @Override
        public Void timestampShape(TimestampShape timestampShape) {
            writer.write("$L.readTimestamp($L)", deserVar, schemaName);
            return null;
        }

        @Override
        public void run() {
            memberShape.accept(this);
        }
    }
}
