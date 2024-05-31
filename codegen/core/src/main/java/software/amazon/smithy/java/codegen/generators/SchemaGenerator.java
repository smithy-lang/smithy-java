/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Generates a schema constant for a given shape.
 *
 * <p>Member schemas are always generated as {@code private} while all other
 * schema properties are generated as {@code package-private}.
 */
@SmithyInternalApi
public final class SchemaGenerator extends ShapeVisitor.Default<Void> implements Runnable {

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
        writer.putContext("schemaClass", SdkSchema.class);
        writer.putContext("shapeTypeClass", ShapeType.class);
        writer.putContext("shapeId", shape.toShapeId());
        writer.putContext("shapeType", shape.getType().name());
        writer.putContext("traitInitializer", new TraitInitializerGenerator(writer, shape, context.runtimeTraits()));
        shape.accept(this);
        writer.popState();
    }

    @Override
    protected Void getDefault(Shape shape) {
        writer.write(
            """
                ${schemaClass:T}.builder()
                    .type(${shapeTypeClass:T}.${shapeType:L})
                    .id(${shapeId:S})${traitInitializer:C}
                    .build();
                """
        );
        return null;
    }

    @Override
    public Void listShape(ListShape shape) {
        writer.write(
            """
                ${schemaClass:T}.builder()
                    .type(${shapeTypeClass:T}.${shapeType:L})
                    .id(${shapeId:S})${traitInitializer:C}
                    .members(${C})
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
                ${schemaClass:T}.builder()
                    .type(${shapeTypeClass:T}.${shapeType:L})
                    .id(${shapeId:S})${traitInitializer:C}
                    .members(
                            ${C|},
                            ${C|}
                    )
                    .build();
                """,
            (Runnable) () -> shape.getKey().accept(this),
            (Runnable) () -> shape.getValue().accept(this)
        );
        return null;
    }

    @Override
    public Void intEnumShape(IntEnumShape shape) {
        writer.putContext("variants", shape.getEnumValues().keySet());
        writer.write(
            """
                static final ${schemaClass:T} SCHEMA = ${schemaClass:T}.builder()
                    .id(ID)
                    .type(${shapeTypeClass:T}.${shapeType:L})${traitInitializer:C}
                    .intEnumValues(
                        ${#variants}${value:L}.value${^key.last},
                        ${/key.last}${/variants}
                    )
                    .build();
                """
        );
        return null;
    }

    // TODO: handle string enums?
    @Override
    public Void enumShape(EnumShape shape) {
        writer.putContext("variants", shape.getEnumValues().keySet());
        writer.write(
            """
                static final ${schemaClass:T} SCHEMA = ${schemaClass:T}.builder()
                    .id(ID)
                    .type(${shapeTypeClass:T}.${shapeType:L})${traitInitializer:C}
                    .stringEnumValues(
                        ${#variants}${value:L}.value${^key.last},
                        ${/key.last}${/variants}
                    )
                    .build();
                """
        );
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
        for (var member : shape.members()) {
            writeNestedMemberSchema(member);
        }

        // Add the member schema names to the context, so we can iterate through them
        writer.putContext(
            "memberSchemas",
            CodegenUtils.getSortedMembers(shape)
                .stream()
                .map(symbolProvider::toMemberName)
                .map(CodegenUtils::toMemberSchemaName)
                .toList()
        );
        writer.write(
            """
                static final ${schemaClass:T} SCHEMA = ${schemaClass:T}.builder()
                    .id(ID)
                    .type(${shapeTypeClass:T}.${shapeType:L})${traitInitializer:C}
                    ${?memberSchemas}.members(${#memberSchemas}
                        ${value:L}${^key.last},${/key.last}${/memberSchemas}
                    )
                    ${/memberSchemas}.build();
                """
        );
    }

    @Override
    public Void memberShape(MemberShape shape) {
        var target = model.expectShape(shape.getTarget());
        writer.putContext("member", symbolProvider.toMemberName(shape));
        writer.putContext("schema", CodegenUtils.getSchemaType(writer, symbolProvider, target));
        writer.write("${schemaClass:T}.memberBuilder(${member:S}, ${schema:L})${traitInitializer:C}");

        return null;
    }

    private void writeNestedMemberSchema(MemberShape member) {
        var memberName = symbolProvider.toMemberName(member);
        var target = model.expectShape(member.getTarget());

        writer.pushState();
        writer.putContext("schema", CodegenUtils.getSchemaType(writer, symbolProvider, target));
        writer.putContext("memberName", memberName);
        writer.putContext("name", CodegenUtils.toMemberSchemaName(memberName));
        writer.write(
            """
                private static final ${schemaClass:T} ${name:L} = ${schemaClass:T}.memberBuilder(${memberName:S}, ${schema:L})
                    .id(ID)${C}
                    .build();
                """,
            new TraitInitializerGenerator(writer, member, context.runtimeTraits())
        );
        writer.popState();
    }
}
