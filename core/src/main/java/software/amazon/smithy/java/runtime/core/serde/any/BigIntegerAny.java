/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import java.math.BigInteger;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class BigIntegerAny implements Any {

    private final SdkSchema schema;
    private final BigInteger value;

    BigIntegerAny(BigInteger value, SdkSchema schema) {
        if (!(schema.type() == ShapeType.DOCUMENT || schema.type() == ShapeType.BIG_INTEGER)) {
            throw new SdkSerdeException(
                "Expected bigInteger Any to have a bigInteger or document schema, but found "
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
        return ShapeType.BIG_INTEGER;
    }

    @Override
    public BigInteger asBigInteger() {
        return value;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.writeBigInteger(schema, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BigIntegerAny that = (BigIntegerAny) o;
        return Objects.equals(schema, that.schema) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, value);
    }

    @Override
    public String toString() {
        return "BigIntegerAny{value=" + value + ", schema=" + schema + '}';
    }
}
