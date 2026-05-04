/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.DocumentationTrait;
import software.amazon.smithy.utils.CodeInterceptor;

/**
 * Adds Javadoc documentation for the {@link DocumentationTrait}.
 *
 * <p>The documentation trait contents are converted from Markdown/HTML to
 * Javadoc-compatible HTML with line wrapping adjusted for the nesting level.
 */
final class DocumentationTraitInterceptor implements CodeInterceptor<JavadocSection, JavaWriter> {
    // Top-level class javadoc: " * " prefix = 3 chars at indent 0, so 3 total prefix
    private static final int CLASS_MAX_WIDTH = 117;
    // Member javadoc (getters, setters, etc.): "     * " prefix = 7 chars at indent 4, so 7 total prefix
    private static final int MEMBER_MAX_WIDTH = 113;

    @Override
    public void write(JavaWriter writer, String previousText, JavadocSection section) {
        var markdown = section.targetedShape().expectTrait(DocumentationTrait.class).getValue();
        int maxWidth = section.parent() instanceof ClassSection ? CLASS_MAX_WIDTH : MEMBER_MAX_WIDTH;
        writer.writeWithNoFormatting(MarkdownToJavadoc.convert(markdown, maxWidth));

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
        return section.targetedShape() != null && section.targetedShape().hasTrait(DocumentationTrait.class);
    }
}
