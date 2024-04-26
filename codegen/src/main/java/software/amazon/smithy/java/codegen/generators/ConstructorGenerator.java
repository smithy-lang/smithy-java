/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Collections;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.SymbolUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.utils.SmithyBuilder;

/**
 * Generates a constructor for a Java class.
 *
 * <p>The constructor expects to take only one argument, a static {@code Builder} class
 * Use the {@link BuilderGenerator} class to generate this static builder.
 */
final class ConstructorGenerator implements Runnable {

    private final JavaWriter writer;
    private final Shape shape;
    private final SymbolProvider symbolProvider;
    private final Model model;

    ConstructorGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model) {
        this.writer = writer;
        this.shape = shape;
        this.symbolProvider = symbolProvider;
        this.model = model;
    }

    @Override
    public void run() {
        writer.openBlock(
            "private $T(Builder builder) {",
            "}",
            symbolProvider.toSymbol(shape),
            () -> {
                if (shape.hasTrait(ErrorTrait.class)) {
                    writer.write("super(ID, builder.message);");
                }

                for (var member : shape.members()) {
                    var memberName = symbolProvider.toMemberName(member);
                    // Special case for error builders. Message is passed to
                    // the super constructor. No initializer is created.
                    // TODO: should this have a validator?
                    if (shape.hasTrait(ErrorTrait.class) && memberName.equals("message")) {
                        continue;
                    }
                    writeMemberInitializer(member, memberName);
                }
            }
        );
    }

    private void writeMemberInitializer(MemberShape member, String memberName) {
        if (SymbolUtils.isNullableMember(member) || SymbolUtils.targetsCollection(model, member)) {
            writer.write("this.$L = $L;", memberName, getBuilderValue(member, memberName));
        } else {
            writer.write(
                "this.$1L = $2T.requiredState($1S, $3L);",
                memberName,
                SmithyBuilder.class,
                getBuilderValue(member, memberName)
            );
        }
    }

    private String getBuilderValue(MemberShape member, String memberName) {
        // If the member requires a builderRef we need to copy that builder ref value rather than use it directly.
        var memberSymbol = symbolProvider.toSymbol(member);
        if (memberSymbol.getProperty(SymbolProperties.COLLECTION_COPY_METHOD).isEmpty()) {
            return writer.format("builder.$L", memberName);
        }
        if (SymbolUtils.isNullableMember(member)) {
            return writer.format(
                "builder.$1L != null ? $2T.$3L(builder.$1L) : null",
                memberName,
                Collections.class,
                memberSymbol.expectProperty(SymbolProperties.COLLECTION_COPY_METHOD)
            );
        }
        return writer.format(
            "$T.$L(builder.$L)",
            Collections.class,
            memberSymbol.expectProperty(SymbolProperties.COLLECTION_COPY_METHOD),
            memberName
        );
    }
}
