/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.waiters;

import software.amazon.smithy.java.codegen.sections.JavadocSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.CodeInterceptor;

/**
 * Adds Javadoc documentation for waiter methods.
 */
final class WaiterDocumentationInterceptor implements CodeInterceptor.Appender<JavadocSection, JavaWriter> {

    @Override
    public void append(JavaWriter writer, JavadocSection section) {
        if (section.parent() instanceof WaiterSection ws) {
            ws.waiter().getDocumentation().ifPresent(writer::writeWithNoFormatting);
            if (ws.waiter().isDeprecated()) {
                writer.write("@deprecated Waiter is deprecated");
            }
        }
    }

    @Override
    public Class<JavadocSection> sectionType() {
        return JavadocSection.class;
    }

    @Override
    public boolean isIntercepted(JavadocSection section) {
        return section.parent() instanceof WaiterSection;
    }
}
