/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class IntegerAny implements Any {

    private final int value;
    private final SdkSchema schema;

    IntegerAny(int value, SdkSchema schema) {
        if (!(schema.type() == ShapeType.DOCUMENT || schema.type() == ShapeType.INTEGER)) {
            throw new SdkSerdeException(
                "Expected integer Any to have an integer or document schema, but found "
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
        return ShapeType.INTEGER;
    }

    @Override
    public int asInteger() {
        return value;
    }

    @Override
    public long asLong() {
        // Allow a widening to long.
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        IntegerAny that = (IntegerAny) o;
        return value == that.value && Objects.equals(schema, that.schema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, schema);
    }

    @Override
    public String toString() {
        return "IntegerAny{value=" + value + ", schema=" + schema + '}';
    }
}
