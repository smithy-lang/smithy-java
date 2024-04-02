/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import java.time.Instant;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class TimestampAny implements Any {

    private final SdkSchema schema;
    private final Instant value;

    TimestampAny(Instant value, SdkSchema schema) {
        if (!(schema.type() == ShapeType.DOCUMENT || schema.type() == ShapeType.TIMESTAMP)) {
            throw new SdkSerdeException(
                "Expected timestamp Any to have a timestamp or document schema, but found "
                    + schema
            );
        }

        this.value = value;
        this.schema = schema;
    }

    @Override
    public SdkSchema schema() {
        return schema;
    }

    @Override
    public ShapeType type() {
        return ShapeType.TIMESTAMP;
    }

    @Override
    public Instant asTimestamp() {
        return value;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.writeTimestamp(schema, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TimestampAny that = (TimestampAny) o;
        return Objects.equals(schema, that.schema) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, value);
    }

    @Override
    public String toString() {
        return "TimestampAny{value=" + value + ", schema=" + schema + '}';
    }
}
