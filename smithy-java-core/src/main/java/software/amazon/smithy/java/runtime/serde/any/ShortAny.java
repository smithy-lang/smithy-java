/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.serde.any;

import software.amazon.smithy.java.runtime.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.shapes.SdkSchema;
import software.amazon.smithy.model.shapes.ShapeType;

final class ShortAny implements Any {

    private final SdkSchema schema;
    private final short value;

    ShortAny(short value, SdkSchema schema) {
        this.value = value;
        this.schema = schema;
    }

    @Override
    public SdkSchema getSchema() {
        return schema;
    }

    @Override
    public ShapeType getType() {
        return ShapeType.SHORT;
    }

    @Override
    public short asShort() {
        return value;
    }

    @Override
    public int asInteger() {
        return value;
    }

    @Override
    public float asFloat() {
        return value;
    }

    @Override
    public double asDouble() {
        return value;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.writeShort(schema, value);
    }
}
