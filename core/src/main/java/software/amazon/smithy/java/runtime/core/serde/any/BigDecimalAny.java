/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import java.math.BigDecimal;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class BigDecimalAny implements Any {

    private final SdkSchema schema;
    private final BigDecimal value;

    BigDecimalAny(BigDecimal value, SdkSchema schema) {
        if (!(schema.type() == ShapeType.DOCUMENT || schema.type() == ShapeType.BIG_DECIMAL)) {
            throw new SdkSerdeException(
                "Expected bigDecimal Any to have a bigDecimal or document schema, but found "
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
        return ShapeType.BIG_DECIMAL;
    }

    @Override
    public BigDecimal asBigDecimal() {
        return value;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.writeBigDecimal(schema, value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BigDecimalAny that = (BigDecimalAny) o;
        return Objects.equals(schema, that.schema) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, value);
    }

    @Override
    public String toString() {
        return "BigDecimalAny{value=" + value + ", schema=" + schema + '}';
    }
}
