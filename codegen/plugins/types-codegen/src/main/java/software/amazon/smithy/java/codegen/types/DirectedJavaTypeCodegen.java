/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

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
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.JavaSymbolProvider;
import software.amazon.smithy.java.codegen.generators.EnumGenerator;
import software.amazon.smithy.java.codegen.generators.ListGenerator;
import software.amazon.smithy.java.codegen.generators.MapGenerator;
import software.amazon.smithy.java.codegen.generators.SchemaIndexGenerator;
import software.amazon.smithy.java.codegen.generators.SchemasGenerator;
import software.amazon.smithy.java.codegen.generators.SharedSerdeGenerator;
import software.amazon.smithy.java.codegen.generators.StructureGenerator;
import software.amazon.smithy.java.codegen.generators.UnionGenerator;
import software.amazon.smithy.java.codegen.types.generators.TypeMappingGenerator;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
final class DirectedJavaTypeCodegen
        implements DirectedCodegen<CodeGenerationContext, JavaCodegenSettings, JavaCodegenIntegration> {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(DirectedJavaTypeCodegen.class);

    @Override
    public CodeGenerationContext createContext(
            CreateContextDirective<JavaCodegenSettings, JavaCodegenIntegration> directive
    ) {
        return new CodeGenerationContext(directive, "type");
    }

    @Override
    public SymbolProvider createSymbolProvider(CreateSymbolProviderDirective<JavaCodegenSettings> directive) {
        return new JavaSymbolProvider(
                directive.model(),
                directive.service(),
                directive.settings().packageNamespace());
    }

    @Override
    public void generateService(GenerateServiceDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        // Type codegen does not generate a service.
    }

    @Override
    public void generateStructure(GenerateStructureDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (!isSynthetic(directive.shape())) {
            LOGGER.debug("Generating Java Class for structure: {}", directive.shape());
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
    public void customizeBeforeIntegrations(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new SharedSerdeGenerator().accept(directive);
        new SchemasGenerator().accept(directive);
        new SchemaIndexGenerator().accept(directive);
    }

    @Override
    public void customizeAfterIntegrations(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        new TypeMappingGenerator().accept(directive);
    }

    private static boolean isSynthetic(Shape shape) {
        return shape.getId().getNamespace().equals(SyntheticServiceTransform.SYNTHETIC_NAMESPACE);
    }
}
