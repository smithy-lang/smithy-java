/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;


import java.util.Arrays;
import java.util.Objects;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.Shape;

/**
 * Generates an {@code equals} implementation for structure shapes.
 */
record EqualsGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider) implements Runnable {

    @Override
    public void run() {
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
                return ${2C|};""",
            symbolProvider.toSymbol(shape),
            (Runnable) this::writePropertyEqualityChecks
        );
    }

    private void writePropertyEqualityChecks() {
        var iter = shape.members().iterator();
        while (iter.hasNext()) {
            var member = iter.next();
            var memberSymbol = symbolProvider.toSymbol(member);
            writer.pushState();
            writer.putContext("memberName", symbolProvider.toMemberName(member));
            // Use `==` instead of `equals` for unboxed primitives
            if (memberSymbol.expectProperty(SymbolProperties.IS_PRIMITIVE) && !CodegenUtils.isNullableMember(member)) {
                writer.writeInline("${memberName:L} == that.${memberName:L}");
            } else {
                Class<?> comparator = CodegenUtils.isJavaArray(memberSymbol) ? Arrays.class : Objects.class;
                writer.writeInline("$T.equals(${memberName:L}, that.${memberName:L})", comparator);
            }
            if (iter.hasNext()) {
                writer.writeInlineWithNoFormatting(writer.getNewline() + "&& ");
            }
            writer.popState();
        }
    }
}
