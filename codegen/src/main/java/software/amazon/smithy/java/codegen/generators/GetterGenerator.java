/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Optional;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.sections.GetterSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.StreamingTrait;

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

        private GetterGeneratorVisitor(JavaWriter writer, SymbolProvider symbolProvider, Model model, MemberShape member) {
            this.writer = writer;
            this.symbolProvider = symbolProvider;
            this.model = model;
            this.member = member;
        }

        @Override
        protected Void getDefault(Shape shape) {
            // If the member is required then do not wrap return in an Optional
            if (member.isRequired()) {
                writeRequiredGetter(shape);
            } else {
                writer.pushState(new GetterSection(member))
                        .write(
                                """
                                    public $1T<$2T> $3L() {
                                        return $1T.ofNullable($3L);
                                    }
                                    """,
                                Optional.class,
                                symbolProvider.toSymbol(shape),
                                symbolProvider.toMemberName(member)
                        )
                        .popState();
            }
            return null;
        }

        @Override
        public Void blobShape(BlobShape shape) {
            // Streaming blobs can never be optional returns
            if (shape.hasTrait(StreamingTrait.class)) {
                writeRequiredGetter(shape);
                return null;
            }
            getDefault(shape);
            return null;
        }

        @Override
        public Void listShape(ListShape shape) {
            getDefault(shape);
            writeOrElseEmptyMethod(shape);
            return null;
        }

        @Override
        public Void mapShape(MapShape shape) {
            getDefault(shape);
            writeOrElseEmptyMethod(shape);
            return null;
        }

        @Override
        public Void memberShape(MemberShape shape) {
            return model.expectShape(shape.getTarget()).accept(this);
        }

        private void writeRequiredGetter(Shape shape) {
            writer.pushState(new GetterSection(member))
                    .write(
                            """
                                public $1T $2L() {
                                    return $2L;
                                }
                                """,
                            symbolProvider.toSymbol(shape),
                            symbolProvider.toMemberName(member)
                    )
                    .popState();
        }

        private void writeOrElseEmptyMethod(Shape shape) {
            // If the member targets a collection shape and is optional then generate an unwrapped
            // getter as a convenience method as well.
            writer.write(
                    """
                        public $1T $2LOrEmpty() {
                            return $2L;
                        }
                        """,
                    symbolProvider.toSymbol(shape),
                    symbolProvider.toMemberName(member)
            );
        }
    }
}
