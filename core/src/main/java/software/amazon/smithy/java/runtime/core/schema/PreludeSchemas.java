/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.UnitTypeTrait;

/**
 * {@link SdkSchema} definitions for the Smithy prelude
 */
public final class PreludeSchemas {
    public static final SdkSchema BLOB = SdkSchema.builder().type(ShapeType.BLOB).id("smithy.api#String").build();
    public static final SdkSchema BOOLEAN = SdkSchema.builder()
        .type(ShapeType.BOOLEAN)
        .id("smithy.api#Boolean")
        .build();
    public static final SdkSchema STRING = SdkSchema.builder().type(ShapeType.STRING).id("smithy.api#String").build();
    public static final SdkSchema TIMESTAMP = SdkSchema.builder()
        .type(ShapeType.TIMESTAMP)
        .id("smithy.api#Timestamp")
        .build();
    public static final SdkSchema BYTE = SdkSchema.builder().type(ShapeType.BYTE).id("smithy.api#Byte").build();
    public static final SdkSchema SHORT = SdkSchema.builder().type(ShapeType.SHORT).id("smithy.api#Short").build();
    public static final SdkSchema INTEGER = SdkSchema.builder()
        .type(ShapeType.INTEGER)
        .id("smithy.api#Integer")
        .build();
    public static final SdkSchema LONG = SdkSchema.builder().type(ShapeType.LONG).id("smithy.api#Long").build();
    public static final SdkSchema FLOAT = SdkSchema.builder().type(ShapeType.FLOAT).id("smithy.api#Float").build();
    public static final SdkSchema DOUBLE = SdkSchema.builder().type(ShapeType.DOUBLE).id("smithy.api#Double").build();
    public static final SdkSchema BIG_INTEGER = SdkSchema.builder()
        .type(ShapeType.BIG_INTEGER)
        .id("smithy.api#BigInteger")
        .build();
    public static final SdkSchema BIG_DECIMAL = SdkSchema.builder()
        .type(ShapeType.BIG_DECIMAL)
        .id("smithy.api#BigDecimal")
        .build();

    // Primitive types
    public static final SdkSchema PRIMITIVE_BOOLEAN = SdkSchema.builder()
        .type(ShapeType.BOOLEAN)
        .id("smithy.api#PrimitiveBoolean")
        .traits(new DefaultTrait(Node.from(false)))
        .build();
    public static final SdkSchema PRIMITIVE_BYTE = SdkSchema.builder()
        .type(ShapeType.BYTE)
        .id("smithy.api#PrimitiveByte")
        .traits(new DefaultTrait(Node.from(0)))
        .build();
    public static final SdkSchema PRIMITIVE_SHORT = SdkSchema.builder()
        .type(ShapeType.SHORT)
        .id("smithy.api#PrimitiveShort")
        .traits(new DefaultTrait(Node.from(0)))
        .build();
    public static final SdkSchema PRIMITIVE_INTEGER = SdkSchema.builder()
        .type(ShapeType.INTEGER)
        .id("smithy.api#PrimitiveInteger")
        .traits(new DefaultTrait(Node.from(0)))
        .build();
    public static final SdkSchema PRIMITIVE_LONG = SdkSchema.builder()
        .type(ShapeType.LONG)
        .id("smithy.api#PrimitiveLong")
        .traits(new DefaultTrait(Node.from(0)))
        .build();
    public static final SdkSchema PRIMITIVE_FLOAT = SdkSchema.builder()
        .type(ShapeType.FLOAT)
        .id("smithy.api#PrimitiveFloat")
        .traits(new DefaultTrait(Node.from(0)))
        .build();
    public static final SdkSchema PRIMITIVE_DOUBLE = SdkSchema.builder()
        .type(ShapeType.DOUBLE)
        .id("smithy.api#PrimitiveDouble")
        .traits(new DefaultTrait(Node.from(0)))
        .build();
    public static final SdkSchema UNIT = SdkSchema.builder()
        .type(ShapeType.STRUCTURE)
        .id("smithy.api#Unit")
        .traits(new UnitTypeTrait())
        .build();

    private PreludeSchemas() {
        // Class should not be instantiated.
    }
}
