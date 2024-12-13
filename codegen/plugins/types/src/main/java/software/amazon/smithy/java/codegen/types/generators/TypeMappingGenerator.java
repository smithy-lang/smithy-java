/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.types.generators;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class TypeMappingGenerator
    implements Consumer<CustomizeDirective<CodeGenerationContext, JavaCodegenSettings>> {
    private static final String PROPERTY_FILE = "META-INF/smithy-java/type-mappings.properties";
    private static final String SYNTHETIC_NAMESPACE = "smithy.synthetic";
    private static final EnumSet<ShapeType> GENERATED_TYPES = EnumSet.of(
        ShapeType.STRUCTURE,
        ShapeType.UNION,
        ShapeType.ENUM,
        ShapeType.INT_ENUM
    );

    @Override
    public void accept(CustomizeDirective<CodeGenerationContext, JavaCodegenSettings> directive) {
        Map<ShapeId, Symbol> symbolMap = new HashMap<>();

        /// Add all types within the synthetic service closure
        Set<Shape> serviceClosure = (new Walker(directive.model())).walkShapes(directive.service());
        for (var shape : serviceClosure) {
            var shapeId = shape.getId();
            // only add mappings for shapes that generate a class
            if (GENERATED_TYPES.contains(shape.getType()) && !SYNTHETIC_NAMESPACE.equals(shapeId.getNamespace())) {
                var symbol = directive.symbolProvider().toSymbol(shape);
                // Skip any external types used in this package
                if (symbol.getProperty(SymbolProperties.EXTERNAL_TYPE).isPresent()) {
                    continue;
                }
                symbolMap.put(shapeId, symbol);
            }
        }

        if (symbolMap.isEmpty()) {
            return;
        }

        directive.context().writerDelegator().useFileWriter(PROPERTY_FILE, writer -> {
            // Add a helpful header
            writer.writeWithNoFormatting("""
                # This file maps Smithy shape ID's to concrete java class implementations
                # WARNING: This file is code generated. Do not modify by hand.
                """);
            for (var entry : symbolMap.entrySet()) {
                writer.write("$L=$L", entry.getKey(), entry.getValue());
            }
        });
    }
}
