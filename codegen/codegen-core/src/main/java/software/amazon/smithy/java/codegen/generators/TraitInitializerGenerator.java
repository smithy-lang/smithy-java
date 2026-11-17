/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.Trait;

record TraitInitializerGenerator(
        JavaWriter writer,
        Shape shape,
        CodeGenerationContext context,
        List<Trait> extraTraits) implements Runnable {

    TraitInitializerGenerator(JavaWriter writer, Shape shape, CodeGenerationContext context) {
        this(writer, shape, context, List.of());
    }

    @Override
    public void run() {
        var traitsToAdd = new ArrayList<Trait>();

        for (var entry : shape.getAllTraits().entrySet()) {
            if (context.runtimeTraits().contains(entry.getKey())) {
                traitsToAdd.add(entry.getValue());
            }
        }

        traitsToAdd.addAll(extraTraits);
        if (traitsToAdd.isEmpty()) {
            return;
        }

        // Keep traits on the shape or member line to reduce LineNumberTable entries in generated Schemas classes.
        for (var trait : traitsToAdd) {
            writer.pushState();
            writer.writeInline(", ");
            context.getInitializer(trait).accept(writer, trait);
            writer.popState();
        }
    }
}
