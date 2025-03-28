/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.sections.OperationSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.traits.TitleTrait;
import software.amazon.smithy.utils.CodeInterceptor;

/**
 * Adds the {@code @throws} javadoc tag to operations for all modeled errors the operation might throw.
 */
final class OperationErrorInterceptor implements CodeInterceptor.Appender<JavadocSection, JavaWriter> {

    @Override
    public void append(JavaWriter writer, JavadocSection section) {
        if (section.parent() instanceof OperationSection os) {
            for (var error : os.targetedShape().getErrors()) {
                writer.pushState();
                var errorShape = os.model().expectShape(error);
                var errorSymbol = os.symbolProvider().toSymbol(errorShape);
                var hasTitle = errorShape.hasTrait(TitleTrait.class);
                writer.putContext("hasTitle", hasTitle);
                if (hasTitle) {
                    writer.putContext("title", errorShape.expectTrait(TitleTrait.class).getValue());
                }
                writer.write("@throws $T${?hasTitle} - ${title:L}${/hasTitle}", errorSymbol);
                writer.popState();
            }
        } else {
            throw new IllegalArgumentException("Cannot run operation error interceptor on non-operation section");
        }
    }

    @Override
    public Class<JavadocSection> sectionType() {
        return JavadocSection.class;
    }

    @Override
    public boolean isIntercepted(JavadocSection section) {
        return section.parent() instanceof OperationSection;
    }
}
