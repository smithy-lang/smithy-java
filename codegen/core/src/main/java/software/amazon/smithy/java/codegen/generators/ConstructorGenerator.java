/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.ErrorTrait;

/**
 * Generates a constructor for a Java class.
 *
 * <p>The constructor expects to take only one argument, a static {@code Builder} class
 * Use the {@link BuilderGenerator} class to generate this static builder.
 */
record ConstructorGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider, Model model) implements
    Runnable {

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
                    writer.write("this.$L = builder.$L;", memberName, memberName);
                }
            }
        );
    }
}
