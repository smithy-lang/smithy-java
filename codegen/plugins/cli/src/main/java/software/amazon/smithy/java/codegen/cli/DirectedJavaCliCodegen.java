/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.cli;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.CreateContextDirective;
import software.amazon.smithy.codegen.core.directed.CreateSymbolProviderDirective;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.codegen.core.directed.DirectedCodegen;
import software.amazon.smithy.codegen.core.directed.GenerateEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateErrorDirective;
import software.amazon.smithy.codegen.core.directed.GenerateIntEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateListDirective;
import software.amazon.smithy.codegen.core.directed.GenerateMapDirective;
import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.codegen.core.directed.GenerateResourceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.cli.generators.ServiceCommandGenerator;
import software.amazon.smithy.java.codegen.generators.EnumGenerator;
import software.amazon.smithy.java.codegen.generators.ListGenerator;
import software.amazon.smithy.java.codegen.generators.MapGenerator;
import software.amazon.smithy.java.codegen.generators.OperationGenerator;
import software.amazon.smithy.java.codegen.generators.ResourceGenerator;
import software.amazon.smithy.java.codegen.generators.SharedSchemasGenerator;
import software.amazon.smithy.java.codegen.generators.SharedSerdeGenerator;
import software.amazon.smithy.java.codegen.generators.StructureGenerator;
import software.amazon.smithy.java.codegen.generators.UnionGenerator;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
final class DirectedJavaCliCodegen implements
        DirectedCodegen<CodeGenerationContext, JavaCodegenSettings, JavaCodegenIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<JavaCodegenSettings> directive
    ) {
        return new CliJavaSymbolProvider(
                directive.model(),
                directive.service(),
                directive.settings().packageNamespace(),
                directive.settings().name());
    }

    @Override
    public void generateService(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new ServiceCommandGenerator().accept(directive);
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
                directive.integrations(),
                "client");
    }

    @Override
    public void generateStructure(GenerateStructureDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (!directive.settings().useExternalTypes()) {
            new StructureGenerator<>().accept(directive);
        }
    }

    @Override
    public void generateError(GenerateErrorDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (!directive.settings().useExternalTypes()) {
            new StructureGenerator<>().accept(directive);
        }
    }

    @Override
    public void generateUnion(GenerateUnionDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (!directive.settings().useExternalTypes()) {
            new UnionGenerator().accept(directive);
        }
    }

    @Override
    public void generateList(GenerateListDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (!directive.settings().useExternalTypes()) {
            new ListGenerator().accept(directive);
        }
    }

    @Override
    public void generateMap(GenerateMapDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (!directive.settings().useExternalTypes()) {
            new MapGenerator().accept(directive);
        }
    }

    @Override
    public void generateEnumShape(GenerateEnumDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (!directive.settings().useExternalTypes()) {
            new EnumGenerator<>().accept(directive);
        }
    }

    @Override
    public void generateIntEnumShape(GenerateIntEnumDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (!directive.settings().useExternalTypes()) {
            new EnumGenerator<>().accept(directive);
        }
    }

    @Override
    public void generateOperation(GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (!directive.settings().useExternalTypes()) {
            new OperationGenerator().accept(directive);
        }
    }

    @Override
    public void generateResource(GenerateResourceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (!directive.settings().useExternalTypes()) {
            new ResourceGenerator().accept(directive);
        }
    }

    @Override
    public void customizeBeforeIntegrations(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (!directive.settings().useExternalTypes()) {
            new SharedSchemasGenerator().accept(directive);
            new SharedSerdeGenerator().accept(directive);
        }
    }
}
