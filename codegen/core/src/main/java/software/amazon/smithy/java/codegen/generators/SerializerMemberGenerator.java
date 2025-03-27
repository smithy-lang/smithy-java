/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.ContextualDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.Unit;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ByteShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.traits.UnitTypeTrait;

final class SerializerMemberGenerator extends ShapeVisitor.DataShapeVisitor<Void> implements
        Runnable {
    private final JavaWriter writer;
    private final SymbolProvider provider;
    private final Model model;
    private final ServiceShape service;
    private final Shape shape;
    private final String state;
    private final ContextualDirective<CodeGenerationContext, ?> directive;

    SerializerMemberGenerator(
            ContextualDirective<CodeGenerationContext, ?> directive,
            JavaWriter writer,
            Shape shape,
            String state
    ) {
        this.directive = directive;
        this.writer = writer;
        this.provider = directive.symbolProvider();
        this.model = directive.model();
        this.service = directive.service();
        this.shape = shape;
        this.state = state;
    }

    @Override
    public void run() {
        writer.pushState();
        writer.putContext("state", state);
        writer.putContext("schema", "$SCHEMA");
        shape.accept(this);
        writer.popState();
    }

    @Override
    public Void blobShape(BlobShape blobShape) {
        if (CodegenUtils.isStreamingBlob(blobShape)) {
            writer.write("serializer.writeDataStream(${schema:L}, ${state:L})");
        } else {
            writer.write("serializer.writeBlob(${schema:L}, ${state:L})");
        }
        return null;
    }

    @Override
    public Void booleanShape(BooleanShape booleanShape) {
        writer.write("serializer.writeBoolean(${schema:L}, ${state:L})");
        return null;
    }

    @Override
    public Void listShape(ListShape listShape) {
        writer.write(
                "serializer.writeList(${schema:L}, ${state:L}, ${state:L}.size(), SharedSerde.$USerializer.INSTANCE)",
                CodegenUtils.getDefaultName(listShape, service));
        return null;
    }

    @Override
    public Void mapShape(MapShape mapShape) {
        writer.write(
                "serializer.writeMap(${schema:L}, ${state:L}, ${state:L}.size(), SharedSerde.$USerializer.INSTANCE)",
                CodegenUtils.getDefaultName(mapShape, service));
        return null;
    }

    @Override
    public Void byteShape(ByteShape byteShape) {
        writer.write("serializer.writeByte(${schema:L}, ${state:L})");
        return null;
    }

    @Override
    public Void shortShape(ShortShape shortShape) {
        writer.write("serializer.writeShort(${schema:L}, ${state:L})");
        return null;
    }

    @Override
    public Void integerShape(IntegerShape integerShape) {
        writer.write("serializer.writeInteger(${schema:L}, ${state:L})");
        return null;
    }

    @Override
    public Void intEnumShape(IntEnumShape shape) {
        writer.write("serializer.writeInteger(${schema:L}, ${state:L}.value())");
        return null;
    }

    @Override
    public Void longShape(LongShape longShape) {
        writer.write("serializer.writeLong(${schema:L}, ${state:L})");
        return null;
    }

    @Override
    public Void floatShape(FloatShape floatShape) {
        writer.write("serializer.writeFloat(${schema:L}, ${state:L})");
        return null;
    }

    @Override
    public Void documentShape(DocumentShape documentShape) {
        writer.write("serializer.writeDocument(${schema:L}, ${state:L})");
        return null;
    }

    @Override
    public Void doubleShape(DoubleShape doubleShape) {
        writer.write("serializer.writeDouble(${schema:L}, ${state:L})");
        return null;
    }

    @Override
    public Void bigIntegerShape(BigIntegerShape bigIntegerShape) {
        writer.write("serializer.writeBigInteger(${schema:L}, ${state:L})");
        return null;
    }

    @Override
    public Void bigDecimalShape(BigDecimalShape bigDecimalShape) {
        writer.write("serializer.writeBigDecimal(${schema:L}, ${state:L})");
        return null;
    }

    @Override
    public Void stringShape(StringShape stringShape) {
        if (stringShape.hasTrait(StreamingTrait.class)) {
            writer.write("serializer.writeDataStream(${schema:L}, ${state:L})");
        } else {
            writer.write("serializer.writeString(${schema:L}, ${state:L})");
        }
        return null;
    }

    @Override
    public Void enumShape(EnumShape shape) {
        writer.write("serializer.writeString(${schema:L}, ${state:L}.value())");
        return null;
    }

    @Override
    public Void structureShape(StructureShape structureShape) {
        if (structureShape.hasTrait(UnitTypeTrait.class)) {
            writer.write("serializer.writeStruct(${schema:L}, $T.getInstance())", Unit.class);
        } else {
            writer.write("serializer.writeStruct(${schema:L}, ${state:L})");
        }
        return null;
    }

    @Override
    public Void unionShape(UnionShape unionShape) {
        if (unionShape.hasTrait(StreamingTrait.class)) {
            writer.write("serializer.writeEventStream(${schema:L}, ${state:L})");
        } else {
            writer.write("serializer.writeStruct(${schema:L}, ${state:L})");
        }
        return null;
    }

    @Override
    public Void memberShape(MemberShape memberShape) {
        // The error `message` member must be accessed from parent class.
        var memberName = provider.toMemberName(memberShape);
        if (memberShape.hasTrait(ErrorTrait.class) && memberName.equals("message")) {
            writer.write("serializer.writeString(SCHEMA_MESSAGE, ${state:L}.getMessage())");
            return null;
        }
        var container = model.expectShape(memberShape.getContainer());
        var schemaFieldOder = directive.context().schemaFieldOrder();
        if (container.isListShape()) {
            var memberSchema =
                    schemaFieldOder.getSchemaFieldName(container, writer) + ".listMember()";
            writer.putContext("schema", memberSchema);
        } else if (container.isMapShape()) {
            var memberSchema =
                    schemaFieldOder.getSchemaFieldName(container, writer) + ".mapValueMember()";
            writer.putContext("schema", memberSchema);
        } else {
            writer.putContext("schema", CodegenUtils.toMemberSchemaName(memberName));
        }
        return model.expectShape(memberShape.getTarget()).accept(this);
    }

    @Override
    public Void timestampShape(TimestampShape timestampShape) {
        writer.write("serializer.writeTimestamp(${schema:L}, ${state:L})");
        return null;
    }
}
