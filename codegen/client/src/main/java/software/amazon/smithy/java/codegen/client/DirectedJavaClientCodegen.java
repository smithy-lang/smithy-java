/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.*;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.JavaDirectedCodegen;
import software.amazon.smithy.java.codegen.client.generators.ClientImplementationGenerator;
import software.amazon.smithy.java.codegen.client.generators.ClientInterfaceGenerator;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
final class DirectedJavaClientCodegen implements JavaDirectedCodegen {

    @Override
    public SymbolProvider createSymbolProvider(
        CreateSymbolProviderDirective<JavaCodegenSettings> directive
    ) {
        return new ClientJavaSymbolProvider(
            directive.model(),
            directive.service(),
            directive.settings().packageNamespace(),
            directive.settings().name()
        );
    }

    @Override
    public void generateService(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new ClientInterfaceGenerator().accept(directive);
        new ClientImplementationGenerator().accept(directive);
    }
}
