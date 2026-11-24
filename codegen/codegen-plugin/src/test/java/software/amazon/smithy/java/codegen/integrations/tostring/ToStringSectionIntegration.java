/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.tostring;

import java.util.List;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.sections.ToStringSection;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Test integration that replaces the generated {@code toString()} through {@link ToStringSection}.
 *
 * <p>This integration is discovered via {@code ServiceLoader} for every codegen run in this module,
 * so it only acts on shapes in the marker namespace to avoid perturbing other tests.
 */
public final class ToStringSectionIntegration implements JavaCodegenIntegration {

    static final String MARKER_NAMESPACE = "smithy.java.codegen.tostring";

    @Override
    public String name() {
        return "to-string-section-test";
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, JavaWriter>> interceptors(
            CodeGenerationContext context
    ) {
        return List.of(new CodeInterceptor<ToStringSection, JavaWriter>() {
            @Override
            public Class<ToStringSection> sectionType() {
                return ToStringSection.class;
            }

            @Override
            public boolean isIntercepted(ToStringSection section) {
                return section.targetedShape().getId().getNamespace().equals(MARKER_NAMESPACE);
            }

            @Override
            public void write(JavaWriter writer, String previousText, ToStringSection section) {
                writer.write("""
                        @Override
                        public String toString() {
                            return $S;
                        }
                        """, "custom:" + section.targetedShape().getId());
            }
        });
    }
}
