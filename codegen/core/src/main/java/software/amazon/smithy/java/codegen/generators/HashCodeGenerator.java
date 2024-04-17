/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.Shape;

final class HashCodeGenerator implements Runnable {

    private final JavaWriter writer;
    private final Shape shape;
    private final SymbolProvider symbolProvider;

    HashCodeGenerator(JavaWriter writer, Shape shape, SymbolProvider symbolProvider) {
        this.writer = writer;
        this.shape = shape;
        this.symbolProvider = symbolProvider;
    }

    @Override
    public void run() {
        writer.write(
            """
                @Override
                public int hashCode() {
                    ${C|}
                }
                """,
            (Runnable) this::generate
        );
    }

    private void generate() {
        List<String> arrayMemberNames = shape.members()
            .stream()
            .filter(member -> CodegenUtils.isJavaArray(symbolProvider.toSymbol(member)))
            .map(symbolProvider::toMemberName)
            .toList();
        List<String> objectMemberNames = shape.members()
            .stream()
            .map(symbolProvider::toMemberName)
            .filter(name -> !arrayMemberNames.contains(name))
            .toList();
        writer.pushState();
        writer.putContext("arr", arrayMemberNames);
        writer.putContext("obj", objectMemberNames);

        if (arrayMemberNames.isEmpty()) {
            writer.write(
                "return $T.hash(${#obj}${value:L}${^key.last}, ${/key.last}${/obj});",
                Objects.class
            );
        } else {
            writer.write(
                """
                    int result = $T.hash(${#obj}${value:L}${^key.last}, ${/key.last}${/obj});
                    result = 31 * result${#arr} + $T.hashCode(${value:L})${/arr};
                    return result;
                    """,
                Objects.class,
                Arrays.class
            );
        }
        writer.popState();
    }
}
