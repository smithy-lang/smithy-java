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
import software.amazon.smithy.java.codegen.CodeGenerationContext;
import software.amazon.smithy.java.codegen.CodegenUtils;
import software.amazon.smithy.java.codegen.SymbolProperties;
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

    private static final int FAST_PATH_THRESHOLD = 500;
    //The default JVM limit for a class file is 64KB, we use 50KB as a safe limit
    // because there will be other things in the class too like imports, etc.
    private static final int SCHEMA_FILE_SIZE_THRESHOLD = 50_000;

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

    public SchemaFieldOrder(Directive<?> directive, CodeGenerationContext context) {
        this.symbolProvider = context.symbolProvider();
        var connectedShapes = new HashSet<>(directive.connectedShapes().values());
        var index = TopologicalIndex.of(directive.model());
        var allShapes = Stream.concat(index.getOrderedShapes().stream(), index.getRecursiveShapes().stream())
                .filter(connectedShapes::contains)
                .filter(s -> !s.hasTrait(SyntheticTrait.class))
                .filter(s -> !EXCLUDED_TYPES.contains(s.getType()))
                .filter(not(Prelude::isPreludeShape))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Phase 1: Assign field names and default className "Schemas"
        var allFields = new ArrayList<SchemaField>();
        var usedFieldNames = new HashSet<String>();
        for (var shape : allShapes) {
            var shapeFieldName = toSchemaName(shape);
            if (usedFieldNames.contains(shapeFieldName) || shape.getId().getName().equals(shapeFieldName)) {
                shapeFieldName = toFullQualifiedSchemaName(shape);
            }
            boolean isShapeRecursive = CodegenUtils.recursiveShape(directive.model(), shape);
            boolean isExternal =
                    symbolProvider.toSymbol(shape).getProperty(SymbolProperties.EXTERNAL_TYPE).orElse(false);
            allFields.add(
                    new SchemaField(shape, shapeFieldName, SchemaClassRef.placeholder(), isShapeRecursive, isExternal));
            usedFieldNames.add(shapeFieldName);
        }

        // Build reverseMapping so getSchemaFieldName() works during measurement
        Map<ShapeId, SchemaField> map = new HashMap<>();
        for (var field : allFields) {
            map.put(field.shape().getId(), field);
        }
        this.reverseMapping = map;

        // Phase 2: Partition
        if (allFields.size() < FAST_PATH_THRESHOLD) {
            // Fast path: keep all shapes in a single "Schemas" partition
            this.partitions = allFields.isEmpty()
                    ? Collections.emptyList()
                    : List.of(List.copyOf(allFields));
        } else {
            // Measure actual generated sizes and partition based on content size
            int[] sizes = SchemasGenerator.measureShapeSizes(allFields, directive.model(), context, this);
            this.partitions = computePartitions(allFields, sizes);
        }
    }

    private static List<List<SchemaField>> computePartitions(
            List<SchemaField> allFields,
            int[] sizes
    ) {
        List<List<SchemaField>> result = new ArrayList<>();
        var currentPartition = new ArrayList<SchemaField>();
        result.add(currentPartition);
        int currentSize = 0;
        int classNumber = 0;
        String className = "Schemas";

        for (int i = 0; i < allFields.size(); i++) {
            var field = allFields.get(i);
            int shapeSize = sizes[i];
            if (currentSize + shapeSize > SCHEMA_FILE_SIZE_THRESHOLD && !currentPartition.isEmpty()) {
                classNumber++;
                className = "Schemas" + classNumber;
                currentPartition = new ArrayList<>();
                result.add(currentPartition);
                currentSize = 0;
            }
            field.classRef.update(className);
            currentPartition.add(field);
            currentSize += shapeSize;
        }
        result.removeIf(List::isEmpty);
        return Collections.unmodifiableList(result);
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
        } else if (schemaField.isExternal()) {
            return writer.format("$L.$$SCHEMA", symbolProvider.toSymbol(shape));
        }
        return writer.format("$L.$L", schemaField.classRef().className(), schemaField.fieldName());
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
        throw new IllegalStateException(
                "Shape " + shape.getId() + " is not in schema field order and has no known schema fallback");
    }

    private static String toSchemaName(Shape shape) {
        return CaseUtils.toSnakeCase(shape.toShapeId().getName()).toUpperCase(Locale.ENGLISH);
    }

    private static String toFullQualifiedSchemaName(Shape shape) {
        return CaseUtils.toSnakeCase(shape.toShapeId().toString().replace(".", "_").replace("#", "_"))
                .toUpperCase(Locale.ENGLISH);
    }

    /**
     * Represents a shape's schema field assignment in a Schemas partition class.
     * Uses a class instead of a record so className can be updated during partitioning.
     */
    public record SchemaField(
            Shape shape,
            String fieldName,
            SchemaClassRef classRef,
            boolean isRecursive,
            boolean isExternal) {}

    public static final class SchemaClassRef {
        private String className;
        private boolean isPlaceholder;

        private SchemaClassRef(String className, boolean isPlaceholder) {
            this.className = className;
            this.isPlaceholder = isPlaceholder;
        }

        public static SchemaClassRef placeholder() {
            return new SchemaClassRef("Schemas", true);
        }

        public static SchemaClassRef of(String className) {
            return new SchemaClassRef(className, false);
        }

        public void update(String className) {
            if (!isPlaceholder) {
                throw new IllegalStateException("Trying to update a non-place holder SchemaClassRef. Existing : "
                        + this.className + ", Update Attempted With : " + className);
            }
            this.className = className;
            this.isPlaceholder = false;
        }

        public boolean isPlaceholder() {
            return isPlaceholder;
        }

        public String className() {
            return className;
        }

    }
}
