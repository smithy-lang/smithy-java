/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.SymbolUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.ErrorTrait;

/**
 * Generates properties for a Java class from Smithy shape members
 */
final class PropertyGenerator implements Runnable {

    private final JavaWriter writer;
    private final Shape shape;
    private final SymbolProvider symbolProvider;

    PropertyGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider) {
        this.writer = writer;
        this.shape = shape;
        this.symbolProvider = symbolProvider;
    }

    @Override
    public void run() {
        for (var member : shape.members()) {
            // Error message members do not generate property
            var memberName = symbolProvider.toMemberName(member);
            if (shape.hasTrait(ErrorTrait.class) && memberName.equals("message")) {
                continue;
            }
            writer.pushState();
            writer.putContext("isNullable", SymbolUtils.isNullableMember(member));
            writer.write(
                "private final ${?isNullable}$1B${/isNullable}${^isNullable}$1T${/isNullable} $2L;",
                symbolProvider.toSymbol(member),
                memberName
            );
            writer.popState();
        }
    }
}
