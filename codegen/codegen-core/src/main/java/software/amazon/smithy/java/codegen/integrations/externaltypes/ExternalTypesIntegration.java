/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.integrations.externaltypes;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.SymbolReference;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.JavaCodegenIntegration;
import software.amazon.smithy.java.codegen.JavaCodegenSettings;
import software.amazon.smithy.java.codegen.SymbolProperties;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class ExternalTypesIntegration implements JavaCodegenIntegration {
    private static final String PROPERTY_FILE = "META-INF/smithy-java/type-mappings.properties";
    private static final Map<ShapeId, Symbol> TYPE_MAPPINGS = getErrorMappings();

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
                            .build();;
                    var existing = result.put(shapeId, symbol);
                    if (existing != null) {
                        throw new CodegenException(
                                "Found duplicate mapping for external type: "
                                        + property.getKey() + ". Existing: " + existing
                                        + "Duplicate: " + property.getValue());
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
                if (TYPE_MAPPINGS.containsKey(shape.toShapeId())) {
                    return TYPE_MAPPINGS.get(shape.toShapeId());
                } else if (shape instanceof MemberShape ms && TYPE_MAPPINGS.containsKey(ms.getTarget())) {
                    return TYPE_MAPPINGS.get(ms.getTarget());
                } else if (shape instanceof ListShape ls && TYPE_MAPPINGS.containsKey(ls.getMember().getTarget())) {
                    var targetSymbol = TYPE_MAPPINGS.get(ls.getMember().getTarget());
                    return symbolProvider.toSymbol(shape)
                            .toBuilder()
                            .references(List.of(new SymbolReference(targetSymbol)))
                            .build();
                } else if (shape instanceof MapShape ms && TYPE_MAPPINGS.containsKey(ms.getValue().getTarget())) {
                    var valueSymbol = TYPE_MAPPINGS.get(ms.getValue().getTarget());
                    var keySymbol = symbolProvider.toSymbol(ms.getKey());
                    return symbolProvider.toSymbol(shape)
                            .toBuilder()
                            .references(List.of(new SymbolReference(keySymbol), new SymbolReference(valueSymbol)))
                            .build();
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
