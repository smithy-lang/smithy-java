/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.serde.any;

import software.amazon.smithy.java.runtime.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.shapes.SdkSchema;
import software.amazon.smithy.model.shapes.ShapeType;

final class IntegerAny implements Any {

    private final int value;
    private final SdkSchema schema;

    IntegerAny(int value, SdkSchema schema) {
        this.value = value;
        this.schema = schema;
    }

    @Override
    public SdkSchema getSchema() {
        return schema;
    }

    @Override
    public ShapeType getType() {
        return ShapeType.STRING;
    }

    @Override
    public int asInteger() {
        return value;
    }

    @Override
    public float asFloat() {
        // Allow a widening to float.
        return value;
    }

    @Override
    public double asDouble() {
        // Allow a widening to double.
        return value;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.writeInteger(schema, value);
    }
}
