/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.nullableannotation;

import java.util.List;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Adds all built-in nullable annotation interceptors.
 *
 * <p>This integration adds all the required interceptors that ensure
 * that non null shapes have their nullable java annotations
 */
@SmithyInternalApi
public final class NullableAnnotationIntegration implements JavaCodegenIntegration {
    private String nullableAnnotation;

    @Override
    public String name() {
        return "nullableannotation-integration";
    }

    @Override
    public void configure(JavaCodegenSettings settings, ObjectNode integrationSettings) {
        this.nullableAnnotation = integrationSettings
                .getStringMemberOrDefault("nullableAnnotation", "");
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, JavaWriter>> interceptors(
            CodeGenerationContext codegenContext
    ) {
        return List.of(
                new NullableAnnotationInjectorInterceptor(nullableAnnotation)
        );
    }
}
