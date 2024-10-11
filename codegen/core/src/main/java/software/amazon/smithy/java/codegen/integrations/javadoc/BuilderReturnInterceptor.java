/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import software.amazon.smithy.java.codegen.sections.BuilderSetterSection;
import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.CodeInterceptor;

/**
 * Adds the {@code @return} tag to builder setters with a static value.
 */
final class BuilderReturnInterceptor implements CodeInterceptor.Appender<JavadocSection, JavaWriter> {

    @Override
    public void append(JavaWriter writer, JavadocSection section) {
        writer.writeWithNoFormatting("@return this builder.");
    }

    @Override
    public Class<JavadocSection> sectionType() {
        return JavadocSection.class;
    }

    @Override
    public boolean isIntercepted(JavadocSection section) {
        return section.parent() instanceof BuilderSetterSection;
    }
}
