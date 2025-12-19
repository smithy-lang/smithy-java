/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.integrations.javadoc;

import java.util.List;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Adds client examples to the generated Javadoc.
 */
@SmithyInternalApi
public class ClientJavadocExamplesIntegration implements JavaCodegenIntegration {

    @Override
    public String name() {
        return "client-javadoc-examples";
    }

    @Override
    public List<String> runBefore() {
        // The DocumentationTrait interceptor uses "prepend", and the finalizing formatter
        // wholly replaces the contents of the JavaDoc section with a formatted version.
        // By running before the "javadoc" plugin, which includes those interceptors,
        // we can be sure that what we write will appear after the docs from the doc trait
        // and will still benefit from formatting.
        return List.of("javadoc");
    }

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, JavaWriter>> interceptors(
            CodeGenerationContext codegenContext
    ) {
        return List.of(new ExamplesTraitInterceptor(codegenContext));
    }

}
