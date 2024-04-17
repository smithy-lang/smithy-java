/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Collection;
import java.util.Collections;
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
            if (SymbolUtils.isStreamingBlob(model.expectShape(member.getTarget()))) {
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
            writer.putContext(
                "collectionImpl",
                memberSymbol.expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS, Class.class)
            );
            writer.putContext("memberName", memberName);
            writer.putContext("targetSymbol", symbolProvider.toSymbol(shape.getMember()));

            // Collection Replacement
            writer.write(
                """
                    public Builder ${memberName:L}($T<${targetSymbol:T}> ${memberName:L}) {
                        this.${memberName:L} = ${memberName:L} != null ? new ${collectionImpl:T}<>(${memberName:L}) : null;
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
                        }
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
                        this.${memberName:L}.add(${memberName:L});
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
                        $T.addAll(this.${memberName:L}, ${memberName:L});
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
            writer.putContext("memberName", memberName);
            writer.putContext("symbol", symbolProvider.toSymbol(shape));
            writer.putContext(
                "collectionImpl",
                memberSymbol.expectProperty(SymbolProperties.COLLECTION_IMPLEMENTATION_CLASS, Class.class)
            );
            writer.putContext("keySymbol", symbolProvider.toSymbol(shape.getKey()));
            writer.putContext("valueSymbol", symbolProvider.toSymbol(shape.getValue()));

            // Replace Map
            writer.write(
                """
                    public Builder ${memberName:L}(${symbol:T} ${memberName:L}) {
                        this.${memberName:L} = ${memberName:L} != null ? new ${collectionImpl:T}<>(${memberName:L}) : null;
                        return this;
                    }
                    """
            );

            // Add all items
            writer.write(
                """
                    public Builder putAll${memberName:U}(${symbol:T} ${memberName:L}) {
                        if (this.${memberName:L} == null) {
                            this.${memberName:L} = new ${collectionImpl:T}<>(${memberName:L});
                        } else {
                            this.${memberName:L}.putAll(${memberName:L});
                        }
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
                       this.${memberName:L}.put(key, value);
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
}
