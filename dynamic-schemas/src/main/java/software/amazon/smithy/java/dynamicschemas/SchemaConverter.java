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
        // Aggregate shapes are re-entrant and may need to recursively set values in the CHM.
        var result = schemas.get(shape);

        if (result == null) {
            result = createSchema(shape);
            var previous = schemas.putIfAbsent(shape, result);
            if (previous != null) {
                result = previous;
            }
        }

        return result;
    }

    private Schema createSchema(Shape shape) {
        return isRecursive(shape)
                ? getOrCreateRecursiveSchemaBuilder(shape).build()
                : createNonRecursiveSchema(shape);
    }

    private boolean isRecursive(Shape shape) {
        if (shape.getType().getCategory() == ShapeType.Category.SIMPLE) {
            return false;
        } else {
            return isRecursive(new HashSet<>(), shape);
        }
    }

    private boolean isRecursive(Set<ShapeId> visited, Shape shape) {
        if (!visited.add(shape.getId())) {
            return true;
        }

        return switch (shape.getType().getCategory()) {
            case SIMPLE, SERVICE -> false;
            case MEMBER -> isRecursive(visited, model.expectShape(shape.asMemberShape().orElseThrow().getTarget()));
            case AGGREGATE -> {
                for (var member : shape.members()) {
                    if (isRecursive(visited, member)) {
                        yield true;
                    }
                }
                yield false;
            }
        };
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
            case LIST, SET, MAP, STRUCTURE, UNION -> getOrCreateRecursiveSchemaBuilder(shape).build();
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

    private SchemaBuilder getOrCreateRecursiveSchemaBuilder(Shape shape) {
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
                // Recursion happens at this point, and more schemas are added.
                addMembers(shape, builder);
            }
        }

        return builder;
    }

    private void addMembers(Shape shape, SchemaBuilder builder) {
        for (var member : shape.members()) {
            var memberTraits = new Trait[member.getAllTraits().size()];
            member.getAllTraits().values().toArray(memberTraits);
            var targetShape = model.expectShape(member.getTarget());
            // Recursive members stay as builders for now to avoid infinite recursion in recursive schemas.
            if (isRecursive(targetShape)) {
                SchemaBuilder targetBuilder = getOrCreateRecursiveSchemaBuilder(targetShape);
                builder.putMember(member.getMemberName(), targetBuilder, memberTraits);
            } else {
                Schema targetSchema = getSchema(model.expectShape(member.getTarget()));
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
        };
    }
}
