

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
public final class NestedIntEnum implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.common#NestedIntEnum");
    public static final NestedIntEnum A = new NestedIntEnum(Type.A, 1);
    public static final NestedIntEnum B = new NestedIntEnum(Type.B, 2);

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.INT_ENUM)
        .intEnumValues(
            A.value,
            B.value
        )
        .build();

    private final int value;
    private final Type type;

    private NestedIntEnum(Type type, int value) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.value = value;
    }

    public enum Type {
        $UNKNOWN,
        A,
        B
    }

    public int value() {
        return value;
    }

    public Type type() {
        return type;
    }

    public static NestedIntEnum unknown(int value) {
        return new NestedIntEnum(Type.$UNKNOWN, value);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeInteger(SCHEMA, this.value());
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    public static NestedIntEnum from(int value) {
        return switch (value) {
            case 1 -> A;
            case 2 -> B;
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
        NestedIntEnum that = (NestedIntEnum) other;
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
     * Builder for {@link NestedIntEnum}.
     */
    public static final class Builder implements ShapeBuilder<NestedIntEnum> {
        private int value;

        private Builder() {}

        private Builder value(int value) {
            this.value = value;
            return this;
        }

        @Override
        public NestedIntEnum build() {
            return switch (value) {
                case 1 -> A;
                case 2 -> B;
                default -> new NestedIntEnum(Type.$UNKNOWN, value);
            };
        }

        @Override
        public Builder deserialize(ShapeDeserializer de) {
            return value(de.readInteger(SCHEMA));
        }

    }
}

