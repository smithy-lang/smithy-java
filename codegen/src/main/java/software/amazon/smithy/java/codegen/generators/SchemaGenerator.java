/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.SchemaUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Generates a schema constant for a given shape.
 *
 * <p>Member schemas are always generated as {@code private} while all other
 * schema properties are generated as {@code package-private}.
 */
final class SchemaGenerator extends ShapeVisitor.Default<Void> implements Runnable {

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
        shape.accept(this);
        writer.popState();
    }

    @Override
    protected Void getDefault(Shape shape) {
        writer.write(
            """
                static final ${schemaClass:T} $1L = ${schemaClass:T}.builder()
                    .type(${shapeTypeClass:T}.$2L)
                    .id($3S)${4C}
                    .build();
                """,
            SchemaUtils.toSchemaName(shape),
            shape.getType().name(),
            shape.toShapeId(),
            new TraitInitializerGenerator(writer, shape, context.runtimeTraits())
        );
        return null;
    }

    @Override
    public Void listShape(ListShape shape) {
        writer.write(
            """
                static final ${schemaClass:T} $1L = ${schemaClass:T}.builder()
                    .type(${shapeTypeClass:T}.LIST)
                    .id($2S)${3C}
                    .members(${4C})
                    .build();
                """,
            SchemaUtils.toSchemaName(shape),
            shape.toShapeId(),
            new TraitInitializerGenerator(writer, shape, context.runtimeTraits()),
            (Runnable) () -> shape.getMember().accept(this)
        );

        return null;
    }

    @Override
    public Void mapShape(MapShape shape) {
        writer.write(
            """
                static final ${schemaClass:T} $1L = ${schemaClass:T}.builder()
                    .type(${shapeTypeClass:T}.MAP)
                    .id($2S)${3C}
                    .members(
                            ${4C|},
                            ${5C|}
                    )
                    .build();
                """,
            SchemaUtils.toSchemaName(shape),
            shape.toShapeId(),
            new TraitInitializerGenerator(writer, shape, context.runtimeTraits()),
            (Runnable) () -> shape.getKey().accept(this),
            (Runnable) () -> shape.getValue().accept(this)
        );
        return null;
    }

    @Override
    public Void structureShape(StructureShape shape) {
        for (var member : shape.members()) {
            writeNestedMemberSchema(member);
        }
        writer.pushState();
        // Add the member schema names to the context, so we can iterate through them
        writer.putContext(
            "memberSchemas",
            shape.members()
                .stream()
                .map(symbolProvider::toMemberName)
                .map(SchemaUtils::toMemberSchemaName)
                .toList()
        );
        writer.write(
            """
                static final ${schemaClass:T} SCHEMA = ${schemaClass:T}.builder()
                    .id(ID)
                    .type(${shapeTypeClass:T}.$1L)${2C}
                    ${?memberSchemas}.members(${#memberSchemas}
                        ${value:L}${^key.last},${/key.last}${/memberSchemas}
                    )
                    ${/memberSchemas}.build();
                """,
            shape.getType().toString().toUpperCase(),
            new TraitInitializerGenerator(writer, shape, context.runtimeTraits())
        );
        writer.popState();

        return null;
    }

    @Override
    public Void memberShape(MemberShape shape) {
        var target = model.expectShape(shape.getTarget());
        writer.write(
            "${schemaClass:T}.memberBuilder($1S, $2L)${3C}",
            symbolProvider.toMemberName(shape),
            SchemaUtils.getSchemaType(writer, symbolProvider, target),
            new TraitInitializerGenerator(writer, shape, context.runtimeTraits())
        );
        return null;
    }

    private void writeNestedMemberSchema(MemberShape member) {
        var memberName = symbolProvider.toMemberName(member);
        var target = model.expectShape(member.getTarget());
        writer.write(
            """
                private static final ${schemaClass:T} $1L = ${schemaClass:T}.memberBuilder($2S, $3L)
                    .id(ID)${4C}
                    .build();
                """,
            SchemaUtils.toMemberSchemaName(memberName),
            memberName,
            SchemaUtils.getSchemaType(writer, symbolProvider, target),
            new TraitInitializerGenerator(writer, member, context.runtimeTraits())
        );

        generateCollectionMemberSchemas(target, SchemaUtils.toMemberSchemaName(memberName));
    }

    /**
     * Generates a static constant for member schemas of list and map shapes for use in serde.
     * Other shapes are ignored.
     *
     * <p>These member schemas are resolved using the {@link SdkSchema#member(String)} method and allow serde
     * code to use the member schemas without creating a new variable in a consumer lambda each time serde is performed.
     *
     * @param shape shape to generate member schemas for
     * @param baseSchemaName name of the list or map schema to get the member schema for
     */
    private void generateCollectionMemberSchemas(Shape shape, String baseSchemaName) {
        if (shape.isListShape()) {
            writer.write(
                "private static final ${schemaClass:T} $1L_MEMBER = $1L.member(\"member\");",
                baseSchemaName
            );
            generateCollectionMemberSchemas(
                model.expectShape(shape.asListShape().get().getMember().getTarget()),
                baseSchemaName + "_MEMBER"
            );
        } else if (shape.isMapShape()) {
            writer.write("private static final ${schemaClass:T} $1L_KEY = $1L.member(\"key\");", baseSchemaName);
            writer.write(
                "private static final ${schemaClass:T} $1L_VALUE = $1L.member(\"value\");",
                baseSchemaName
            );
            generateCollectionMemberSchemas(
                model.expectShape(shape.asMapShape().get().getValue().getTarget()),
                baseSchemaName + "_VALUE"
            );
        }
    }
}
