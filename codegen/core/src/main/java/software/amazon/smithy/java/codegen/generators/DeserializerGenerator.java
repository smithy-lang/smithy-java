/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.concurrent.Flow;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
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
import software.amazon.smithy.model.traits.StreamingTrait;

final class DeserializerGenerator extends ShapeVisitor.DataShapeVisitor<Void> implements Runnable {

    private final JavaWriter writer;
    private final Shape shape;
    private final SymbolProvider symbolProvider;
    private final Model model;
    private final ServiceShape service;
    private final String deserializer;
    private final String schemaName;

    DeserializerGenerator(
        JavaWriter writer,
        Shape shape,
        SymbolProvider symbolProvider,
        Model model,
        ServiceShape service,
        String deserializer,
        String schemaName
    ) {
        this.writer = writer;
        this.shape = shape;
        this.symbolProvider = symbolProvider;
        this.model = model;
        this.service = service;
        this.deserializer = deserializer;
        this.schemaName = schemaName;
    }

    @Override
    public void run() {
        writer.pushState();
        writer.putContext("schemaName", schemaName);
        writer.putContext("deserializer", deserializer);
        shape.accept(this);
        writer.popState();
    }

    @Override
    public Void blobShape(BlobShape blobShape) {
        if (blobShape.hasTrait(StreamingTrait.class)) {
            writer.write("${deserializer:L}.readDataStream(${schemaName:L})");
        } else {
            writer.write("${deserializer:L}.readBlob(${schemaName:L})");
        }
        return null;
    }

    @Override
    public Void booleanShape(BooleanShape booleanShape) {
        writer.write("${deserializer:L}.readBoolean(${schemaName:L})");
        return null;
    }

    @Override
    public Void listShape(ListShape listShape) {
        writer.write(
            "SharedSerde.deserialize$L(${schemaName:L}, ${deserializer:L})",
            CodegenUtils.getDefaultName(listShape, service)
        );
        return null;
    }

    @Override
    public Void mapShape(MapShape mapShape) {
        writer.write(
            "SharedSerde.deserialize$L(${schemaName:L}, ${deserializer:L})",
            CodegenUtils.getDefaultName(mapShape, service)
        );
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
    public Void intEnumShape(IntEnumShape shape) {
        delegateDeser();
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
        if (stringShape.hasTrait(StreamingTrait.class)) {
            writer.write("${deserializer:L}.readDataStream(${schemaName:L})");
        } else {
            writer.write("${deserializer:L}.readString(${schemaName:L})");
        }
        return null;
    }

    @Override
    public Void enumShape(EnumShape shape) {
        delegateDeser();
        return null;
    }

    @Override
    public Void structureShape(StructureShape structureShape) {
        delegateDeser();
        return null;
    }

    @Override
    public Void unionShape(UnionShape unionShape) {
        if (unionShape.hasTrait(StreamingTrait.class)) {
            Symbol flowPublisherType = CodegenUtils.fromClass(Flow.Publisher.class)
                .toBuilder()
                .addReference(symbolProvider.toSymbol(unionShape))
                .build();
            writer.write("($T) ${deserializer:L}.readEventStream(${schemaName:L})", flowPublisherType);
        } else {
            delegateDeser();
        }
        return null;
    }

    @Override
    public Void timestampShape(TimestampShape timestampShape) {
        writer.write("${deserializer:L}.readTimestamp(${schemaName:L})");
        return null;
    }

    private void delegateDeser() {
        writer.write("$T.builder().deserialize(${deserializer:L}).build()", symbolProvider.toSymbol(shape));
    }

    @Override
    public Void memberShape(MemberShape memberShape) {
        return model.expectShape(memberShape.getTarget()).accept(this);
    }
}
