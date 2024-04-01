/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.*;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public class DirectedJavaCodegen implements
        DirectedCodegen<CodeGenerationContext, JavaCodegenSettings, JavaCodegenIntegration> {
    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<JavaCodegenSettings> directive
    ) {
        return new JavaSymbolProvider(
                directive.model(),
                directive.service(),
                directive.settings().packageNamespace()
        );
    }

    @Override
    public CodeGenerationContext createContext(
            CreateContextDirective<JavaCodegenSettings, JavaCodegenIntegration> directive
    ) {
        return new CodeGenerationContext(
                directive.model(),
                directive.settings(),
                directive.symbolProvider(),
                directive.fileManifest(),
                directive.integrations()
        );
    }

    @Override
    public void generateService(
            GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> generateServiceDirective
    ) {
        // TODO
    }

    @Override
    public void generateOperation(GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        // TODO
    }

    @Override
    public void generateStructure(
            GenerateStructureDirective<CodeGenerationContext, JavaCodegenSettings> generateStructureDirective
    ) {
        // TODO
    }

    @Override
    public void generateError(
            GenerateErrorDirective<CodeGenerationContext, JavaCodegenSettings> generateErrorDirective
    ) {
        // TODO
    }

    @Override
    public void generateUnion(
            GenerateUnionDirective<CodeGenerationContext, JavaCodegenSettings> generateUnionDirective
    ) {
        // TODO
    }

    @Override
    public void generateEnumShape(
            GenerateEnumDirective<CodeGenerationContext, JavaCodegenSettings> generateEnumDirective
    ) {
        // TODO
    }

    @Override
    public void generateIntEnumShape(
            GenerateIntEnumDirective<CodeGenerationContext, JavaCodegenSettings> generateIntEnumDirective
    ) {
        // TODO
    }
}
