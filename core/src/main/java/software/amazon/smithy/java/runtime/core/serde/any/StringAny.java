/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class StringAny implements Any {

    private final String value;
    private final SdkSchema schema;

    StringAny(String value, SdkSchema schema) {
        this.schema = schema;
        this.value = value;
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
    public String asString() {
        return value;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.writeString(SCHEMA, value);
    }
}
