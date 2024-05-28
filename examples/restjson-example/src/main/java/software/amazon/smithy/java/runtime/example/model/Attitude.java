/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Example of what a generated String enum might look like.
 */
public final class Attitude implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.foo#bar");
    public static final Attitude OPTIMISTIC = new Attitude(Type.OPTIMISTIC, "optimistic");
    public static final Attitude PESSIMISTIC = new Attitude(Type.PESSIMISTIC, "pessimistic");
    public static final Attitude REALISTIC = new Attitude(Type.REALISTIC, "realistic");

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.ENUM)
        .stringEnumValues(
            OPTIMISTIC.value,
            PESSIMISTIC.value,
            REALISTIC.value
        )
        .build();

    private final String value;
    private final Type type;

    private Attitude(Type type, String value) {
        this.type = type;
        this.value = value;
    }

    public enum Type {
        OPTIMISTIC,
        PESSIMISTIC,
        REALISTIC,
        $UNKNOWN
    }

    public String value() {
        return value;
    }

    public Type type() {
        return type;
    }

    public static Attitude valueOf(String value) {
        return switch (value) {
            case "OPTIMISTIC" -> OPTIMISTIC;
            case "PESSIMISTIC" -> PESSIMISTIC;
            case "REALISTIC" -> REALISTIC;
            default -> new Attitude(Type.$UNKNOWN, "");
        };
    }

    public static Attitude of(String value) {
        return switch (value) {
            case "optimistic" -> OPTIMISTIC;
            case "pessimistic" -> PESSIMISTIC;
            case "realistic" -> REALISTIC;
            default -> new Attitude(Type.$UNKNOWN, value);
        };
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.writeString(SCHEMA, value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder implements SdkShapeBuilder<Attitude> {
        private String value;

        public Builder value(String value) {
            this.value = Objects.requireNonNull(value, "Enum value cannot be null");
            return this;
        }

        @Override
        public SdkShapeBuilder<Attitude> deserialize(ShapeDeserializer decoder) {
            return value(decoder.readString(SCHEMA));
        }

        @Override
        public Attitude build() {
            return Attitude.of(value);
        }
    }
}
