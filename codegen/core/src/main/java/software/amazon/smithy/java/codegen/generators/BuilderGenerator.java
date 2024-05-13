/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
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
            (Runnable) this::generateMemberSwitchCases
        );
        writer.popState();
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

    // Adds builder properties and initializers
    private void builderProperties() {
        var defaultVisitor = new DefaultInitializerGenerator(writer, model, symbolProvider);
        for (var member : shape.members()) {
            if (CodegenUtils.isStreamingBlob(model.expectShape(member.getTarget()))) {
                // Streaming blobs need a custom initializer
                writer.write(
                    "private $1T $2L = $1T.ofEmpty();",
                    DataStream.class,
                    symbolProvider.toMemberName(member)
                );
                continue;
            }
            writer.pushState();
            writer.putContext("isNullable", CodegenUtils.isNullableMember(member));
            writer.putContext("default", member.hasNonNullDefault());
            defaultVisitor.member = member;
            writer.write(
                "private ${^isNullable}$1T${/isNullable}${?isNullable}$1B${/isNullable} $2L${?default} = ${3C|}${/default};",
                symbolProvider.toSymbol(member),
                symbolProvider.toMemberName(member),
                defaultVisitor
            );
            writer.popState();
        }
    }

    private void builderSetters() {
        for (var member : shape.members()) {
            new SetterVisitor(writer, symbolProvider, model, member).run();
        }
    }

    /**
     *  Generates Builder setter methods for a member shape
     */
    private static final class SetterVisitor extends ShapeVisitor.Default<Void> implements Runnable {
        private final JavaWriter writer;
        private final SymbolProvider symbolProvider;
        private final Model model;
        private final MemberShape memberShape;

        private SetterVisitor(JavaWriter writer, SymbolProvider symbolProvider, Model model, MemberShape memberShape) {
            this.writer = writer;
            this.symbolProvider = symbolProvider;
            this.model = model;
            this.memberShape = memberShape;
        }

        @Override
        public void run() {
            writer.pushState();
            writer.putContext("memberName", symbolProvider.toMemberName(memberShape));
            writer.putContext("memberSymbol", symbolProvider.toSymbol(memberShape));
            writer.putContext("tracked", CodegenUtils.isRequiredWithNoDefault(memberShape));
            writer.putContext("schemaName", CodegenUtils.toMemberSchemaName(symbolProvider.toMemberName(memberShape)));
            memberShape.accept(this);
            writer.popState();
        }

        @Override
        protected Void getDefault(Shape shape) {
            writer.write(
                """
                    public Builder ${memberName:L}(${memberSymbol:T} ${memberName:L}) {
                        this.${memberName:L} = ${memberName:L};
                        ${?tracked}tracker.setMember(${schemaName:L});
                        ${/tracked}return this;
                    }
                    """
            );
            return null;
        }

        @Override
        public Void blobShape(BlobShape shape) {
            getDefault(shape);

            // If streaming blob then a setter must be added to allow
            // operation to set on builder.
            if (CodegenUtils.isStreamingBlob(shape)) {
                writer.write("""
                    @Override
                    public void setDataStream($T stream) {
                        ${memberName:L}(stream);
                    }
                    """, DataStream.class);
            }
            return null;
        }

        @Override
        public Void listShape(ListShape shape) {
            writer.pushState();
            writer.putContext(
                "collectionImpl",
                symbolProvider.toSymbol(memberShape).expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS)
            );
            writer.putContext("targetSymbol", symbolProvider.toSymbol(shape.getMember()));

            // Collection Replacement
            writer.write(
                """
                    public Builder ${memberName:L}($T<${targetSymbol:T}> ${memberName:L}) {
                        this.${memberName:L} = ${memberName:L} != null ? new ${collectionImpl:T}<>(${memberName:L}) : null;${?tracked}
                        tracker.setMember(${schemaName:L});
                        ${/tracked}
                        return this;
                    }
                    """,
                Collection.class
            );

            // Bulk Add
            writer.write(
                """
                    public Builder addAll${memberName:U}($T<${targetSymbol:T}> ${memberName:L}) {
                        if (this.${memberName:L} == null) {
                            this.${memberName:L} = new ${collectionImpl:T}<>(${memberName:L});
                        } else {
                            this.${memberName:L}.addAll(${memberName:L});
                        }${?tracked}
                        tracker.setMember(${schemaName:L});
                        ${/tracked}
                        return this;
                    }
                    """,
                Collection.class
            );

            // Set one
            writer.write(
                """
                    public Builder ${memberName:L}(${targetSymbol:T} ${memberName:L}) {
                        if (this.${memberName:L} == null) {
                            this.${memberName:L} = new ${collectionImpl:T}<>();
                        }
                        this.${memberName:L}.add(${memberName:L});${?tracked}
                        tracker.setMember(${schemaName:L});
                        ${/tracked}
                        return this;
                    }
                    """
            );

            // Set with varargs
            writer.write(
                """
                    public Builder ${memberName:L}(${targetSymbol:T}... ${memberName:L}) {
                        if (this.${memberName:L} == null) {
                            this.${memberName:L} = new ${collectionImpl:T}<>();
                        }
                        $T.addAll(this.${memberName:L}, ${memberName:L});${?tracked}
                        tracker.setMember(${schemaName:L});
                        ${/tracked}
                        return this;
                    }
                    """,
                Collections.class
            );

            writer.popState();

            return null;
        }

        @Override
        public Void mapShape(MapShape shape) {
            writer.pushState();
            writer.putContext(
                "collectionImpl",
                symbolProvider.toSymbol(memberShape).expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS)
            );
            writer.putContext("keySymbol", symbolProvider.toSymbol(shape.getKey()));
            writer.putContext("valueSymbol", symbolProvider.toSymbol(shape.getValue()));

            // Replace Map
            writer.write(
                """
                    public Builder ${memberName:L}(${memberSymbol:T} ${memberName:L}) {
                        this.${memberName:L} = ${memberName:L} != null ? new ${collectionImpl:T}<>(${memberName:L}) : null;${?tracked}
                        tracker.setMember(${schemaName:L});
                        ${/tracked}
                        return this;
                    }
                    """
            );

            // Add all items
            writer.write(
                """
                    public Builder putAll${memberName:U}(${memberSymbol:T} ${memberName:L}) {
                        if (this.${memberName:L} == null) {
                            this.${memberName:L} = new ${collectionImpl:T}<>(${memberName:L});
                        } else {
                            this.${memberName:L}.putAll(${memberName:L});
                        }${?tracked}
                        tracker.setMember(${schemaName:L});
                        ${/tracked}
                        return this;
                    }
                    """
            );

            // Set one
            writer.write(
                """
                    public Builder put${memberName:U}(${keySymbol:T} key, ${valueSymbol:T} value) {
                       if (this.${memberName:L} == null) {
                           this.${memberName:L} = new ${collectionImpl:T}<>();
                       }
                       this.${memberName:L}.put(key, value);${?tracked}
                       tracker.setMember(${schemaName:L});
                       ${/tracked}
                       return this;
                    }
                    """
            );
            writer.popState();
            return null;
        }

        @Override
        public Void memberShape(MemberShape shape) {
            return model.expectShape(shape.getTarget()).accept(this);
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
            if (member.hasTrait(TimestampFormatTrait.class)) {
                switch (member.expectTrait(TimestampFormatTrait.class).getFormat()) {
                    case EPOCH_SECONDS:
                        writer.writeInline(
                            "$T.ofEpochSecond($LL)",
                            Instant.class,
                            defaultValue.expectNumberNode().getValue().longValue()
                        );
                        return null;
                    case HTTP_DATE:
                        writer.writeInline(
                            "$T.from($T.RFC_1123_DATE_TIME.parse($S))",
                            Instant.class,
                            DateTimeFormatter.class,
                            defaultValue.expectStringNode().getValue()
                        );
                        return null;
                    default:
                        // Fall through on default
                        break;
                }
            }
            writer.write("$T.parse($S)", Instant.class, defaultValue.expectStringNode().getValue());
            return null;
        }
    }
}
