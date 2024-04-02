/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class StructAny implements Any {

    private final SdkSchema schema;
    private final Map<String, Any> value;

    StructAny(Map<String, Any> value, SdkSchema schema) {
        if (!(schema.type() == ShapeType.DOCUMENT || schema.type() == ShapeType.STRUCTURE)) {
            throw new SdkSerdeException(
                "Expected struct Any to have a structure or document schema, but found "
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
        return schema.type() != ShapeType.DOCUMENT ? schema.type() : ShapeType.STRUCTURE;
    }

    @Override
    public Any getStructMember(String memberName) {
        return value.get(memberName);
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.beginStruct(schema, structSerializer -> {
            for (var value : value.values()) {
                structSerializer.member(value.schema(), value::serialize);
            }
        });
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StructAny structAny = (StructAny) o;
        return Objects.equals(schema, structAny.schema) && Objects.equals(value, structAny.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, value);
    }

    @Override
    public String toString() {
        return "StructAny{value=" + value + ", schema=" + schema + '}';
    }
}
