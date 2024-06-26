

package software.amazon.smithy.java.codegen.test.model;

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
public final class UnsafeValueEnum implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.enums#UnsafeValueEnum");
    public static final UnsafeValueEnum A = new UnsafeValueEnum(Type.A, "./U/Y/Q/.../?");
    public static final UnsafeValueEnum B = new UnsafeValueEnum(Type.B, "/////////////");

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.ENUM)
        .stringEnumValues(
            A.value,
            B.value
        )
        .build();

    private final String value;
    private final Type type;

    private UnsafeValueEnum(Type type, String value) {
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

    public static UnsafeValueEnum unknown(String value) {
        return new UnsafeValueEnum(Type.$UNKNOWN, value);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeString(SCHEMA, this.value());
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    public static UnsafeValueEnum from(String value) {
        return switch (value) {
            case "./U/Y/Q/.../?" -> A;
            case "/////////////" -> B;
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
        UnsafeValueEnum that = (UnsafeValueEnum) other;
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
     * Builder for {@link UnsafeValueEnum}.
     */
    public static final class Builder implements ShapeBuilder<UnsafeValueEnum> {
        private String value;

        private Builder() {}

        private Builder value(String value) {
            this.value = Objects.requireNonNull(value, "Enum value cannot be null");
            return this;
        }

        @Override
        public UnsafeValueEnum build() {
            return switch (value) {
                case "./U/Y/Q/.../?" -> A;
                case "/////////////" -> B;
                default -> new UnsafeValueEnum(Type.$UNKNOWN, value);
            };
        }

        @Override
        public Builder deserialize(ShapeDeserializer de) {
            return value(de.readString(SCHEMA));
        }

    }
}

