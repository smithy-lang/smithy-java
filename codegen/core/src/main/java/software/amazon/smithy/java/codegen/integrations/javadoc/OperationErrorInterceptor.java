/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.sections.OperationSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.CodeInterceptor;

/**
 * TODO: Docs
 */
public class OperationErrorInterceptor implements CodeInterceptor.Appender<JavadocSection, JavaWriter> {

    @Override
    public void append(JavaWriter writer, JavadocSection section) {
        var operation = section.shape().asOperationShape().orElseThrow();
        if (section.parent() instanceof OperationSection os) {
            for (var error : operation.getErrors()) {
                writer.pushState();
                var errorShape = os.model().expectShape(error);
                var errorSymbol = os.symbolProvider().toSymbol(errorShape);
                writer.write("@throws $T", errorSymbol);
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
        if (section.parent() instanceof OperationSection) {
            return section.shape().isOperationShape();
        }
        return false;
    }
}
