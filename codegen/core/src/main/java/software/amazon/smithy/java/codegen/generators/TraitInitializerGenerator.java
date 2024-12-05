/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.Map;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
record TraitInitializerGenerator(JavaWriter writer, Shape shape, CodeGenerationContext context) implements
    Runnable {

    @Override
    public void run() {
        var traitsToAdd = shape.getAllTraits()
            .entrySet()
            .stream()
            .filter(entry -> !CodeGenerationContext.BUILD_ONLY_TRAITS.contains(entry.getKey()))
            .filter(entry -> !entry.getKey().getNamespace().startsWith("smithy.test"))
            .filter(entry -> !entry.getKey().getNamespace().startsWith("smithy.protocoltests"))
            .map(Map.Entry::getValue)
            .filter(trait -> !trait.isSynthetic())
            .toList();
        if (traitsToAdd.isEmpty()) {
            return;
        }
        writer.write(",");
        writer.indent().indent();
        var iter = traitsToAdd.iterator();
        while (iter.hasNext()) {
            var trait = iter.next();
            writer.pushState();
            context.getInitializer(trait).accept(writer, trait);
            if (iter.hasNext()) {
                writer.writeInline(",\n");
            }
            writer.popState();
        }
        writer.dedent().dedent();
    }
}
