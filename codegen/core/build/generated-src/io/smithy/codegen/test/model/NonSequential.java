

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
public final class NonSequential implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.enums#NonSequential");
    public static final NonSequential ONE = new NonSequential(Type.ONE, 1);
    public static final NonSequential TEN = new NonSequential(Type.TEN, 10);
    public static final NonSequential TWO = new NonSequential(Type.TWO, 2);
    public static final NonSequential TWENTY = new NonSequential(Type.TWENTY, 20);

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.INT_ENUM)
        .intEnumValues(
            ONE.value,
            TEN.value,
            TWO.value,
            TWENTY.value
        )
        .build();

    private final int value;
    private final Type type;

    private NonSequential(Type type, int value) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.value = value;
    }

    public enum Type {
        $UNKNOWN,
        ONE,
        TEN,
        TWO,
        TWENTY
    }

    public int value() {
        return value;
    }

    public Type type() {
        return type;
    }

    public static NonSequential unknown(int value) {
        return new NonSequential(Type.$UNKNOWN, value);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeInteger(SCHEMA, this.value());
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    public static NonSequential from(int value) {
        return switch (value) {
            case 20 -> TWENTY;
            case 1 -> ONE;
            case 10 -> TEN;
            case 2 -> TWO;
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
        NonSequential that = (NonSequential) other;
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
     * Builder for {@link NonSequential}.
     */
    public static final class Builder implements ShapeBuilder<NonSequential> {
        private int value;

        private Builder() {}

        private Builder value(int value) {
            this.value = value;
            return this;
        }

        @Override
        public NonSequential build() {
            return switch (value) {
                case 20 -> TWENTY;
                case 1 -> ONE;
                case 10 -> TEN;
                case 2 -> TWO;
                default -> new NonSequential(Type.$UNKNOWN, value);
            };
        }

        @Override
        public Builder deserialize(ShapeDeserializer de) {
            return value(de.readInteger(SCHEMA));
        }

    }
}

