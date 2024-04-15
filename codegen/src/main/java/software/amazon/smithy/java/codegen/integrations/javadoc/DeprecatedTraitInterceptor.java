/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.DeprecatedTrait;
import software.amazon.smithy.utils.CodeInterceptor;

/**
 * Adds Javadoc documentation for the {@link DeprecatedTrait} using the {@code @deprecated} Javadoc tag.
 */
final class DeprecatedTraitInterceptor implements CodeInterceptor.Appender<JavadocSection, JavaWriter> {

    @Override
    public void append(JavaWriter writer, JavadocSection section) {
        var trait = section.shape().expectTrait(DeprecatedTrait.class);
        writer.putContext("since", trait.getSince());
        writer.write("@deprecated ${?since}As of ${since:L}. ${/since}$L", trait.getMessage());
    }

    @Override
    public Class<JavadocSection> sectionType() {
        return JavadocSection.class;
    }

    @Override
    public boolean isIntercepted(JavadocSection section) {
        return section.shape().hasTrait(DeprecatedTrait.class);
    }
}
