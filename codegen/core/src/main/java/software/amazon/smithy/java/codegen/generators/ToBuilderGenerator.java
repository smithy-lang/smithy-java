/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
final class ToBuilderGenerator implements Runnable {
    private final JavaWriter writer;
    private final StructureShape shape;
    private final SymbolProvider symbolProvider;
    private final Model model;

    public ToBuilderGenerator(JavaWriter writer, StructureShape shape, SymbolProvider symbolProvider, Model model) {
        this.writer = writer;
        this.shape = shape;
        this.symbolProvider = symbolProvider;
        this.model = model;
    }

    @Override
    public void run() {
        writer.write(
            """
                public Builder toBuilder() {
                    var builder =  new Builder();
                    ${C|}
                    return builder;
                }
                """,
            (Runnable) this::copyBuilderProperties
        );
    }

    private void copyBuilderProperties() {
        for (var member : shape.members()) {
            writer.pushState();
            writer.putContext("member", symbolProvider.toMemberName(member));
            writer.write("builder.${member:L}(this.${member:L});");
            writer.popState();
        }
    }
}
