/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.generators;

import static java.util.function.Predicate.not;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.TopologicalIndex;
import software.amazon.smithy.codegen.core.directed.Directive;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.writer.JavaWriter;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.model.loader.Prelude;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.UnitTypeTrait;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.SmithyInternalApi;

@SmithyInternalApi
public final class SchemaFieldOrder {

    private static final EnumSet<ShapeType> EXCLUDED_TYPES = EnumSet.of(
            ShapeType.SERVICE,
            ShapeType.RESOURCE,
            ShapeType.MEMBER,
            ShapeType.OPERATION,
            ShapeType.ENUM,
            ShapeType.INT_ENUM);

    private final List<List<SchemaField>> partitions;
    private final Map<ShapeId, SchemaField> reverseMapping;
    private final SymbolProvider symbolProvider;

    public SchemaFieldOrder(Directive<?> directive, long partitionThreshold, SymbolProvider symbolProvider) {
        var connectedShapes = new HashSet<>(directive.connectedShapes().values());
        var index = TopologicalIndex.of(directive.model());
        var allShapes = Stream.concat(index.getOrderedShapes().stream(), index.getRecursiveShapes().stream())
                .filter(connectedShapes::contains)
                .filter(s -> !s.hasTrait(SyntheticTrait.class))
                .filter(s -> !EXCLUDED_TYPES.contains(s.getType()))
                .filter(not(Prelude::isPreludeShape))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<List<SchemaField>> computedPartitions = new ArrayList<>();
        int curIndex = 0;
        var curParition = new ArrayList<SchemaField>();
        var curFieldNames = new HashSet<String>();
        computedPartitions.add(curParition);
        int curClassNumber = 0;
        String curClassName = "Schemas";
        for (var shape : allShapes) {
            if (curIndex >= partitionThreshold) {
                curIndex = 0;
                curClassNumber++;
                curClassName = "Schemas" + curClassNumber;
                curFieldNames.clear();
                curParition = new ArrayList<>();
                computedPartitions.add(curParition);
            }
            var shapeFieldName = toSchemaName(shape);
            if (curFieldNames.contains(shapeFieldName)) {
                shapeFieldName = toFullQualifiedSchemaName(shape);
            }
            boolean isShapeRecursive = CodegenUtils.recursiveShape(directive.model(), shape);
            var shapeField = new SchemaField(shape, shapeFieldName, curClassName, isShapeRecursive);
            curParition.add(shapeField);
            curFieldNames.add(shapeFieldName);
            if (isShapeRecursive) {
                curIndex++;
            }
            curIndex++;
        }
        computedPartitions.removeIf(List::isEmpty);
        this.partitions = Collections.unmodifiableList(computedPartitions);
        Map<ShapeId, SchemaField> map = new HashMap<>();
        for (var partition : this.partitions) {
            for (var schemaField : partition) {
                map.put(schemaField.shape.getId(), schemaField);
            }
        }
        this.reverseMapping = map;
        this.symbolProvider = symbolProvider;
    }

    public SchemaField getSchemaField(ShapeId shape) {
        return reverseMapping.get(shape);
    }

    public List<List<SchemaField>> partitions() {
        return partitions;
    }

    public String getSchemaFieldName(Shape shape, JavaWriter writer) {
        SchemaField schemaField = this.getSchemaField(shape.getId());
        if (schemaField == null) {
            return getSchemaType(writer, symbolProvider, shape);
        }
        return writer.format("$L.$L", schemaField.className(), schemaField.fieldName());
    }

    private static String getSchemaType(
            JavaWriter writer,
            SymbolProvider provider,
            Shape shape
    ) {
        if (shape.hasTrait(UnitTypeTrait.class)) {
            return writer.format("$T.SCHEMA", provider.toSymbol(shape));
        } else if (Prelude.isPreludeShape(shape)) {
            return writer.format("$T.$L", PreludeSchemas.class, shape.getType().name());
        } else if (EXCLUDED_TYPES.contains(shape.getType())) {
            // Shapes that generate a class have their schemas as static properties on that class
            return writer.format(
                    "$T.$$SCHEMA",
                    provider.toSymbol(shape));
        }
        return writer.format("Schemas.$L", toSchemaName(shape));
    }

    private static String toSchemaName(Shape shape) {
        return CaseUtils.toSnakeCase(shape.toShapeId().getName()).toUpperCase(Locale.ENGLISH);
    }

    private static String toFullQualifiedSchemaName(Shape shape) {
        return CaseUtils.toSnakeCase(shape.toShapeId().toString().replace(".", "_").replace("#", "_"))
                .toUpperCase(Locale.ENGLISH);
    }

    record SchemaField(Shape shape, String fieldName, String className, boolean isRecursive) {}
}
