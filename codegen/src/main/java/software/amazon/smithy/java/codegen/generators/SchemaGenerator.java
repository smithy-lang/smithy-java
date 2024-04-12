/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.SchemaUtils;
import software.amazon.smithy.java.codegen.sections.TraitSection;
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
final class SchemaGenerator implements Runnable {

    private final JavaWriter writer;
    private final Shape shape;
    private final SymbolProvider symbolProvider;
    private final Model model;

    public SchemaGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model) {
        this.writer = writer;
        this.shape = shape;
        this.symbolProvider = symbolProvider;
        this.model = model;
    }

    @Override
    public void run() {
        shape.accept(new SchemaGeneratorVisitor(writer, symbolProvider, model));
    }

    private static final class SchemaGeneratorVisitor extends ShapeVisitor.Default<Void> {

        private final JavaWriter writer;
        private final SymbolProvider symbolProvider;
        private final Model model;

        private SchemaGeneratorVisitor(JavaWriter writer, SymbolProvider symbolProvider, Model model) {
            this.writer = writer;
            this.symbolProvider = symbolProvider;
            this.model = model;
        }

        @Override
        protected Void getDefault(Shape shape) {
            writer.write(
                """
                    static final $1T $2L = $1T.builder()
                        .type($3T.$4L)
                        .id($5S)${6C}
                        .build();
                    """,
                SdkSchema.class,
                SchemaUtils.toSchemaName(shape),
                ShapeType.class,
                shape.getType().name(),
                shape.toShapeId(),
                writer.consumer(w -> w.injectSection(new TraitSection(shape)))
            );
            return null;
        }

        @Override
        public Void listShape(ListShape shape) {
            var target = model.expectShape(shape.getMember().getTarget());
            writer.write(
                """
                    static final $1T $2L = $1T.builder()
                            .type($3T.LIST)
                            .id($4S)${5C}
                            .members(SdkSchema.memberBuilder(0, "member", $6C))
                            .build();
                    """,
                SdkSchema.class,
                SchemaUtils.toSchemaName(shape),
                ShapeType.class,
                shape.toShapeId(),
                writer.consumer(w -> w.injectSection(new TraitSection(shape))),
                writer.consumer(w -> SchemaUtils.writeSchemaType(w, target))
            );

            return null;
        }

        @Override
        public Void mapShape(MapShape shape) {
            var keyShape = model.expectShape(shape.getKey().getTarget());
            var valueShape = model.expectShape(shape.getValue().getTarget());
            writer.write(
                """
                    static final $1T $2L = $1T.builder()
                        .type($3T.MAP)
                        .id($4S)${5C}
                        .members(
                                $1T.memberBuilder(0, "key", $6C),
                                $1T.memberBuilder(1, "value", $7C)
                        )
                        .build();
                    """,
                SdkSchema.class,
                SchemaUtils.toSchemaName(shape),
                ShapeType.class,
                shape.toShapeId(),
                writer.consumer(w -> w.injectSection(new TraitSection(shape))),
                writer.consumer(w -> SchemaUtils.writeSchemaType(w, keyShape)),
                writer.consumer(w -> SchemaUtils.writeSchemaType(w, valueShape))
            );
            return null;
        }

        @Override
        public Void structureShape(StructureShape shape) {
            int idx = 0;
            for (var iter = shape.members().iterator(); iter.hasNext(); idx++) {
                writeNestedMemberSchema(idx, iter.next());
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
                    static final $1T SCHEMA = $1T.builder()
                        .id(ID)
                        .type($2T.$3L)${4C}
                        ${?memberSchemas}.members(${#memberSchemas}
                            ${value:L}${^key.last},${/key.last}${/memberSchemas}
                        )
                        ${/memberSchemas}.build();
                    """,
                SdkSchema.class,
                ShapeType.class,
                shape.getType().toString().toUpperCase(),
                writer.consumer(w -> w.injectSection(new TraitSection(shape)))
            );
            writer.popState();

            return null;
        }

        @Override
        public Void memberShape(MemberShape shape) {
            throw new UnsupportedOperationException("Member Shapes cannot directly Generate a Schema.");
        }

        private void writeNestedMemberSchema(int idx, MemberShape member) {
            var memberName = symbolProvider.toMemberName(member);
            var target = model.expectShape(member.getTarget());
            writer.write(
                """
                    private static final $1T $2L = $1T.memberBuilder($3L, $4S, $5C)
                        .id(ID)${6C}
                        .build();
                    """,
                SdkSchema.class,
                SchemaUtils.toMemberSchemaName(memberName),
                idx,
                memberName,
                writer.consumer(w -> SchemaUtils.writeSchemaType(w, target)),
                writer.consumer(w -> w.injectSection(new TraitSection(member)))
            );
        }
    }
}
