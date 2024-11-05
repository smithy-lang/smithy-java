/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.server;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.CreateSymbolProviderDirective;
import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.JavaDirectedCodegen;
import software.amazon.smithy.java.codegen.generators.OperationGenerator;
import software.amazon.smithy.java.codegen.server.generators.OperationInterfaceGenerator;
import software.amazon.smithy.java.codegen.server.generators.ServiceGenerator;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public class DirectedJavaServerCodegen implements JavaDirectedCodegen {

    @Override
    public SymbolProvider createSymbolProvider(
        CreateSymbolProviderDirective<JavaCodegenSettings> directive
    ) {
        return new ServiceJavaSymbolProvider(
            directive.model(),
            directive.service(),
            directive.settings().packageNamespace(),
            directive.settings().name()
        );
    }

    @Override
    public void generateService(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new ServiceGenerator().accept(directive);
    }

    @Override
    public void generateOperation(GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new OperationInterfaceGenerator().accept(directive);
        new OperationGenerator().accept(directive);
    }
}
