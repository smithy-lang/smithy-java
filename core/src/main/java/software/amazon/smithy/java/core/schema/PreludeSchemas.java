/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;

/**
 * {@link Schema} definitions for the Smithy prelude
 */
public final class PreludeSchemas {

    public static final Schema BLOB = Schema.createBlob(ShapeId.from("smithy.api#Blob"));
    public static final Schema BOOLEAN = Schema.createBoolean(ShapeId.from("smithy.api#Boolean"));
    public static final Schema STRING = Schema.createString(ShapeId.from("smithy.api#String"));
    public static final Schema TIMESTAMP = Schema.createTimestamp(ShapeId.from("smithy.api#Timestamp"));
    public static final Schema BYTE = Schema.createByte(ShapeId.from("smithy.api#Byte"));
    public static final Schema SHORT = Schema.createShort(ShapeId.from("smithy.api#Short"));
    public static final Schema INTEGER = Schema.createInteger(ShapeId.from("smithy.api#Integer"));
    public static final Schema LONG = Schema.createLong(ShapeId.from("smithy.api#Long"));
    public static final Schema FLOAT = Schema.createFloat(ShapeId.from("smithy.api#Float"));
    public static final Schema DOUBLE = Schema.createDouble(ShapeId.from("smithy.api#Double"));
    public static final Schema BIG_INTEGER = Schema.createBigInteger(ShapeId.from("smithy.api#BigInteger"));
    public static final Schema BIG_DECIMAL = Schema.createBigDecimal(ShapeId.from("smithy.api#BigDecimal"));
    public static final Schema DOCUMENT = Schema.createDocument(ShapeId.from("smithy.api#Document"));

    // Primitive types
    public static final Schema PRIMITIVE_BOOLEAN = Schema.createBoolean(
            ShapeId.from("smithy.api#PrimitiveBoolean"),
            new DefaultTrait(Node.from(false)));
    public static final Schema PRIMITIVE_BYTE = Schema.createByte(
            ShapeId.from("smithy.api#PrimitiveByte"),
            new DefaultTrait(Node.from((byte) 0)));
    public static final Schema PRIMITIVE_SHORT = Schema.createShort(
            ShapeId.from("smithy.api#PrimitiveShort"),
            new DefaultTrait(Node.from((short) 0)));
    public static final Schema PRIMITIVE_INTEGER = Schema.createInteger(
            ShapeId.from("smithy.api#PrimitiveInteger"),
            new DefaultTrait(Node.from(0)));
    public static final Schema PRIMITIVE_LONG = Schema.createLong(
            ShapeId.from("smithy.api#PrimitiveLong"),
            new DefaultTrait(Node.from(0L)));
    public static final Schema PRIMITIVE_FLOAT = Schema.createFloat(
            ShapeId.from("smithy.api#PrimitiveFloat"),
            new DefaultTrait(Node.from(0f)));
    public static final Schema PRIMITIVE_DOUBLE = Schema.createDouble(
            ShapeId.from("smithy.api#PrimitiveDouble"),
            new DefaultTrait(Node.from(0.0)));

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
