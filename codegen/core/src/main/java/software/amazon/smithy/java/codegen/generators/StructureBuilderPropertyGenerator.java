/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ByteShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * Adds builder properties and initializers for structure shapes.
 */
record StructureBuilderPropertyGenerator(
    JavaWriter writer, SymbolProvider symbolProvider, Model model, StructureShape shape
) implements Runnable {

    @Override
    public void run() {
        writer.pushState();

        // Add any static default properties
        var defaultVisitor = new DefaultInitializerGenerator(writer, model, symbolProvider);
        for (var member : shape.members()) {
            if (member.hasNonNullDefault()
                && symbolProvider.toSymbol(member).expectProperty(SymbolProperties.REQUIRES_STATIC_DEFAULT)
            ) {
                defaultVisitor.member = member;
                writer.write(
                    "private static final $T $L = $C;",
                    symbolProvider.toSymbol(member),
                    CodegenUtils.toDefaultValueName(symbolProvider.toMemberName(member)),
                    defaultVisitor
                );
            }
        }

        // Add presence tracker
        writer.putContext("tracker", PresenceTracker.class);
        writer.write("private final ${tracker:T} tracker = ${tracker:T}.of(SCHEMA);");

        // Add non-static builder properties
        for (var member : shape.members()) {
            var memberName = symbolProvider.toMemberName(member);
            if (CodegenUtils.isStreamingBlob(model.expectShape(member.getTarget()))) {
                // Streaming blobs need a custom initializer
                writer.write(
                    "private $1T $2L = $1T.ofEmpty();",
                    DataStream.class,
                    memberName
                );
                continue;
            }
            writer.pushState();
            writer.putContext("nullable", CodegenUtils.isNullableMember(member));
            writer.putContext("default", member.hasNonNullDefault());
            writer.putContext(
                "static",
                symbolProvider.toSymbol(member).expectProperty(SymbolProperties.REQUIRES_STATIC_DEFAULT)
            );
            defaultVisitor.member = member;
            writer.write(
                "private ${^nullable}$1T${/nullable}${?nullable}$1B${/nullable} $2L${?default} = ${?static}$3L${/static}${^static}$4C${/static}${/default};",
                symbolProvider.toSymbol(member),
                memberName,
                CodegenUtils.toDefaultValueName(memberName),
                defaultVisitor
            );
            writer.popState();
        }
        writer.popState();
    }

    /**
     * Adds default values to builder properties.
     */
    private static final class DefaultInitializerGenerator extends ShapeVisitor.DataShapeVisitor<Void> implements
        Runnable {
        private final JavaWriter writer;
        private final Model model;
        private final SymbolProvider symbolProvider;
        private MemberShape member;
        private Node defaultValue;

        DefaultInitializerGenerator(
            JavaWriter writer,
            Model model,
            SymbolProvider symbolProvider
        ) {
            this.writer = writer;
            this.model = model;
            this.symbolProvider = symbolProvider;
        }

        @Override
        public void run() {
            if (member.hasNonNullDefault()) {
                this.defaultValue = member.expectTrait(DefaultTrait.class).toNode();
                member.accept(this);
            }
        }

        @Override
        public Void blobShape(BlobShape blobShape) {
            throw new UnsupportedOperationException("Blob default value cannot be set.");
        }

        @Override
        public Void booleanShape(BooleanShape booleanShape) {
            writer.write("$L", defaultValue.expectBooleanNode().getValue());
            return null;
        }

        @Override
        public Void listShape(ListShape listShape) {
            // Note that Lists can _ONLY_ have empty maps as the default,
            // so we do not need to check the default value. See:
            // https://github.com/smithy-lang/smithy/blob/main/designs/defaults-and-model-evolution.md
            writer.write(
                "$T.$L",
                Collections.class,
                symbolProvider.toSymbol(listShape).expectProperty(SymbolProperties.COLLECTION_EMPTY_METHOD)
            );
            return null;
        }

        @Override
        public Void mapShape(MapShape mapShape) {
            // Note that Maps can _ONLY_ have empty maps as the default,
            // so we do not need to check the default value. See:
            // https://github.com/smithy-lang/smithy/blob/main/designs/defaults-and-model-evolution.md
            writer.write(
                "$T.$L",
                Collections.class,
                symbolProvider.toSymbol(mapShape).expectProperty(SymbolProperties.COLLECTION_EMPTY_METHOD)
            );
            return null;
        }

        @Override
        public Void byteShape(ByteShape byteShape) {
            // Bytes duplicate the integer toString method
            writer.write("$L", defaultValue.expectNumberNode().getValue().intValue());
            return null;
        }

        @Override
        public Void shortShape(ShortShape shortShape) {
            // Shorts duplicate the int toString method
            writer.write("$L", defaultValue.expectNumberNode().getValue().intValue());
            return null;
        }

        @Override
        public Void integerShape(IntegerShape integerShape) {
            writer.write("$L", defaultValue.expectNumberNode().getValue().intValue());
            return null;
        }

        @Override
        public Void longShape(LongShape longShape) {
            writer.write("$LL", defaultValue.expectNumberNode().getValue().longValue());
            return null;
        }

        @Override
        public Void floatShape(FloatShape floatShape) {
            writer.write("$Lf", defaultValue.expectNumberNode().getValue().floatValue());
            return null;
        }

        @Override
        public Void documentShape(DocumentShape documentShape) {
            throw new UnsupportedOperationException("Document shape defaults cannot be set.");
        }

        @Override
        public Void doubleShape(DoubleShape doubleShape) {
            writer.write("$L", defaultValue.expectNumberNode().getValue().doubleValue());
            return null;
        }

        @Override
        public Void bigIntegerShape(BigIntegerShape bigIntegerShape) {
            writer.write("$T.valueOf($L)", BigInteger.class, defaultValue.expectNumberNode().getValue().intValue());
            return null;
        }

        @Override
        public Void bigDecimalShape(BigDecimalShape bigDecimalShape) {
            writer.write("$T.valueOf($L)", BigDecimal.class, defaultValue.expectNumberNode().getValue().doubleValue());
            return null;
        }

        @Override
        public Void stringShape(StringShape stringShape) {
            writer.write("$S", defaultValue.expectStringNode().getValue());
            return null;
        }

        @Override
        public Void structureShape(StructureShape structureShape) {
            throw new UnsupportedOperationException("Structure shape defaults cannot be set.");
        }

        @Override
        public Void unionShape(UnionShape unionShape) {
            throw new UnsupportedOperationException("Union shape defaults cannot be set.");

        }

        @Override
        public Void memberShape(MemberShape memberShape) {
            return model.expectShape(memberShape.getTarget()).accept(this);
        }

        @Override
        public Void timestampShape(TimestampShape timestampShape) {
            Instant value;
            if (member.hasTrait(TimestampFormatTrait.class)) {
                value = switch (member.expectTrait(TimestampFormatTrait.class).getFormat()) {
                    case EPOCH_SECONDS -> Instant.ofEpochMilli(defaultValue.expectNumberNode().getValue().longValue());
                    case HTTP_DATE -> Instant.from(
                        DateTimeFormatter.RFC_1123_DATE_TIME.parse(defaultValue.expectStringNode().getValue())
                    );
                    default -> instantFromDefaultTimestamp(defaultValue);
                };
            } else {
                value = instantFromDefaultTimestamp(defaultValue);
            }
            writer.write("$T.ofEpochMilli($LL)", Instant.class, value.toEpochMilli());
            return null;
        }

        private static Instant instantFromDefaultTimestamp(Node defaultValue) {
            if (defaultValue.isNumberNode()) {
                return Instant.ofEpochSecond(defaultValue.expectNumberNode().getValue().longValue());
            } else if (defaultValue.isStringNode()) {
                return Instant.parse(defaultValue.expectStringNode().getValue());
            }
            throw new IllegalArgumentException(
                "Invalid timestamp value node: " + defaultValue + "Expected string or number"
            );
        }
    }
}
