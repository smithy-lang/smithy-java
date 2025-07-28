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
import software.amazon.smithy.java.codegen.generators.ApiServiceGenerator;
import software.amazon.smithy.java.codegen.generators.EnumGenerator;
import software.amazon.smithy.java.codegen.generators.ListGenerator;
import software.amazon.smithy.java.codegen.generators.MapGenerator;
import software.amazon.smithy.java.codegen.generators.OperationGenerator;
import software.amazon.smithy.java.codegen.generators.ResourceGenerator;
import software.amazon.smithy.java.codegen.generators.SchemaIndexGenerator;
import software.amazon.smithy.java.codegen.generators.SchemasGenerator;
import software.amazon.smithy.java.codegen.generators.ServiceExceptionGenerator;
import software.amazon.smithy.java.codegen.generators.SharedSerdeGenerator;
import software.amazon.smithy.java.codegen.generators.StructureGenerator;
import software.amazon.smithy.java.codegen.generators.UnionGenerator;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
final class DirectedJavaClientCodegen
        implements DirectedCodegen<CodeGenerationContext, JavaCodegenSettings, JavaCodegenIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<JavaCodegenSettings> directive
    ) {
        return new ClientJavaSymbolProvider(
                directive.model(),
                directive.service(),
                directive.settings().packageNamespace(),
                directive.settings().name());
    }

    @Override
    public CodeGenerationContext createContext(
            CreateContextDirective<JavaCodegenSettings, JavaCodegenIntegration> directive
    ) {
        return new CodeGenerationContext(
                directive,
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
    public void generateService(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new ClientInterfaceGenerator().accept(directive);
        new ClientImplementationGenerator().accept(directive);

        if (!directive.context().settings().useExternalTypes()) {
            new ApiServiceGenerator().accept(directive);
            new ServiceExceptionGenerator<>().accept(directive);
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
            new SchemasGenerator().accept(directive);
            new SharedSerdeGenerator().accept(directive);
            new SchemaIndexGenerator().accept(directive);
        }
    }
}
