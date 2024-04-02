/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class MapAny implements Any {

    private final SdkSchema schema;
    private final Map<Any, Any> value;

    MapAny(Map<Any, Any> value, SdkSchema schema) {
        if (!(schema.type() == ShapeType.DOCUMENT || schema.type() == ShapeType.MAP)) {
            throw new SdkSerdeException("Expected map Any to have a map or document schema, but found " + schema);
        }

        // Validate that map keys are member shapes named 'key' or documents, and all the same.
        // Validate that map values are member shapes named 'value' or documents, and all the same.
        if (!value.isEmpty()) {
            SdkSchema firstKey = null;
            SdkSchema firstValue = null;
            for (var entry : value.entrySet()) {
                var keySchema = entry.getKey().schema();
                var valueSchema = entry.getValue().schema();
                // Every key must resolve to a valid map key type.
                switch (entry.getKey().type()) {
                    case STRING, ENUM, INTEGER, INT_ENUM, LONG -> {
                    }
                    default -> throw new SdkSerdeException(
                        "Map keys must be a string, enum, integer, intEnum, or long, "
                            + "but found " + entry.getKey()
                    );
                }
                // If it's the entry, then validate that the key and value members are correct. These member schemas
                // are compared against later entries to ensure every entry is correct and consistent.
                if (firstKey == null) {
                    firstKey = keySchema;
                    firstValue = valueSchema;
                    if (keySchema.isMember() && !keySchema.memberName().equals("key")) {
                        throw new SdkSerdeException(
                            "Map Any key member has a member name of '" + keySchema.memberName() + "', "
                                + "but map key members must be named 'key': " + entry.getKey()
                        );
                    } else if (!keySchema.isMember() && keySchema.type() != ShapeType.DOCUMENT) {
                        throw new SdkSerdeException(
                            "Key members of a map Any must be a member or a document schema, "
                                + "but found " + entry.getKey()
                        );
                    }
                    if (valueSchema.isMember() && !valueSchema.memberName().equals("value")) {
                        throw new SdkSerdeException(
                            "Map Any value member has a member name of '" + valueSchema.memberName() + "', "
                                + "but map value members must be named 'value': " + firstValue
                        );
                    } else if (!valueSchema.isMember() && valueSchema.type() != ShapeType.DOCUMENT) {
                        throw new SdkSerdeException(
                            "Value members of a map Any must be a member or a document schema, "
                                + "but found " + entry.getValue()
                        );
                    }
                } else if (!keySchema.equals(firstKey)) {
                    throw new SdkSerdeException(
                        "Every Any map key member must use the same schema. Expected "
                            + firstKey + ", but found " + entry.getKey()
                    );
                } else if (!valueSchema.equals(firstValue)) {
                    throw new SdkSerdeException(
                        "Every Any map value member must use the same schema. Expected "
                            + firstValue + ", but found " + entry.getValue()
                    );
                }
            }
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
        return ShapeType.MAP;
    }

    @Override
    public Map<Any, Any> asMap() {
        return value;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.beginMap(schema, s -> {
            for (var entry : value.entrySet()) {
                Any key = entry.getKey();
                Consumer<ShapeSerializer> valueSer = ser -> entry.getValue().serialize(ser);
                switch (key.type()) {
                    case STRING, ENUM -> s.entry(entry.getKey().asString(), valueSer);
                    case INTEGER, INT_ENUM -> s.entry(entry.getKey().asInteger(), valueSer);
                    case LONG -> s.entry(entry.getKey().asLong(), valueSer);
                    default -> throw new UnsupportedOperationException("Unexpected document map key: " + key.type());
                }
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
        MapAny mapAny = (MapAny) o;
        return Objects.equals(schema, mapAny.schema) && Objects.equals(value, mapAny.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, value);
    }

    @Override
    public String toString() {
        return "MapAny{value=" + value + ", schema=" + schema + '}';
    }
}
