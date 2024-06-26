/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.Trait;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public record TraitInitializerGenerator(JavaWriter writer, Shape shape, CodeGenerationContext context) implements
    Runnable {

    @Override
    public void run() {
        var traitsToAdd = shape.getAllTraits().keySet().stream().filter(context.runtimeTraits()::contains).toList();
        if (traitsToAdd.isEmpty()) {
            return;
        }

        writer.newLine();
        writer.indent();
        writer.openBlock(".traits(", ")", () -> {
            var iter = traitsToAdd.iterator();
            while (iter.hasNext()) {
                var trait = shape.getAllTraits().get(iter.next());
                getInitializer(trait).accept(writer, trait);
                if (iter.hasNext()) {
                    writer.writeInline(",").newLine();
                }
            }
            writer.newLine();
        });
        writer.dedent();
    }

    @SuppressWarnings("unchecked")
    private <T extends Trait> TraitInitializer<T> getInitializer(T trait) {
        for (var initializer : context.traitInitializers()) {
            if (initializer.traitClass().isInstance(trait)) {
                return (TraitInitializer<T>) initializer;
            }
        }
        throw new IllegalArgumentException("Could not find initializer for " + trait);
    }

}
