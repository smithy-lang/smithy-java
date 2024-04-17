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
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.StructureShape;
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
        shape.accept(this);
        writer.popState();
    }

    @Override
    protected Void getDefault(Shape shape) {
        writer.write(
            """
                ${schemaClass:T}.builder()
                    .type(${shapeTypeClass:T}.$1L)
                    .id($2S)${3C}
                    .build();
                """,
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
                ${schemaClass:T}.builder()
                    .type(${shapeTypeClass:T}.LIST)
                    .id($1S)${2C}
                    .members(${3C})
                    .build();
                """,
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
                ${schemaClass:T}.builder()
                    .type(${shapeTypeClass:T}.MAP)
                    .id($1S)${2C}
                    .members(
                            ${3C|},
                            ${4C|}
                    )
                    .build();
                """,
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
            CodegenUtils.getSchemaType(writer, symbolProvider, target),
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
            CodegenUtils.toMemberSchemaName(memberName),
            memberName,
            CodegenUtils.getSchemaType(writer, symbolProvider, target),
            new TraitInitializerGenerator(writer, member, context.runtimeTraits())
        );
    }
}
