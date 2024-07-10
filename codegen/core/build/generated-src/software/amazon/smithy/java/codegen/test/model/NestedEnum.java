

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class NestedEnum implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.common#NestedEnum");
    public static final NestedEnum A = new NestedEnum(Type.A, "A");
    public static final NestedEnum B = new NestedEnum(Type.B, "B");

    static final Schema SCHEMA = Schema.createEnum(ID,
        Set.of(A.value,B.value)
    );

    private final String value;
    private final Type type;

    private NestedEnum(Type type, String value) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.value = Objects.requireNonNull(value, "value cannot be null");
    }

    public enum Type {
        $UNKNOWN,
        A,
        B
    }

    public String value() {
        return value;
    }

    public Type type() {
        return type;
    }

    public static NestedEnum unknown(String value) {
        return new NestedEnum(Type.$UNKNOWN, value);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeString(SCHEMA, this.value());
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    public static NestedEnum from(String value) {
        return switch (value) {
            case "A" -> A;
            case "B" -> B;
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
        NestedEnum that = (NestedEnum) other;
        return this.value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link NestedEnum}.
     */
    public static final class Builder implements ShapeBuilder<NestedEnum> {
        private String value;

        private Builder() {}

        private Builder value(String value) {
            this.value = Objects.requireNonNull(value, "Enum value cannot be null");
            return this;
        }

        @Override
        public NestedEnum build() {
            return switch (value) {
                case "A" -> A;
                case "B" -> B;
                default -> new NestedEnum(Type.$UNKNOWN, value);
            };
        }

        @Override
        public Builder deserialize(ShapeDeserializer de) {
            return value(de.readString(SCHEMA));
        }

    }
}

