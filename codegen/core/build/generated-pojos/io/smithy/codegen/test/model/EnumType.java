

package io.smithy.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class EnumType implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#EnumType");
    public static final EnumType OPTION_ONE = new EnumType(Type.OPTION_ONE, "option-one");
    public static final EnumType OPTION_TWO = new EnumType(Type.OPTION_TWO, "option-two");

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.ENUM)
        .stringEnumValues(
            OPTION_ONE.value,
            OPTION_TWO.value
        )
        .build();

    private final String value;
    private final Type type;

    private EnumType(Type type, String value) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.value = Objects.requireNonNull(value, "value cannot be null");
    }

    public enum Type {
        $UNKNOWN,
        OPTION_ONE,
        OPTION_TWO
    }

    public String value() {
        return value;
    }

    public Type type() {
        return type;
    }

    public static EnumType unknown(String value) {
        return new EnumType(Type.$UNKNOWN, value);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeString(SCHEMA, this.value());
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        EnumType that = (EnumType) other;
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
     * Builder for {@link EnumType}.
     */
    public static final class Builder implements SdkShapeBuilder<EnumType> {
        private String value;

        private Builder() {}

        private Builder value(String value) {
            this.value = Objects.requireNonNull(value, "Enum value cannot be null");
            return this;
        }

        @Override
        public EnumType build() {
            return switch (value) {
                case "option-one" -> OPTION_ONE;
                case "option-two" -> OPTION_TWO;
                default -> new EnumType(Type.$UNKNOWN, value);
            };
        }

        @Override
        public Builder deserialize(ShapeDeserializer de) {
            return value(de.readString(SCHEMA));
        }

    }
}

