/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.*;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.client.generators.ClientImplementationGenerator;
import software.amazon.smithy.java.codegen.client.generators.ClientInterfaceGenerator;
import software.amazon.smithy.java.codegen.generators.EnumGenerator;
import software.amazon.smithy.java.codegen.generators.ListGenerator;
import software.amazon.smithy.java.codegen.generators.MapGenerator;
import software.amazon.smithy.java.codegen.generators.OperationGenerator;
import software.amazon.smithy.java.codegen.generators.SharedSchemasGenerator;
import software.amazon.smithy.java.codegen.generators.SharedSerdeGenerator;
import software.amazon.smithy.java.codegen.generators.StructureGenerator;
import software.amazon.smithy.java.codegen.generators.UnionGenerator;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
final class DirectedJavaClientCodegen implements
    DirectedCodegen<CodeGenerationContext, JavaCodegenSettings, JavaCodegenIntegration> {

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
    public void generateService(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new ClientInterfaceGenerator().accept(directive);
        new ClientImplementationGenerator().accept(directive);
    }

    @Override
    public void generateOperation(GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new OperationGenerator().accept(directive);
    }

    @Override
    public void generateStructure(GenerateStructureDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new StructureGenerator<>().accept(directive);

    }

    @Override
    public void generateError(GenerateErrorDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new StructureGenerator<>().accept(directive);
    }

    @Override
    public void generateUnion(GenerateUnionDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new UnionGenerator().accept(directive);
    }

    @Override
    public void generateList(GenerateListDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new ListGenerator().accept(directive);
    }

    @Override
    public void generateMap(GenerateMapDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new MapGenerator().accept(directive);
    }

    @Override
    public void generateEnumShape(GenerateEnumDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new EnumGenerator<>().accept(directive);
    }

    @Override
    public void generateIntEnumShape(GenerateIntEnumDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new EnumGenerator<>().accept(directive);
    }

    @Override
    public void customizeBeforeIntegrations(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new SharedSchemasGenerator().accept(directive);
        new SharedSerdeGenerator().accept(directive);
    }
}
