/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen;

import java.util.Set;
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
import software.amazon.smithy.java.codegen.client.generators.BddFileGenerator;
import software.amazon.smithy.java.codegen.client.generators.ClientImplementationGenerator;
import software.amazon.smithy.java.codegen.client.generators.ClientInterfaceGenerator;
import software.amazon.smithy.java.codegen.client.waiters.WaiterContainerGenerator;
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
import software.amazon.smithy.java.codegen.server.generators.OperationInterfaceGenerator;
import software.amazon.smithy.java.codegen.server.generators.ServiceGenerator;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyInternalApi;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Unified directed codegen for all modes (client, server, types).
 */
@SmithyUnstableApi
final class DirectedJavaCodegen
        implements DirectedCodegen<CodeGenerationContext, JavaCodegenSettings, JavaCodegenIntegration> {

    private final Set<CodegenMode> modes;
    //Visible For Testing
    @SmithyInternalApi
    CodeGenerationContext context;

    DirectedJavaCodegen(Set<CodegenMode> modes) {
        this.modes = modes;
    }

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<JavaCodegenSettings> directive
    ) {
        return new JavaSymbolProvider(
                directive.model(),
                directive.service(),
                directive.settings().packageNamespace(),
                directive.settings().name(),
                modes);
    }

    @Override
    public CodeGenerationContext createContext(
            CreateContextDirective<JavaCodegenSettings, JavaCodegenIntegration> directive
    ) {
        String pluginName = getPluginName();
        this.context = new CodeGenerationContext(directive, pluginName);
        return context;
    }

    private String getPluginName() {
        if (modes.contains(CodegenMode.CLIENT) && modes.contains(CodegenMode.SERVER)) {
            return "client+server";
        } else if (modes.contains(CodegenMode.CLIENT)) {
            return "client";
        } else if (modes.contains(CodegenMode.SERVER)) {
            return "server";
        } else {
            return "type";
        }
    }

    @Override
    public void generateStructure(GenerateStructureDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (!isSynthetic(directive.shape())) {
            new StructureGenerator<>().accept(directive);
        }
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
    public void generateOperation(GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (isSynthetic(directive.shape())) {
            return;
        }
        if (modes.contains(CodegenMode.SERVER)) {
            new OperationInterfaceGenerator().accept(directive);
        }
        new OperationGenerator().accept(directive);
    }

    @Override
    public void generateService(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        // In TYPES-only mode, generateService is a no-op (the synthetic service has no real service shape)
        if (modes.contains(CodegenMode.TYPES) && !modes.contains(CodegenMode.CLIENT)
                && !modes.contains(CodegenMode.SERVER)) {
            return;
        }

        if (modes.contains(CodegenMode.CLIENT)) {
            new ClientInterfaceGenerator().accept(directive);
            new ClientImplementationGenerator().accept(directive);
        }

        if (modes.contains(CodegenMode.SERVER)) {
            new ServiceGenerator().accept(directive);
        }

        new ApiServiceGenerator().accept(directive);
        new ServiceExceptionGenerator<>().accept(directive);

        if (modes.contains(CodegenMode.CLIENT)) {
            var service = directive.service();
            if (service.hasTrait(ShapeId.from("smithy.rules#endpointBdd"))
                    || service.hasTrait(ShapeId.from("smithy.rules#endpointRuleSet"))) {
                new BddFileGenerator().accept(directive);
            }
        }
    }

    @Override
    public void generateResource(GenerateResourceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new ResourceGenerator().accept(directive);
    }

    @Override
    public void customizeBeforeIntegrations(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new SchemasGenerator().accept(directive);
        new SharedSerdeGenerator().accept(directive);
        new SchemaIndexGenerator().accept(directive);
    }

    @Override
    public void customizeAfterIntegrations(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (modes.contains(CodegenMode.CLIENT)
                && !directive.model().getShapesWithTrait(ShapeId.from("smithy.waiters#waitable")).isEmpty()) {
            new WaiterContainerGenerator().accept(directive.context());
        }
    }

    private static boolean isSynthetic(Shape shape) {
        return shape.getId().getNamespace().equals(SyntheticServiceTransform.SYNTHETIC_NAMESPACE);
    }
}
