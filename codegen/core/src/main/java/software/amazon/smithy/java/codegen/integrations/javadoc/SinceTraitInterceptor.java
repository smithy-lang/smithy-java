/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.SinceTrait;
import software.amazon.smithy.utils.CodeInterceptor;

/**
 * Adds Javadoc documentation for the {@link SinceTrait} using the {@code @since} Javadoc tag.
 */
final class SinceTraitInterceptor implements CodeInterceptor.Appender<JavadocSection, JavaWriter> {
    @Override
    public void append(JavaWriter writer, JavadocSection section) {
        var trait = section.targetShape().expectTrait(SinceTrait.class);
        writer.write("@since $L", trait.getValue());
    }

    @Override
    public Class<JavadocSection> sectionType() {
        return JavadocSection.class;
    }

    @Override
    public boolean isIntercepted(JavadocSection section) {
        return section.targetShape() != null && section.targetShape().hasTrait(SinceTrait.class);
    }
}
