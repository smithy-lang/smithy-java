/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.externaltypes;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class ExternalTypesIntegration implements JavaCodegenIntegration {
    private static final String PROPERTY_FILE = "META-INF/smithy-java/type-mappings.properties";
    private static final Map<ShapeId, Symbol> ERROR_MAPPINGS = getErrorMappings();

    private static Map<ShapeId, Symbol> getErrorMappings() {
        try {
            Map<ShapeId, Symbol> result = new HashMap<>();
            var classLoader = ExternalTypesIntegration.class.getClassLoader();
            var mappings = classLoader.getResources(PROPERTY_FILE);
            while (mappings.hasMoreElements()) {
                var properties = new Properties();
                try (InputStream is = mappings.nextElement().openStream()) {
                    properties.load(is);
                }
                for (var property : properties.entrySet()) {
                    var shapeId = ShapeId.from((String) property.getKey());
                    var shapeClass = Class.forName((String) property.getValue());
                    var symbol = CodegenUtils.fromClass(shapeClass)
                        .toBuilder()
                        .putProperty(SymbolProperties.EXTERNAL_TYPE, true)
                        .build();
                    ;
                    var existing = result.put(shapeId, symbol);
                    if (existing != null) {
                        throw new CodegenException(
                            "Found duplicate mapping for external type: "
                                + property.getKey() + ". Existing: " + existing
                                + "Duplicate: " + property.getValue()
                        );
                    }
                }
            }
            return result;
        } catch (IOException exc) {
            throw new UncheckedIOException("Error while loading external type mappings", exc);
        } catch (ClassNotFoundException e) {
            throw new CodegenException("Could not find class for mapped external type.");
        }
    }

    @Override
    public String name() {
        return "external-types";
    }

    @Override
    public byte priority() {
        return -1;
    }

    @Override
    public SymbolProvider decorateSymbolProvider(
        Model model,
        JavaCodegenSettings settings,
        SymbolProvider symbolProvider
    ) {
        return new SymbolProvider() {
            @Override
            public Symbol toSymbol(Shape shape) {
                if (ERROR_MAPPINGS.containsKey(shape.toShapeId())) {
                    return ERROR_MAPPINGS.get(shape.toShapeId());
                } else if (shape instanceof MemberShape ms && ERROR_MAPPINGS.containsKey(ms.getTarget())) {
                    return ERROR_MAPPINGS.get(ms.getTarget());
                }
                return symbolProvider.toSymbol(shape);
            }

            @Override
            public String toMemberName(MemberShape shape) {
                return symbolProvider.toMemberName(shape);
            }
        };
    }
}
