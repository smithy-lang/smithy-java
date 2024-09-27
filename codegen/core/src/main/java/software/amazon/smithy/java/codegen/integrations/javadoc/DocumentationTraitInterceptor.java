/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.utils.CodeInterceptor;

/**
 * Adds Javadoc documentation for the {@link DocumentationTrait}.
 *
 * <p>The documentation trait contents are added as the contents of the Javadoc.
 */
final class DocumentationTraitInterceptor implements CodeInterceptor<JavadocSection, JavaWriter> {

    @Override
    public void write(JavaWriter writer, String previousText, JavadocSection section) {
        writer.writeWithNoFormatting(section.shape().expectTrait(DocumentationTrait.class).getValue());

        if (!previousText.isEmpty()) {
            // Add spacing if tags have been added to the javadoc
            writer.newLine();
            writer.writeInlineWithNoFormatting(previousText);
        }
    }

    @Override
    public Class<JavadocSection> sectionType() {
        return JavadocSection.class;
    }

    @Override
    public boolean isIntercepted(JavadocSection section) {
        return section.shape().hasTrait(DocumentationTrait.class);
    }
}
