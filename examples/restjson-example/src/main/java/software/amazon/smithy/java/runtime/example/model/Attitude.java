/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.EnumUtils;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

public enum Attitude implements SerializableShape {
    OPTIMIST("optimist"),
    PESSIMIST("pessimist"),
    REALIST("realist"),
    UNKNOWN(null);

    public static final ShapeId ID = ShapeId.from("smithy.example#Attitude");

    private static final Map<String, Attitude> VALUE_MAP = EnumUtils.valueMapOf(Attitude.class, Attitude::value);

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.ENUM)
        .stringEnumValues(OPTIMIST.value, PESSIMIST.value)
        .build();

    private final String value;

    Attitude(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        if (value == null) {
            encoder.writeNull(SCHEMA);
        } else {
            encoder.writeString(SCHEMA, value);
        }
    }

    public static Attitude from(String value) {
        return value == null ? null : VALUE_MAP.getOrDefault(value, UNKNOWN);
    }

    public static Set<Attitude> knownValues() {
        Set<Attitude> knownValues = EnumSet.allOf(Attitude.class);
        knownValues.remove(UNKNOWN);
        return knownValues;
    }
}
