/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.SchemaUtils;
import software.amazon.smithy.java.codegen.SymbolUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
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
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.StreamingTrait;

/**
 * Generates the implementation of the
 * {@link software.amazon.smithy.java.runtime.core.schema.SerializableShape#serialize(ShapeSerializer)}
 * method for a class.
 */
final class SerializerGenerator implements Runnable {

    private final JavaWriter writer;
    private final Shape shape;
    private final SymbolProvider symbolProvider;
    private final Model model;

    public SerializerGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model) {
        this.writer = writer;
        this.shape = shape;
        this.symbolProvider = symbolProvider;
        this.model = model;
    }

    @Override
    public void run() {
        writer.pushState();
        writer.write(
            """
                @Override
                public void serialize($T serializer) {
                    serializer.writeStruct(SCHEMA, st -> {
                        ${C|}
                    });
                }
                """,
            ShapeSerializer.class,
            (Runnable) this::generateMemberSerializers
        );
        writer.popState();
    }

    private void generateMemberSerializers() {
        for (var member : shape.members()) {
            var target = model.expectShape(member.getTarget());
            // Skip streaming blobs for serialization
            if (target.hasTrait(StreamingTrait.class)) {
                continue;
            }

            var memberName = symbolProvider.toMemberName(member);
            if (SymbolUtils.isNullableMember(member) && !SymbolUtils.isAggregateType(target)) {
                writer.write(
                    "$T.writeIfNotNull(st, $L, $L);",
                    ShapeSerializer.class,
                    SchemaUtils.toMemberSchemaName(memberName),
                    memberName
                );
            } else {
                // If we needed to wrap the member in a null check then we don't need an extra semicolon.
                writer.putContext(
                    "noSemicolon",
                    (member.isMapShape() || member.isListShape()) && SymbolUtils.isNullableMember(member)
                );
                writer.write(
                    "$C${^noSemicolon};${/noSemicolon}",
                    new MemberSerializerShapeVisitor(
                        member,
                        memberName,
                        SchemaUtils.toMemberSchemaName(memberName),
                        "st"
                    )
                );
            }
        }
    }

    private final class MemberSerializerShapeVisitor extends ShapeVisitor.DataShapeVisitor<Void> implements Runnable {
        private final MemberShape member;
        private final String memberName;
        private final String schemaName;
        private final String serializerName;

        private MemberSerializerShapeVisitor(
            MemberShape member,
            String memberName,
            String schemaName,
            String serializerName
        ) {
            this.member = member;
            this.memberName = memberName;
            this.serializerName = serializerName;
            this.schemaName = schemaName;
        }

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("schemaName", schemaName);
            writer.putContext("memberName", memberName);
            writer.putContext("serializer", serializerName);
            member.accept(this);
            writer.popState();
        }

        @Override
        public Void blobShape(BlobShape blobShape) {
            // Streaming Blobs do not generate a member serializer
            if (SymbolUtils.isStreamingBlob(blobShape)) {
                return null;
            }
            writer.write("${serializer:L}.writeBlob(${schemaName:L}, ${memberName:L})");
            return null;
        }

        @Override
        public Void booleanShape(BooleanShape booleanShape) {
            writer.write("${serializer:L}.writeBoolean(${schemaName:L}, ${memberName:L})");
            return null;
        }

        @Override
        public Void listShape(ListShape listShape) {
            wrapWithNullCheck(
                listShape,
                s -> writer.write(
                    """
                        var ${schemaName:L}_MEMBER = ${schemaName:L}.member("member");
                        ${serializer:L}.writeList(${schemaName:L}, ${serializer:L}l -> ${memberName:L}.forEach(${memberName:L}Elem -> {
                            ${C|};
                        }))""",
                    new MemberSerializerShapeVisitor(
                        listShape.getMember(),
                        memberName + "Elem",
                        schemaName + "_MEMBER",
                        serializerName + "l"
                    )
                )
            );
            return null;
        }

        @Override
        public Void mapShape(MapShape mapShape) {
            wrapWithNullCheck(
                mapShape,
                m -> writer.write(
                    """
                        ${serializer:L}.writeMap(${schemaName:L}, ${serializer:L}m -> {
                            var ${schemaName:L}_KEY = ${schemaName:L}.member("key");
                            var ${schemaName:L}_VALUE = ${schemaName:L}.member("value");
                            ${memberName:L}.forEach((k, ${memberName:L}Val) -> ${serializer:L}m.writeEntry(${schemaName:L}_KEY, k, ${serializer:L}mv -> {
                                ${C|};
                            }));
                        })""",
                    new MemberSerializerShapeVisitor(
                        mapShape.getValue(),
                        memberName + "Val",
                        schemaName + "_VALUE",
                        serializerName + "mv"
                    )
                )
            );
            return null;
        }

        private void wrapWithNullCheck(Shape shape, Consumer<Shape> consumer) {
            // If the collection member is nullable check for existence first.
            // Also only provide this check if the member is nested within a structure.
            // TODO: Update to handle non-structures as well?
            if (SymbolUtils.isNullableMember(member)
                && model.expectShape(member.getContainer()).isStructureShape()) {
                writer.write("""
                    if (${memberName:L} != null) {
                        ${C|};
                    }""", (Runnable) () -> consumer.accept(shape));
            } else {
                consumer.accept(shape);
            }
        }

        @Override
        public Void byteShape(ByteShape byteShape) {
            writer.write("${serializer:L}.writeByte(${schemaName:L}, ${memberName:L})");
            return null;
        }

        @Override
        public Void shortShape(ShortShape shortShape) {
            writer.write("${serializer:L}.writeShort(${schemaName:L}, ${memberName:L})");
            return null;
        }

        @Override
        public Void integerShape(IntegerShape integerShape) {
            writer.write("${serializer:L}.writeInteger(${schemaName:L}, ${memberName:L})");
            return null;
        }

        @Override
        public Void longShape(LongShape longShape) {
            writer.write("${serializer:L}.writeLong(${schemaName:L}, ${memberName:L})");
            return null;
        }

        @Override
        public Void floatShape(FloatShape floatShape) {
            writer.write("${serializer:L}.writeFloat(${schemaName:L}, ${memberName:L})");
            return null;
        }

        @Override
        public Void documentShape(DocumentShape documentShape) {
            writer.write("${serializer:L}.writeDocument(${schemaName:L}, ${memberName:L})");
            return null;
        }

        @Override
        public Void doubleShape(DoubleShape doubleShape) {
            writer.write("${serializer:L}.writeDouble(${schemaName:L}, ${memberName:L})");
            return null;
        }

        @Override
        public Void bigIntegerShape(BigIntegerShape bigIntegerShape) {
            writer.write("${serializer:L}.writeBigInteger(${schemaName:L}, ${memberName:L})");
            return null;
        }

        @Override
        public Void bigDecimalShape(BigDecimalShape bigDecimalShape) {
            writer.write("${serializer:L}.writeBigDecimal(${schemaName:L}, ${memberName:L})");
            return null;
        }

        @Override
        public Void stringShape(StringShape stringShape) {
            writer.write("${serializer:L}.writeString(${schemaName:L}, ${memberName:L})");
            return null;
        }

        @Override
        public Void structureShape(StructureShape structureShape) {
            writer.write("${memberName:L}.serialize(${serializer:L})");
            return null;
        }

        @Override
        public Void unionShape(UnionShape unionShape) {
            writer.write("${memberName:L}.serialize(${serializer:L})");
            return null;
        }

        @Override
        public Void memberShape(MemberShape memberShape) {
            // The error `message` member must be accessed from parent class.
            if (shape.hasTrait(ErrorTrait.class) && symbolProvider.toMemberName(memberShape).equals("message")) {
                writer.write("${serializer:L}.writeString(SCHEMA_MESSAGE, getMessage())");
                return null;
            }
            return model.expectShape(memberShape.getTarget()).accept(this);
        }

        @Override
        public Void timestampShape(TimestampShape timestampShape) {
            writer.write("${serializer:L}.writeTimestamp(${schemaName:L}, ${memberName:L})");
            return null;
        }
    }
}
