/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import software.amazon.smithy.java.codegen.sections.ClassSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.SmithyGenerated;

/**
 * Adds the {@link software.amazon.smithy.utils.SmithyGenerated} annotation to all generated classes.
 */
final class SmithyGeneratedInterceptor implements CodeInterceptor.Prepender<ClassSection, JavaWriter> {

    @Override
    public void prepend(JavaWriter writer, ClassSection section) {
        writer.write("@$T", SmithyGenerated.class);
    }

    @Override
    public Class<ClassSection> sectionType() {
        return ClassSection.class;
    }
}
