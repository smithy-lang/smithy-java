/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class FloatAny implements Any {

    private final float value;
    private final SdkSchema schema;

    FloatAny(float value, SdkSchema schema) {
        this.value = value;
        this.schema = schema;
    }

    @Override
    public SdkSchema getSchema() {
        return schema;
    }

    @Override
    public ShapeType getType() {
        return ShapeType.FLOAT;
    }

    @Override
    public float asFloat() {
        return value;
    }

    @Override
    public double asDouble() {
        // Allow a widening to double.
        return value;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.writeFloat(schema, value);
    }
}
