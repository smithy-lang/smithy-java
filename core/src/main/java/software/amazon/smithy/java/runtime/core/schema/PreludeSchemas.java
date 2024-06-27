/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 * {@link Schema} definitions for the Smithy prelude
 */
public final class PreludeSchemas {
    public static final Schema BLOB = Schema.builder().type(ShapeType.BLOB).id("smithy.api#Blob").build();
    public static final Schema BOOLEAN = Schema.builder()
        .type(ShapeType.BOOLEAN)
        .id("smithy.api#Boolean")
        .build();
    public static final Schema STRING = Schema.builder().type(ShapeType.STRING).id("smithy.api#String").build();
    public static final Schema TIMESTAMP = Schema.builder()
        .type(ShapeType.TIMESTAMP)
        .id("smithy.api#Timestamp")
        .build();
    public static final Schema BYTE = Schema.builder().type(ShapeType.BYTE).id("smithy.api#Byte").build();
    public static final Schema SHORT = Schema.builder().type(ShapeType.SHORT).id("smithy.api#Short").build();
    public static final Schema INTEGER = Schema.builder()
        .type(ShapeType.INTEGER)
        .id("smithy.api#Integer")
        .build();
    public static final Schema LONG = Schema.builder().type(ShapeType.LONG).id("smithy.api#Long").build();
    public static final Schema FLOAT = Schema.builder().type(ShapeType.FLOAT).id("smithy.api#Float").build();
    public static final Schema DOUBLE = Schema.builder().type(ShapeType.DOUBLE).id("smithy.api#Double").build();
    public static final Schema BIG_INTEGER = Schema.builder()
        .type(ShapeType.BIG_INTEGER)
        .id("smithy.api#BigInteger")
        .build();
    public static final Schema BIG_DECIMAL = Schema.builder()
        .type(ShapeType.BIG_DECIMAL)
        .id("smithy.api#BigDecimal")
        .build();
    public static final Schema DOCUMENT = Schema.builder()
        .type(ShapeType.DOCUMENT)
        .id("smithy.api#Document")
        .build();

    // Primitive types
    public static final Schema PRIMITIVE_BOOLEAN = Schema.builder()
        .type(ShapeType.BOOLEAN)
        .id("smithy.api#PrimitiveBoolean")
        .traits(new DefaultTrait(Node.from(false)))
        .build();
    public static final Schema PRIMITIVE_BYTE = Schema.builder()
        .type(ShapeType.BYTE)
        .id("smithy.api#PrimitiveByte")
        .traits(new DefaultTrait(Node.from(0)))
        .build();
    public static final Schema PRIMITIVE_SHORT = Schema.builder()
        .type(ShapeType.SHORT)
        .id("smithy.api#PrimitiveShort")
        .traits(new DefaultTrait(Node.from(0)))
        .build();
    public static final Schema PRIMITIVE_INTEGER = Schema.builder()
        .type(ShapeType.INTEGER)
        .id("smithy.api#PrimitiveInteger")
        .traits(new DefaultTrait(Node.from(0)))
        .build();
    public static final Schema PRIMITIVE_LONG = Schema.builder()
        .type(ShapeType.LONG)
        .id("smithy.api#PrimitiveLong")
        .traits(new DefaultTrait(Node.from(0)))
        .build();
    public static final Schema PRIMITIVE_FLOAT = Schema.builder()
        .type(ShapeType.FLOAT)
        .id("smithy.api#PrimitiveFloat")
        .traits(new DefaultTrait(Node.from(0)))
        .build();
    public static final Schema PRIMITIVE_DOUBLE = Schema.builder()
        .type(ShapeType.DOUBLE)
        .id("smithy.api#PrimitiveDouble")
        .traits(new DefaultTrait(Node.from(0)))
        .build();

    private PreludeSchemas() {
        // Class should not be instantiated.
    }

    /**
     * Returns the most appropriate prelude schema based on the given type.
     *
     * <p>Numeric and boolean types return the nullable value
     * (e.g., {@link #INTEGER} and not {@link #PRIMITIVE_INTEGER}).
     *
     * <p>Types with no corresponding prelude schema (e.g., LIST, STRUCTURE, UNION), are returned as a
     * {@link #DOCUMENT} schema.
     *
     * @param type Type to compute a schema from.
     * @return the schema type.
     */
    public static Schema getSchemaForType(ShapeType type) {
        return switch (type) {
            case BOOLEAN -> PreludeSchemas.BOOLEAN;
            case BYTE -> PreludeSchemas.BYTE;
            case SHORT -> PreludeSchemas.SHORT;
            case INTEGER, INT_ENUM -> PreludeSchemas.INTEGER;
            case LONG -> PreludeSchemas.LONG;
            case FLOAT -> PreludeSchemas.FLOAT;
            case DOUBLE -> PreludeSchemas.DOUBLE;
            case BIG_INTEGER -> PreludeSchemas.BIG_INTEGER;
            case BIG_DECIMAL -> PreludeSchemas.BIG_DECIMAL;
            case STRING, ENUM -> PreludeSchemas.STRING;
            case BLOB -> PreludeSchemas.BLOB;
            case TIMESTAMP -> PreludeSchemas.TIMESTAMP;
            default -> PreludeSchemas.DOCUMENT;
        };
    }
}
