

package io.smithy.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class IntEnumType implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#IntEnumType");
    public static final IntEnumType OPTION_ONE = new IntEnumType(Type.OPTION_ONE, 1);
    public static final IntEnumType OPTION_TWO = new IntEnumType(Type.OPTION_TWO, 10);

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.INT_ENUM)
        .intEnumValues(
            OPTION_ONE.value,
            OPTION_TWO.value
        )
        .build();

    private final int value;
    private final Type type;

    private IntEnumType(Type type, int value) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.value = value;
    }

    public enum Type {
        $UNKNOWN,
        OPTION_ONE,
        OPTION_TWO
    }

    public int value() {
        return value;
    }

    public Type type() {
        return type;
    }

    public static IntEnumType unknown(int value) {
        return new IntEnumType(Type.$UNKNOWN, value);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeInteger(SCHEMA, this.value());
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    public static IntEnumType from(int value) {
        return switch (value) {
            case 1 -> OPTION_ONE;
            case 10 -> OPTION_TWO;
            default -> throw new IllegalArgumentException("Unknown value: " + value);
        };
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        IntEnumType that = (IntEnumType) other;
        return this.value == that.value;
    }

    @Override
    public int hashCode() {
        return value;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link IntEnumType}.
     */
    public static final class Builder implements ShapeBuilder<IntEnumType> {
        private int value;

        private Builder() {}

        private Builder value(int value) {
            this.value = value;
            return this;
        }

        @Override
        public IntEnumType build() {
            return switch (value) {
                case 1 -> OPTION_ONE;
                case 10 -> OPTION_TWO;
                default -> new IntEnumType(Type.$UNKNOWN, value);
            };
        }

        @Override
        public Builder deserialize(ShapeDeserializer de) {
            return value(de.readInteger(SCHEMA));
        }

    }
}

