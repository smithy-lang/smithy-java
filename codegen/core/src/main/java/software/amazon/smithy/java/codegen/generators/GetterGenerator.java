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
record GetterGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model) implements Runnable {

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
            writer.putContext("symbol", shapeSymbol);
            writer.putContext("memberName", symbolProvider.toMemberName(member));
            writer.putContext("empty", shapeSymbol.expectProperty(SymbolProperties.COLLECTION_EMPTY_METHOD));
            writer.putContext("wrapper", shapeSymbol.expectProperty(SymbolProperties.COLLECTION_IMMUTABLE_WRAPPER));
            writer.putContext("collections", Collections.class);
            writer.putContext("isNullable", CodegenUtils.isNullableMember(member));
            writer.write(
                """
                    public ${symbol:T} ${memberName:L}() {
                        ${?isNullable}if (${memberName:L} == null) {
                            return ${collections:T}.${empty:L};
                        }${/isNullable}
                        return ${collections:T}.${wrapper:L}(${memberName:L});
                    }
                    """
            );

            // Write has-er to allow users to check if a given collection was set. Required collections must always be
            // set
            writer.write(
                """
                    public boolean has${memberName:U}() {
                        return ${?isNullable}${memberName:L} != null${/isNullable}${^isNullable}true${/isNullable};
                    }
                    """
            );

            writer.popState();
        }

        @Override
        public Void memberShape(MemberShape shape) {
            return model.expectShape(shape.getTarget()).accept(this);
        }
    }
}
