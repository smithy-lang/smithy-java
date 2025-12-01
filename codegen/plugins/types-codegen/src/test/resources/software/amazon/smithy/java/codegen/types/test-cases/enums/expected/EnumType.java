
package software.amazon.smithy.java.example.standalone.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public sealed interface EnumType extends SmithyEnum, SerializableShape {
    EnumType OPTION_ONE = new OptionOneType();
    EnumType OPTION_TWO = new OptionTwoType();
    List<EnumType> $TYPES = List.of(OPTION_ONE, OPTION_TWO);

    Schema $SCHEMA = Schema.createEnum(ShapeId.from("smithy.java.codegen.types.naming#EnumType"),
        Set.of(OPTION_ONE.getValue(), OPTION_TWO.getValue())
    );

    ShapeId $ID = $SCHEMA.id();

    String getValue();

    @Override
    default void serialize(ShapeSerializer serializer) {
        serializer.writeString($SCHEMA, getValue());
    }

    /**
     * Create an unknown enum variant with the given value.
     *
     * @param value value for the unknown variant.
     */
    static EnumType unknown(String value) {
        return new $Unknown(value);
    }

    /**
     * Returns an unmodifiable list containing the constants of this enum type, in the order declared.
     */
    static List<EnumType> values() {
        return $TYPES;
    }

    /**
     * Returns a {@link EnumType} constant with the specified value.
     *
     * @param value value to create {@code EnumType} from.
     * @throws IllegalArgumentException if value does not match a known value.
     */
    static EnumType from(String value) {
        return switch (value) {
            case "option-one" -> OPTION_ONE;
            case "option-two" -> OPTION_TWO;
            default -> throw new IllegalArgumentException("Unknown value: " + value);
        };
    }

    final class OptionOneType implements EnumType {
        private OptionOneType() {}

        @Override
        public String getValue() {
            return "option-one";
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    final class OptionTwoType implements EnumType {
        private OptionTwoType() {}

        @Override
        public String getValue() {
            return "option-two";
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    record $Unknown(String value) implements EnumType {
        public $Unknown {
            Objects.requireNonNull(value, "Value cannot be null");
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

        private final class $Hidden implements EnumType {
            @Override
            public String getValue() {
                return null;
            }
        }
    }

    /**
     * @return returns a new Builder.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link EnumType}.
     */
    final class Builder implements ShapeBuilder<EnumType> {
        private String value;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        private Builder value(String value) {
            this.value = Objects.requireNonNull(value, "Enum value cannot be null");
            return this;
        }

        @Override
        public EnumType build() {
            return switch (value) {
                case "option-one" -> OPTION_ONE;
                case "option-two" -> OPTION_TWO;
                default -> new $Unknown(value);
            };
        }

        @Override
        public Builder deserialize(ShapeDeserializer de) {
            return value(de.readString($SCHEMA));
        }
    }
}

