/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.dynamicschemas;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SchemaBuilder;
import software.amazon.smithy.java.core.schema.SchemaIndex;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.Trait;

/**
 * Converts {@link Shape}s to {@link Schema}s.
 */
public final class SchemaConverter {

    private final Model model;
    private final ConcurrentMap<Shape, Schema> schemas = new ConcurrentHashMap<>();
    private final Map<Shape, SchemaBuilder> recursiveBuilders = Collections.synchronizedMap(new HashMap<>());

    /**
     * @param model Model used when converting shapes to schemas.
     */
    public SchemaConverter(Model model) {
        this.model = model;
    }

    /**
     * Create a schema-guided document shape builder.
     *
     * @param schema Schema used to inform deserialization.
     * @param serviceId The shape ID of the service that is used to provide default namespaces for relative shape IDs.
     * @return the created shape builder.
     */
    public static ShapeBuilder<StructDocument> createDocumentBuilder(Schema schema, ShapeId serviceId) {
        return new SchemaGuidedDocumentBuilder(schema, serviceId);
    }

    /**
     * Create a schema-guided document shape builder.
     *
     * <p>Document discriminators that use a relative ID will assume the same namespace as the given {@code schema}.
     *
     * @param schema Schema used to inform deserialization.
     * @return the created shape builder.
     */
    public static ShapeBuilder<StructDocument> createDocumentBuilder(Schema schema) {
        return createDocumentBuilder(schema, schema.id());
    }

    /**
     * Get the converted {@link Schema} of a Smithy {@link Shape}.
     *
     * @param shape Shape to get the converted schema from.
     * @return the converted schema.
     */
    public Schema getSchema(Shape shape) {
        var result = schemas.get(shape);
        if (result != null) {
            return result;
        }
        return getSchema(shape, new HashSet<>());
    }

    private Schema getSchema(Shape shape, Set<Shape> building) {
        var result = schemas.get(shape);

        if (result == null) {
            result = createSchema(shape, building);
            var previous = schemas.putIfAbsent(shape, result);
            if (previous != null) {
                result = previous;
            }
        }

        return result;
    }

    private Schema createSchema(Shape shape, Set<Shape> building) {
        if (shape.getType().getCategory() == ShapeType.Category.AGGREGATE) {
            return getOrCreateRecursiveSchemaBuilder(shape, building).build();
        }
        return createNonRecursiveSchema(shape);
    }

    private Schema createNonRecursiveSchema(Shape shape) {
        return switch (shape.getType()) {
            case BLOB -> Schema.createBlob(shape.getId(), convertTraits(shape));
            case BOOLEAN -> Schema.createBoolean(shape.getId(), convertTraits(shape));
            case STRING -> Schema.createString(shape.getId(), convertTraits(shape));
            case TIMESTAMP -> Schema.createTimestamp(shape.getId(), convertTraits(shape));
            case BYTE -> Schema.createByte(shape.getId(), convertTraits(shape));
            case SHORT -> Schema.createShort(shape.getId(), convertTraits(shape));
            case INTEGER -> Schema.createInteger(shape.getId(), convertTraits(shape));
            case LONG -> Schema.createLong(shape.getId(), convertTraits(shape));
            case FLOAT -> Schema.createFloat(shape.getId(), convertTraits(shape));
            case DOCUMENT -> Schema.createDocument(shape.getId(), convertTraits(shape));
            case DOUBLE -> Schema.createDouble(shape.getId(), convertTraits(shape));
            case BIG_DECIMAL -> Schema.createBigDecimal(shape.getId(), convertTraits(shape));
            case BIG_INTEGER -> Schema.createBigInteger(shape.getId(), convertTraits(shape));
            case ENUM -> Schema.createEnum(
                    shape.getId(),
                    new HashSet<>(shape.asEnumShape().orElseThrow().getEnumValues().values()),
                    convertTraits(shape));
            case INT_ENUM -> Schema.createIntEnum(
                    shape.getId(),
                    new HashSet<>(shape.asIntEnumShape().orElseThrow().getEnumValues().values()),
                    convertTraits(shape));
            case OPERATION -> Schema.createOperation(shape.getId(), convertTraits(shape));
            case SERVICE -> Schema.createService(shape.getId(), convertTraits(shape));
            default -> throw new UnsupportedOperationException("Unexpected shape: " + shape);
        };
    }

    private static Trait[] convertTraits(Shape shape) {
        var traits = new Trait[shape.getAllTraits().size()];
        shape.getAllTraits().values().toArray(traits);
        return traits;
    }

    private SchemaBuilder getOrCreateRecursiveSchemaBuilder(Shape shape, Set<Shape> building) {
        SchemaBuilder builder;
        builder = recursiveBuilders.get(shape);

        if (builder == null) {
            builder = switch (shape.getType()) {
                case LIST, SET -> Schema.listBuilder(shape.getId(), convertTraits(shape));
                case MAP -> Schema.mapBuilder(shape.getId(), convertTraits(shape));
                case STRUCTURE -> Schema.structureBuilder(shape.getId(), convertTraits(shape));
                case UNION -> Schema.unionBuilder(shape.getId(), convertTraits(shape));
                default -> throw new UnsupportedOperationException("Expected aggregate shape: " + shape);
            };
            SchemaBuilder previous = recursiveBuilders.putIfAbsent(shape, builder);
            if (previous != null) {
                builder = previous;
            } else {
                building.add(shape);
                addMembers(shape, builder, building);
                building.remove(shape);
            }
        }

        return builder;
    }

    private void addMembers(Shape shape, SchemaBuilder builder, Set<Shape> building) {
        for (var member : shape.members()) {
            var memberTraits = new Trait[member.getAllTraits().size()];
            member.getAllTraits().values().toArray(memberTraits);
            var targetShape = model.expectShape(member.getTarget());
            if (building.contains(targetShape)) {
                // Target is currently being built — cycle detected, use deferred builder
                SchemaBuilder targetBuilder = getOrCreateRecursiveSchemaBuilder(targetShape, building);
                builder.putMember(member.getMemberName(), targetBuilder, memberTraits);
            } else {
                Schema targetSchema = getSchema(targetShape, building);
                builder.putMember(member.getMemberName(), targetSchema, memberTraits);
            }
        }
    }

    public SchemaIndex getSchemaIndex() {
        return new SchemaIndex() {
            @Override
            public Schema getSchema(ShapeId id) {
                return SchemaConverter.this.getSchema(model.expectShape(id));
            }

            @Override
            public void visit(Consumer<Schema> visitor) {
                SchemaConverter.this.schemas.values().forEach(visitor);
            }
        };
    }
}
