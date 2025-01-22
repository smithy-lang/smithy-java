/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.ExternalDocumentationTrait;
import software.amazon.smithy.utils.CodeInterceptor;

/**
 * Adds Javadoc documentation for the {@link ExternalDocumentationTrait} using the {@code @see} Javadoc tag.
 */
final class ExternalDocumentationTraitInterceptor implements CodeInterceptor.Appender<JavadocSection, JavaWriter> {
    @Override
    public void append(JavaWriter writer, JavadocSection section) {
        var trait = section.targetShape().expectTrait(ExternalDocumentationTrait.class);
        writer.pushState();
        writer.putContext("links", trait.getUrls());
        writer.write("${#links}@see <a href=${value:S}>${key:L}</a>${^key.last}\n${/key.last}${/links}");
        writer.popState();
    }

    @Override
    public Class<JavadocSection> sectionType() {
        return JavadocSection.class;
    }

    @Override
    public boolean isIntercepted(JavadocSection section) {
        return section.targetShape() != null && section.targetShape().hasTrait(ExternalDocumentationTrait.class);
    }
}
