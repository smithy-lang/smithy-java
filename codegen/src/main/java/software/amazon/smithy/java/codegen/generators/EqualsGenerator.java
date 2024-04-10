/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;


import java.util.Arrays;
import java.util.Objects;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.SymbolUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.ErrorTrait;

final class EqualsGenerator implements Runnable {
    private final JavaWriter writer;
    private final Shape shape;
    private final SymbolProvider symbolProvider;

    EqualsGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider) {
        this.writer = writer;
        this.shape = shape;
        this.symbolProvider = symbolProvider;
    }

    @Override
    public void run() {
        if (shape.hasTrait(ErrorTrait.class)) {
            // Do not implement equals for error classes.
            return;
        }

        writer.write(
            """
                @Override
                public boolean equals($T other) {
                    if (other == this) {
                        return true;
                    }
                    ${C|}
                }
                """,
            Object.class,
            (Runnable) this::writeMemberEquals
        );
    }

    private void writeMemberEquals() {
        // If there are no properties to compare, and they are the same non-null
        // type then classes should be considered equal, and we can simplify the return
        if (shape.members().isEmpty()) {
            writer.writeInlineWithNoFormatting("return other != null && getClass() == other.getClass();");
            return;
        }
        writer.write(
            """
                if (other == null || getClass() != other.getClass()) {
                    return false;
                }
                $1T that = ($1T) other;
                return ${2C|};
                """,
            symbolProvider.toSymbol(shape),
            (Runnable) this::writePropertyEqualityChecks
        );
    }

    private void writePropertyEqualityChecks() {
        var iter = shape.members().iterator();
        while (iter.hasNext()) {
            var member = iter.next();
            Class<?> comparator = SymbolUtils.isJavaArray(symbolProvider.toSymbol(member))
                ? Arrays.class
                : Objects.class;
            writer.writeInline("$1T.equals($2L, that.$2L)", comparator, symbolProvider.toMemberName(member));
            if (iter.hasNext()) {
                writer.writeInlineWithNoFormatting(writer.getNewline() + "&& ");
            }
        }
    }
}
