/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types.generators;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.framework.traits.ImplicitErrorsTrait;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class ImplicitErrorMappingGenerator
    implements Consumer<CustomizeDirective<CodeGenerationContext, JavaCodegenSettings>> {
    private static final String PROPERTY_FILE = "META-INF/smithy-java/implicit-errors.properties";
    private static final String FRAMEWORK_NAMESPACE = "smithy.framework";

    @Override
    public void accept(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        var shapesWithImplicitErrors = directive.model().getShapesWithTrait(ImplicitErrorsTrait.class);

        // Collect all implicit errors in the model
        Set<ShapeId> implicitErrors = new HashSet<>();
        for (Shape shape : shapesWithImplicitErrors) {
            var implicitErrorTrait = shape.expectTrait(ImplicitErrorsTrait.class);
            implicitErrors.addAll(implicitErrorTrait.getValues());
        }

        if (implicitErrors.isEmpty()) {
            return;
        }

        directive.context().writerDelegator().useFileWriter(PROPERTY_FILE, writer -> {
            // Add a helpful header
            writer.writeWithNoFormatting("""
                # This file maps implicit error Smithy IDs to concrete java class implementations
                # WARNING: This file is code generated. Do not modify by hand.
                """);
            var errorShapes = directive.model().getShapesWithTrait(ErrorTrait.class);
            for (var errorShape : errorShapes) {
                var errorId = errorShape.getId();
                // Add a mapping for any implicit or framework errors
                if (implicitErrors.contains(errorId) || errorId.getNamespace().equals(FRAMEWORK_NAMESPACE)) {
                    var errorSymbol = directive.symbolProvider().toSymbol(errorShape);
                    writer.write("$L=$L", errorId, errorSymbol.getFullName());
                }
            }
        });
    }
}
