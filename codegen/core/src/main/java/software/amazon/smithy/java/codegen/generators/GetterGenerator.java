/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Collections;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.sections.GetterSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.traits.ErrorTrait;

/**
 * Generates getters for a shape.
 */
final class GetterGenerator implements Runnable {
    private final JavaWriter writer;
    private final Shape shape;
    private final SymbolProvider symbolProvider;
    private final Model model;

    GetterGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model) {
        this.shape = shape;
        this.symbolProvider = symbolProvider;
        this.writer = writer;
        this.model = model;
    }

    @Override
    public void run() {
        for (var member : shape.members()) {
            // Error message members do not generate a getter
            if (shape.hasTrait(ErrorTrait.class) && symbolProvider.toMemberName(member).equals("message")) {
                continue;
            }
            member.accept(new GetterGeneratorVisitor(writer, symbolProvider, model, member));
        }
    }

    private static final class GetterGeneratorVisitor extends ShapeVisitor.Default<Void> {
        private final JavaWriter writer;
        private final SymbolProvider symbolProvider;
        private final Model model;
        private final MemberShape member;

        private GetterGeneratorVisitor(
            JavaWriter writer,
            SymbolProvider symbolProvider,
            Model model,
            MemberShape member
        ) {
            this.writer = writer;
            this.symbolProvider = symbolProvider;
            this.model = model;
            this.member = member;
        }

        @Override
        protected Void getDefault(Shape shape) {
            // If the member is not required then prefer the boxed type
            writer.pushState(new GetterSection(member));
            writer.putContext("isNullable", CodegenUtils.isNullableMember(member));
            writer.write(
                """
                    public ${?isNullable}$1T${/isNullable}${^isNullable}$1B${/isNullable} $2L() {
                        return $2L;
                    }
                    """,
                symbolProvider.toSymbol(shape),
                symbolProvider.toMemberName(member)
            );
            writer.popState();

            return null;
        }

        @Override
        public Void listShape(ListShape shape) {
            writeCollectionGetter(shape);
            return null;
        }

        @Override
        public Void mapShape(MapShape shape) {
            writeCollectionGetter(shape);
            return null;
        }

        private void writeCollectionGetter(Shape shape) {
            writer.pushState(new GetterSection(member));
            var shapeSymbol = symbolProvider.toSymbol(shape);
            // If the collection is nullable use an empty collection if null
            if (CodegenUtils.isNullableMember(member)) {
                writer.write(
                    """
                        public $1T $2L() {
                            return $2L != null ? $2L : $3T.$4L;
                        }
                        """,
                    shapeSymbol,
                    symbolProvider.toMemberName(member),
                    Collections.class,
                    shapeSymbol.expectProperty(SymbolProperties.COLLECTION_EMPTY_METHOD)
                );
            } else {
                writer.write(
                    """
                        public $1T $2L() {
                            return $2L;
                        }
                        """,
                    shapeSymbol,
                    symbolProvider.toMemberName(member)
                );
            }
            writer.popState();
            writeHasCollection();
        }

        @Override
        public Void memberShape(MemberShape shape) {
            return model.expectShape(shape.getTarget()).accept(this);
        }

        private void writeHasCollection() {
            // If the member targets a collection shape and is optional then generate an unwrapped
            // getter as a convenience method as well.
            var memberName = symbolProvider.toMemberName(member);
            writer.write(
                """
                    public boolean has$1U() {
                        return $1L != null;
                    }
                    """,
                memberName
            );
        }
    }
}
