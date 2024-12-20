/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.serde.TypeRegistry;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public record TypeRegistryGenerator(JavaWriter writer, List<Symbol> errorSymbols) implements Runnable {
    @Override
    public void run() {
        writer.pushState();
        writer.putContext("typeRegistry", TypeRegistry.class);
        if (errorSymbols.isEmpty()) {
            writer.write("private static final ${typeRegistry:T} TYPE_REGISTRY = ${typeRegistry:T}.empty();");
            writer.popState();
            return;
        }
        writer.write("private static final ${typeRegistry:T} TYPE_REGISTRY = ${typeRegistry:T}.builder()");
        writer.indent();
        for (var errorSymbol : errorSymbols) {
            writer.write(".putType($1T.$$ID, $1T.class, $1T::builder)", errorSymbol);
        }
        writer.writeWithNoFormatting(".build();");
        writer.dedent();
        writer.popState();
    }
}
