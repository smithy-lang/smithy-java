/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Collections;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.StreamingTrait;

/**
 * TODO: docs
 */
record ErrorCorrectionGenerator(JavaWriter writer, SymbolProvider symbolProvider, Model model, StructureShape shape)
    implements Runnable {

    @Override
    public void run() {
        if (shape.members().stream().noneMatch(CodegenUtils::isRequiredWithNoDefault)) {
            // Do not generate error correction if there are no required members with no default.
            return;
        }

        writer.write(
            """
                @Override
                public ${sdkShapeBuilder:T}<${shape:T}> errorCorrection() {
                    if (tracker.allSet()) {
                        return this;
                    }

                    ${C|}

                    return this;
                }
                """,
            (Runnable) this::writeErrorCorrectionMembers
        );
    }

    private void writeErrorCorrectionMembers() {
        var visitor = new ErrorCorrectionVisitor(writer, symbolProvider, model);
        for (var member : shape.members()) {
            if (CodegenUtils.isRequiredWithNoDefault(member)) {
                var memberName = symbolProvider.toMemberName(member);
                var schemaName = CodegenUtils.toMemberSchemaName(memberName);
                visitor.memberShape = member;
                writer.openBlock("if (!tracker.checkMember($L)) {", "}", schemaName, () -> {
                    if (CodegenUtils.hasBuiltinDefault(symbolProvider, model, member)) {
                        writer.write("tracker.setMember($1L);", schemaName);
                    } else {
                        writer.write("$L($C);", memberName, visitor);
                    }
                });
            }
        }
    }

    /**
     * Returns the error correction value to use for a required field.
     *
     * @see <a href="https://smithy.io/2.0/spec/aggregate-types.html#client-error-correction">client error correction</a>
     */
    private static final class ErrorCorrectionVisitor extends ShapeVisitor.Default<Void> implements Runnable {
        private final JavaWriter writer;
        private final SymbolProvider symbolProvider;
        private final Model model;
        private MemberShape memberShape;

        private ErrorCorrectionVisitor(JavaWriter writer, SymbolProvider symbolProvider, Model model) {
            this.writer = writer;
            this.symbolProvider = symbolProvider;
            this.model = model;
        }

        @Override
        public void run() {
            memberShape.accept(this);
        }

        @Override
        protected Void getDefault(Shape shape) {
            throw new IllegalArgumentException("Could not generate error correction value for " + shape);
        }

        @Override
        public Void blobShape(BlobShape blobShape) {
            if (blobShape.hasTrait(StreamingTrait.class)) {
                writer.writeInline("$T.ofEmpty()", DataStream.class);
            } else {
                writer.writeInlineWithNoFormatting("new byte[0]");
            }
            return null;
        }

        @Override
        public Void listShape(ListShape listShape) {
            writer.writeInline(
                "$T.$L",
                Collections.class,
                symbolProvider.toSymbol(listShape).expectProperty(SymbolProperties.COLLECTION_EMPTY_METHOD)
            );
            return null;
        }

        @Override
        public Void mapShape(MapShape mapShape) {
            writer.writeInline(
                "$T.$L",
                Collections.class,
                symbolProvider.toSymbol(mapShape).expectProperty(SymbolProperties.COLLECTION_EMPTY_METHOD)
            );
            return null;
        }

        @Override
        public Void documentShape(DocumentShape documentShape) {
            writer.writeInline("null");
            return null;
        }

        @Override
        public Void bigIntegerShape(BigIntegerShape bigIntegerShape) {
            writer.writeInline("$T.ZERO", BigInteger.class);
            return null;
        }

        @Override
        public Void bigDecimalShape(BigDecimalShape bigDecimalShape) {
            writer.writeInline("$T.ZERO", BigDecimal.class);
            return null;
        }

        @Override
        public Void stringShape(StringShape stringShape) {
            writer.writeInline("\"\"");
            return null;
        }

        @Override
        public Void structureShape(StructureShape structureShape) {
            // Attempts to make an empty structure member. This could fail if the nested struct
            // has required members.
            writer.writeInline("$T.builder().build()", symbolProvider.toSymbol(structureShape));
            return null;
        }

        @Override
        public Void unionShape(UnionShape unionShape) {
            writer.writeInline("new $T.$$$$Unknown(null)", symbolProvider.toSymbol(unionShape));
            return null;
        }


        @Override
        public Void enumShape(EnumShape shape) {
            // TODO: Implement
            return null;
        }

        @Override
        public Void intEnumShape(IntEnumShape shape) {
            // TODO: Implement
            return null;
        }

        @Override
        public Void timestampShape(TimestampShape timestampShape) {
            writer.write("$T.EPOCH", Instant.class);
            return null;
        }

        @Override
        public Void memberShape(MemberShape memberShape) {
            return model.expectShape(memberShape.getTarget()).accept(this);
        }
    }
}
