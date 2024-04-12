/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.core;

import java.util.HashSet;
import java.util.Set;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.TraitInitializer;
import software.amazon.smithy.java.codegen.sections.TraitSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.CodeInterceptor;

/**
 * Injects Trait definitions using trait initializers.
 *
 * @param context Code generation context.
 */
record TraitInitializerInterceptor(CodeGenerationContext context) implements CodeInterceptor<TraitSection, JavaWriter> {
    private static final System.Logger LOGGER = System.getLogger(TraitInitializerInterceptor.class.getName());

    @Override
    public Class<TraitSection> sectionType() {
        return TraitSection.class;
    }

    @Override
    public void write(JavaWriter writer, String previousText, TraitSection section) {
        if (section.shape().getAllTraits().isEmpty()) {
            // Do not print anything if there are no traits on the shape.
            return;
        }

        // Collect all initializers
        Set<TraitInitializer> initializers = new HashSet<>();
        for (var trait : section.shape().getAllTraits().values()) {
            var initializer = context.initializer(trait.getClass());
            if (initializer == null) {
                LOGGER.log(System.Logger.Level.DEBUG, "No trait initializer found for %s. Skipping...", trait);
                continue;
            }
            initializers.add(initializer);
        }

        // Do not print anything if there are no initializers
        if (initializers.isEmpty()) {
            return;
        }

        // Add the method call for traits now we know valid initializers exist
        writer.newLine();
        writer.indent();
        writer.openBlock(".traits(", ")", () -> {
            // Loop through initializers to inject trait definitions
            var iter = initializers.iterator();
            while (iter.hasNext()) {
                iter.next().accept(writer, section);
                if (iter.hasNext()) {
                    writer.writeInlineWithNoFormatting(", \n");
                }
            }
            writer.newLine();
        });
        writer.dedent();
    }
}
