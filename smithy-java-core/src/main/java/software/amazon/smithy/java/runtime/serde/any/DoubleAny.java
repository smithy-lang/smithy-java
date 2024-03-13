/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.serde.any;

import software.amazon.smithy.java.runtime.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.shapes.SdkSchema;
import software.amazon.smithy.model.shapes.ShapeType;

final class DoubleAny implements Any {

    private final double value;
    private final SdkSchema schema;

    DoubleAny(double value, SdkSchema schema) {
        this.value = value;
        this.schema = schema;
    }

    @Override
    public SdkSchema getSchema() {
        return schema;
    }

    @Override
    public ShapeType getType() {
        return ShapeType.DOUBLE;
    }

    @Override
    public double asDouble() {
        return value;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.writeDouble(schema, value);
    }
}
