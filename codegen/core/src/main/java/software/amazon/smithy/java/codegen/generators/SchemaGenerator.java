/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Set;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SchemaBuilder;
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
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;

/**
 * Generates a schema constant for a given shape.
 *
 * <p>Member schemas are always generated as {@code private} while all other
 * schema properties are generated as {@code package-private}.
 */
final class SchemaGenerator implements ShapeVisitor<Void>, Runnable {

    private final JavaWriter writer;
    private final Shape shape;
    private final SymbolProvider symbolProvider;
    private final Model model;
    private final CodeGenerationContext context;

    public SchemaGenerator(
        JavaWriter writer,
        Shape shape,
        SymbolProvider symbolProvider,
        Model model,
        CodeGenerationContext context
    ) {
        this.writer = writer;
        this.shape = shape;
        this.symbolProvider = symbolProvider;
        this.model = model;
        this.context = context;
    }

    @Override
    public void run() {
        writer.pushState();
        writer.putContext("schemaClass", Schema.class);
        writer.putContext("id", shape.toShapeId());
        writer.putContext("shapeId", ShapeId.class);
        writer.putContext("schemaBuilder", SchemaBuilder.class);
        writer.putContext("name", CodegenUtils.toSchemaName(shape));
        writer.putContext("traits", new TraitInitializerGenerator(writer, shape, context));
        writer.putContext("recursive", CodegenUtils.recursiveShape(model, shape));
        shape.accept(this);
        writer.popState();
    }

    @Override
    public Void blobShape(BlobShape blobShape) {
        writer.write(
            "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createBlob(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void booleanShape(BooleanShape booleanShape) {
        writer.write(
            "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createBoolean(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void listShape(ListShape shape) {
        writer.write(
            """
                ${?recursive}static final ${schemaBuilder:T} ${name:L}_BUILDER = ${schemaClass:T}.listBuilder(${shapeId:T}.from(${id:S})${traits:C});
                ${/recursive}static final ${schemaClass:T} ${name:L} = ${?recursive}${name:L}_BUILDER${/recursive}${^recursive}${schemaClass:T}.listBuilder(${shapeId:T}.from(${id:S})${traits:C})${/recursive}
                    ${C|}
                    .build();
                """,
            (Runnable) () -> shape.getMember().accept(this)
        );
        return null;
    }

    @Override
    public Void mapShape(MapShape shape) {
        writer.write(
            """
                ${?recursive}static final ${schemaBuilder:T} ${name:L}_BUILDER = ${schemaClass:T}.mapBuilder(${shapeId:T}.from(${id:S})${traits:C});
                ${/recursive}static final ${schemaClass:T} ${name:L} = ${?recursive}${name:L}_BUILDER${/recursive}${^recursive}${schemaClass:T}.mapBuilder(${shapeId:T}.from(${id:S})${traits:C})${/recursive}
                    ${C|}
                    ${C|}
                    .build();
                """,
            (Runnable) () -> shape.getKey().accept(this),
            (Runnable) () -> shape.getValue().accept(this)
        );
        return null;
    }

    @Override
    public Void byteShape(ByteShape byteShape) {
        writer.write(
            "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createByte(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void shortShape(ShortShape shortShape) {
        writer.write(
            "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createShort(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void integerShape(IntegerShape integerShape) {
        writer.write(
            "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createInteger(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void intEnumShape(IntEnumShape shape) {
        writer.putContext("variants", shape.getEnumValues().keySet());
        writer.putContext("set", Set.class);
        writer.write("""
            static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createIntEnum(ID,
                ${set:T}.of(${#variants}${value:L}.value${^key.last},${/key.last}${/variants})${traits:C}
            );
            """);
        return null;
    }

    @Override
    public Void longShape(LongShape longShape) {
        writer.write(
            "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createLong(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void floatShape(FloatShape floatShape) {
        writer.write(
            "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createFloat(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void documentShape(DocumentShape documentShape) {
        writer.write(
            "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createDocument(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void doubleShape(DoubleShape doubleShape) {
        writer.write(
            "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createDouble(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void bigIntegerShape(BigIntegerShape bigIntegerShape) {
        writer.write(
            "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createBigInteger(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void bigDecimalShape(BigDecimalShape bigDecimalShape) {
        writer.write(
            "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createBigDecimal(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void enumShape(EnumShape shape) {
        writer.putContext("variants", shape.getEnumValues().keySet());
        writer.putContext("set", Set.class);
        writer.write("""
            static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createEnum(ID,
                ${set:T}.of(${#variants}${value:L}.value${^key.last},${/key.last}${/variants})${traits:C}
            );
            """);
        return null;
    }

    @Override
    public Void structureShape(StructureShape shape) {
        generateStructMemberSchemas(shape);
        return null;
    }

    @Override
    public Void unionShape(UnionShape shape) {
        generateStructMemberSchemas(shape);
        return null;
    }

    private void generateStructMemberSchemas(Shape shape) {
        writer.pushState();
        writer.putContext("hasMembers", !shape.members().isEmpty());
        writer.write(
            """
                ${?recursive}static final ${schemaBuilder:T} ${name:L}_BUILDER = ${schemaClass:T}.structureBuilder(ID${traits:C});
                ${/recursive}static final ${schemaClass:T} ${name:L} = ${?recursive}${name:L}_BUILDER${/recursive}${^recursive}${schemaClass:T}.structureBuilder(ID${traits:C})${/recursive}${?hasMembers}
                    ${C|}
                    ${/hasMembers}.build();
                """,
            (Runnable) () -> shape.members().forEach(m -> m.accept(this))
        );

        // Write a static property for faster access to each member schema.
        for (var member : shape.members()) {
            writeMemberProperty(member);
        }

        writer.popState();
    }

    private void writeMemberProperty(MemberShape member) {
        writer.pushState();
        writer.putContext("memberName", member.getMemberName());
        writer.putContext("memberSchema", CodegenUtils.toMemberSchemaName(symbolProvider.toMemberName(member)));
        writer.write("private static final ${schemaClass:T} ${memberSchema:L} = ${name:L}.member(${memberName:S});");
        writer.popState();
    }

    @Override
    public Void memberShape(MemberShape shape) {
        var target = model.expectShape(shape.getTarget());
        writer.pushState();
        writer.putContext("memberName", shape.getMemberName());
        writer.putContext("schema", CodegenUtils.getSchemaType(writer, symbolProvider, target));
        writer.putContext("traits", new TraitInitializerGenerator(writer, shape, context));
        writer.putContext("recursive", CodegenUtils.recursiveShape(model, target));
        writer.write(".putMember(${memberName:S}, ${schema:L}${?recursive}_BUILDER${/recursive}${traits:C})");
        writer.popState();
        return null;
    }

    @Override
    public Void timestampShape(TimestampShape timestampShape) {
        writer.write(
            "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createTimestamp(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void stringShape(StringShape stringShape) {
        writer.write(
            "static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createString(${shapeId:T}.from(${id:S})${traits:C});"
        );
        return null;
    }

    @Override
    public Void operationShape(OperationShape shape) {
        writer.write("static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.createOperation(ID${traits:C});");
        return null;
    }

    @Override
    public Void resourceShape(ResourceShape resourceShape) {
        throw new UnsupportedOperationException("Schema generation not supported for resource Shapes");
    }

    @Override
    public Void serviceShape(ServiceShape serviceShape) {
        throw new UnsupportedOperationException("Schema generation not supported for service Shapes");
    }
}
