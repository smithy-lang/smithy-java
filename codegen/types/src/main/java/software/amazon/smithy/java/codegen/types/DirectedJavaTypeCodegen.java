/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types;

import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.directed.CreateSymbolProviderDirective;
import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.JavaDirectedCodegen;
import software.amazon.smithy.java.codegen.JavaSymbolProvider;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
record DirectedJavaTypeCodegen(boolean generateOperations) implements JavaDirectedCodegen {

    @Override
    public SymbolProvider createSymbolProvider(CreateSymbolProviderDirective<JavaCodegenSettings> directive) {
        return new JavaSymbolProvider(
            directive.model(),
            directive.service(),
            directive.settings().packageNamespace()
        );
    }

    @Override
    public void generateStructure(GenerateStructureDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (!isSynthetic(directive.shape())) {
            JavaDirectedCodegen.super.generateStructure(directive);
        }
    }

    @Override
    public void generateOperation(GenerateOperationDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        if (generateOperations && !isSynthetic(directive.shape())) {
            JavaDirectedCodegen.super.generateOperation(directive);
        }
    }

    private static boolean isSynthetic(Shape shape) {
        return shape.getId().getNamespace().equals(SyntheticServiceTransform.SYNTHETIC_NAMESPACE);
    }
}
