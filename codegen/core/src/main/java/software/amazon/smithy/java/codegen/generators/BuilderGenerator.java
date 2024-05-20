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
import java.util.Objects;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * Generates a static nested {@code Builder} class for a Java class.
 */
final class BuilderGenerator implements Runnable {

    private final JavaWriter writer;
    private final Shape shape;
    private final SymbolProvider symbolProvider;
    private final Model model;
    private final ServiceShape service;

    public BuilderGenerator(
        JavaWriter writer,
        Shape shape,
        SymbolProvider symbolProvider,
        Model model,
        ServiceShape service
    ) {
        this.writer = writer;
        this.shape = shape;
        this.symbolProvider = symbolProvider;
        this.model = model;
        this.service = service;
    }

    @Override
    public void run() {
        writer.pushState();
        writer.putContext("hasMembers", !shape.members().isEmpty());
        writer.putContext("shape", symbolProvider.toSymbol(shape));
        writer.putContext("sdkShapeBuilder", SdkShapeBuilder.class);
        writer.putContext("shapeDeserializer", ShapeDeserializer.class);
        writer.putContext("tracker", PresenceTracker.class);
        writer.putContext("serdeException", SdkSerdeException.class);
        writer.putContext("sdkSchema", SdkSchema.class);
        writer.putContext(
            "needsErrorCorrection",
            shape.members().stream().anyMatch(CodegenUtils::isRequiredWithNoDefault)
        );
        writer.write(
            """
                public static Builder builder() {
                    return new Builder();
                }

                /**
                 * Builder for {@link ${shape:T}}.
                 */
                public static final class Builder implements ${sdkShapeBuilder:T}<${shape:T}> {
                    ${C|}

                    private final ${tracker:T} tracker = ${tracker:T}.of(SCHEMA);

                    private Builder() {}

                    ${C|}

                    @Override
                    public ${shape:T} build() {
                        tracker.validate();
                        return new ${shape:T}(this);
                    }

                    ${?needsErrorCorrection}@Override
                    public SdkShapeBuilder<${shape:T}> errorCorrection() {
                        if (tracker.allSet()) {
                            return this;
                        }

                        ${C|}

                        return this;
                    }
                    ${/needsErrorCorrection}

                    @Override
                    public Builder deserialize(${shapeDeserializer:T} decoder) {
                        decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
                        return this;
                    }

                    private static final class InnerDeserializer implements ${shapeDeserializer:T}.StructMemberConsumer<Builder> {
                        private static final InnerDeserializer INSTANCE = new InnerDeserializer();

                        @Override
                        public void accept(Builder builder, ${sdkSchema:T} member, ${shapeDeserializer:T} de) {
                            ${?hasMembers}switch (member.memberIndex()) {
                                ${C|}
                            }${/hasMembers}
                        }
                    }
                }""",

            (Runnable) this::builderProperties,
            (Runnable) this::builderSetters,
            (Runnable) this::errorCorrection,
            (Runnable) this::generateMemberSwitchCases
        );
        writer.popState();
    }

    // Adds builder properties and initializers
    private void builderProperties() {
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
    }

    private void builderSetters() {
        for (var member : shape.members()) {
            new SetterGenerator(writer, symbolProvider, model, member).run();
        }
    }

    private void errorCorrection() {
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

    private void generateMemberSwitchCases() {
        int idx = 0;
        for (var iter = CodegenUtils.getSortedMembers(shape).iterator(); iter.hasNext(); idx++) {
            var member = iter.next();
            var target = model.expectShape(member.getTarget());
            if (CodegenUtils.isStreamingBlob(target)) {
                // Streaming blobs are not deserialized by the builder class.
                continue;
            }

            writer.pushState();
            writer.putContext("memberName", symbolProvider.toMemberName(member));
            writer.write(
                "case $L -> builder.${memberName:L}($C);",
                idx,
                new DeserializerGenerator(writer, member, symbolProvider, model, service, "de", "member")
            );
            writer.popState();
        }
    }

    /**
     *  Generates Builder setter methods for a member shape
     */
    private record SetterGenerator(
        JavaWriter writer, SymbolProvider symbolProvider, Model model, MemberShape memberShape
    ) implements Runnable {

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("memberName", symbolProvider.toMemberName(memberShape));
            writer.putContext("memberSymbol", symbolProvider.toSymbol(memberShape));
            writer.putContext("tracked", CodegenUtils.isRequiredWithNoDefault(memberShape));
            writer.putContext("check", CodegenUtils.requiresSetterNullCheck(symbolProvider, memberShape));
            writer.putContext("schemaName", CodegenUtils.toMemberSchemaName(symbolProvider.toMemberName(memberShape)));
            writer.putContext("objects", Objects.class);

            // If streaming blob then a setter must be added to allow
            // operation to set on builder.
            if (CodegenUtils.isStreamingBlob(model.expectShape(memberShape.getTarget()))) {
                writer.write("""
                    @Override
                    public void setDataStream($T stream) {
                        ${memberName:L}(stream);
                    }
                    """, DataStream.class);
            }

            writer.write(
                """
                    public Builder ${memberName:L}(${memberSymbol:T} ${memberName:L}) {
                        this.${memberName:L} = ${?check}${objects:T}.requireNonNull(${/check}${memberName:L}${?check}, "${memberName:L} cannot be null")${/check};${?tracked}
                        tracker.setMember(${schemaName:L});${/tracked}
                        return this;
                    }
                    """
            );

            writer.popState();
        }
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
                "new $T<>()",
                symbolProvider.toSymbol(listShape).expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS)
            );
            return null;
        }

        @Override
        public Void mapShape(MapShape mapShape) {
            // Note that Maps can _ONLY_ have empty maps as the default,
            // so we do not need to check the default value. See:
            // https://github.com/smithy-lang/smithy/blob/main/designs/defaults-and-model-evolution.md
            writer.write(
                "new $T<>()",
                symbolProvider.toSymbol(mapShape).expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS)
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
            // TODO: Implement
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
