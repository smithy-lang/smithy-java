/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Optional;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.SymbolUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.UniqueItemsTrait;
import software.amazon.smithy.utils.BuilderRef;
import software.amazon.smithy.utils.StringUtils;

/**
 * Generates a static nested {@code Builder} class for a Java class.
 */
public class BuilderGenerator implements Runnable {

    private final JavaWriter writer;
    private final Shape shape;
    private final SymbolProvider symbolProvider;
    private final Model model;

    public BuilderGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model) {
        this.writer = writer;
        this.shape = shape;
        this.symbolProvider = symbolProvider;
        this.model = model;
    }

    @Override
    public void run() {
        writer.write(
            """
                public static Builder builder() {
                    return new Builder();
                }

                /**
                 * Builder for {@link $1T}.
                 */
                public static final class Builder implements $2T<$1T> {
                    ${3C|}

                    private Builder() {}

                    ${4C|}

                    @Override
                    public $1T build() {
                        return new $1T(this);
                    }

                    ${5C|}
                }""",
            symbolProvider.toSymbol(shape),
            SdkShapeBuilder.class,
            (Runnable) this::builderProperties,
            (Runnable) this::builderSetters,
            (Runnable) this::deserializer
        );
    }

    // TODO: Implement deserializer
    private void deserializer() {
        writer.write(
            """
                @Override
                public Builder deserialize($T decoder) {
                    // PLACEHOLDER. Needs implementation
                    return this;
                }""",
            ShapeDeserializer.class
        );
    }

    // Adds builder properties and initializers
    private void builderProperties() {
        for (var member : shape.members()) {
            var memberSymbol = symbolProvider.toSymbol(member);
            if (memberSymbol.getProperty(SymbolProperties.BUILDER_REF_INITIALIZER).isPresent()) {
                writer.write(
                        "private final $1T<$2T> $3L = $1T.$4L;",
                        BuilderRef.class,
                        symbolProvider.toSymbol(member),
                        symbolProvider.toMemberName(member),
                        memberSymbol.expectProperty(SymbolProperties.BUILDER_REF_INITIALIZER, String.class)
                );
            } else if (SymbolUtils.isStreamingBlob(model.expectShape(member.getTarget()))) {
                // Streaming blobs need a custom initializer
                writer.write(
                        "private $1T $2L = $1T.ofEmpty();",
                        DataStream.class,
                        symbolProvider.toMemberName(member)
                );
            } else {
                // TODO: handle defaults
                writer.pushState();
                writer.putContext("isRequired", member.isRequired());
                writer.write(
                    "private ${?isRequired}$1T${/isRequired}${^isRequired}$1B${/isRequired} $2L;",
                    symbolProvider.toSymbol(member),
                    symbolProvider.toMemberName(member)
                );
                writer.popState();
            }
        }
    }

    private void builderSetters() {
        for (var memberShape : shape.members()) {
            memberShape.accept(
                    new SetterVisitor(symbolProvider.toSymbol(memberShape), symbolProvider.toMemberName(memberShape))
            );
        }
    }

    /**
     *  Generates Builder setter methods for a member shape
     */
    private final class SetterVisitor extends ShapeVisitor.Default<Void> {
        private final String memberName;
        private final Symbol memberSymbol;

        private SetterVisitor(Symbol memberSymbol, String memberName) {
            this.memberName = memberName;
            this.memberSymbol = memberSymbol;
        }

        @Override
        protected Void getDefault(Shape shape) {
            writer.write(
                """
                    public Builder $1L($2T $1L) {
                        this.$1L = $1L;
                        return this;
                    }
                    """,
                memberName,
                symbolProvider.toSymbol(shape)
            );
            return null;
        }

        @Override
        public Void blobShape(BlobShape shape) {
            getDefault(shape);

            // If streaming blob then a setter must be added to allow
            // operation to set on builder.
            if (SymbolUtils.isStreamingBlob(shape)) {
                writer.write("""
                    @Override
                    public void setDataStream($T stream) {
                        $L(stream);
                    }
                    """, DataStream.class, memberName);
            }
            return null;
        }

        @Override
        public Void listShape(ListShape shape) {
            writer.pushState();
            writer.putContext("builderRef",
                    memberSymbol.getProperty(SymbolProperties.BUILDER_REF_INITIALIZER).isPresent());
            writer.write(
                """
                    public Builder $1L($2T $1L) {
                        clear$3L();${^builderRef}
                        create$3LIfNotExists();
                        ${/builderRef}this.$1L${?builderRef}.get()${/builderRef}.addAll($1L);
                        return this;
                    }
                    """,
                memberName,
                symbolProvider.toSymbol(shape),
                StringUtils.capitalize(memberName)
            );

            writer.write(
                    """
                        public Builder clear$1L() {
                            if ($2L${?builderRef}.hasValue()${/builderRef}${^builderRef} != null${/builderRef}) {
                                $2L${?builderRef}.get()${/builderRef}.clear();
                            }
                            return this;
                        }
                        """,
                    StringUtils.capitalize(memberName),
                    memberName
            );

            // Set one
            writer.write(
                """
                    public Builder add$1L($2T value) {${^builderRef}
                        create$1LIfNotExists();
                        ${/builderRef}$3L${?builderRef}.get()${/builderRef}.add(value);
                        return this;
                    }
                    """,
                StringUtils.capitalize(memberName),
                symbolProvider.toSymbol(shape.getMember()),
                memberName
            );

            // Remove one
            writer.write(
                """
                    public Builder remove$1L($2T value) {
                        if (this.$3L${?builderRef}.hasValue()${/builderRef}${^builderRef} != null${/builderRef}) {
                            $3L${?builderRef}.get()${/builderRef}.remove(value);
                        }
                        return this;
                    }
                    """,
                StringUtils.capitalize(memberName),
                symbolProvider.toSymbol(shape.getMember()),
                memberName
            );

            if (!memberSymbol.getProperty(SymbolProperties.BUILDER_REF_INITIALIZER).isPresent()) {
                writer.write("""
                        private void create$1LIfNotExists() {
                            if ($2L == null) {
                                $2L = new $3T<>();
                            }
                        }
                        """,
                        StringUtils.capitalize(memberName),
                        memberName,
                        memberSymbol.expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS)
                );
            }
            writer.popState();

            return null;
        }

        @Override
        public Void mapShape(MapShape shape) {
            // Set all
            writer.write(
                """
                    public Builder $1L($2T $1L) {
                        clear$3L();
                        this.$1L.get().putAll($1L);
                        return this;
                    }
                    """,
                memberName,
                symbolProvider.toSymbol(shape),
                StringUtils.capitalize(memberName)
            );

            writer.write(
                    """
                        public Builder clear$1L() {
                            if ($2L.hasValue()) {
                                $2L.get().clear();
                            }
                            return this;
                        }
                        """,
                    StringUtils.capitalize(memberName),
                    memberName
            );

            // Set one
            writer.write(
                """
                    public Builder put$L($T key, $T value) {
                       this.$L.get().put(key, value);
                       return this;
                    }
                    """,
                StringUtils.capitalize(memberName),
                symbolProvider.toSymbol(shape.getKey()),
                symbolProvider.toSymbol(shape.getValue()),
                memberName
            );

            // Remove one
            writer.write(
                """
                    public Builder remove$1L($2T $3L) {
                        if (this.$3L.hasValue()) {
                            this.$3L.get().remove($3L);
                        }
                        return this;
                    }
                    """,
                StringUtils.capitalize(memberName),
                symbolProvider.toSymbol(shape.getKey()),
                memberName
            );
            return null;
        }

        @Override
        public Void memberShape(MemberShape shape) {
            return model.expectShape(shape.getTarget()).accept(this);
        }
    }
}
