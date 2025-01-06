/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.shapes.Shape;

record TraitInitializerGenerator(JavaWriter writer, Shape shape, CodeGenerationContext context) implements
        Runnable {

    @Override
    public void run() {
        var traitsToAdd = shape.getAllTraits().keySet().stream().filter(context.runtimeTraits()::contains).toList();
        if (traitsToAdd.isEmpty()) {
            return;
        }
        writer.write(",");
        writer.indent().indent();
        var iter = traitsToAdd.iterator();
        while (iter.hasNext()) {
            var trait = shape.getAllTraits().get(iter.next());
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
