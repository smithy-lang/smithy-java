/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class LongAny implements Any {

    private final SdkSchema schema;
    private final long value;

    LongAny(long value, SdkSchema schema) {
        this.value = value;
        this.schema = schema;
    }

    @Override
    public SdkSchema getSchema() {
        return schema;
    }

    @Override
    public ShapeType getType() {
        return ShapeType.LONG;
    }

    @Override
    public long asLong() {
        return value;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.writeLong(schema, value);
    }
}
