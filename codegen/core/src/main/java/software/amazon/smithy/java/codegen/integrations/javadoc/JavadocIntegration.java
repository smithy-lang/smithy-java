/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.javadoc;

import java.util.List;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Adds all built-in Javadoc-generating interceptors.
 *
 * <p>This integration adds all the required documentation interceptors that ensure
 * that methods, classes, and enum variants all have Javadocs added. This integration also
 * adds annotations such as {@code @Deprecated} that serve as documentation.
 */
@SmithyInternalApi
public final class JavadocIntegration implements JavaCodegenIntegration {

    @Override
    public String name() {
        return "javadoc";
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, JavaWriter>> interceptors(
        CodeGenerationContext codegenContext
    ) {
        return List.of(
            new SmithyGeneratedInterceptor(),
            new JavadocInjectorInterceptor(),
            new OperationErrorInterceptor(),
            new BuilderReturnInterceptor(),
            new ExternalDocumentationTraitInterceptor(),
            new SinceTraitInterceptor(),
            new DeprecatedTraitInterceptor(),
            new DocumentationTraitInterceptor(),
            new JavadocFormatterInterceptor()
        );
    }
}
