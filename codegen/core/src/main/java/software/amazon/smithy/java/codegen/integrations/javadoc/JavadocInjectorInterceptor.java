/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import software.amazon.smithy.java.codegen.sections.ApplyDocumentation;
import software.amazon.smithy.java.codegen.sections.DocumentedSection;
import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.DeprecatedTrait;
import software.amazon.smithy.model.traits.UnstableTrait;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Adds a javadoc section to classes, getters, and enum variants.
 *
 * <p>This interceptor will also add any relevant documentation annotation to classes, getters, or enum variants.
 */
final class JavadocInjectorInterceptor implements CodeInterceptor.Prepender<CodeSection, JavaWriter> {

    @Override
    public Class<CodeSection> sectionType() {
        return CodeSection.class;
    }

    @Override
    public boolean isIntercepted(CodeSection section) {
        // Javadocs are generated for Classes, on member Getters, and on enum variants, operations, and builder setters.
        if (section instanceof DocumentedSection c) {
            return c.applyDocumentation() == ApplyDocumentation.DOCUMENT;
        } else {
            return false;
        }
    }

    @Override
    public void prepend(JavaWriter writer, CodeSection section) {
        if (section instanceof DocumentedSection ds) {
            var shape = ds.targetedShape();
            writer.injectSection(new JavadocSection(shape, section));
            if (shape == null) {
                return;
            }

            if (shape.hasTrait(UnstableTrait.class)) {
                writer.write("@$T", SmithyUnstableApi.class);
            }

            if (shape.hasTrait(DeprecatedTrait.class)) {
                var deprecated = shape.expectTrait(DeprecatedTrait.class);
                writer.pushState();
                writer.putContext("since", deprecated.getSince().orElse(""));
                writer.write("@$T${?since}(since = ${since:S})${/since}", Deprecated.class);
                writer.popState();
            }
        }
    }
}
