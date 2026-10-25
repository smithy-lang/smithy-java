package software.amazon.smithy.java.example.standalone.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.java.core.schema.EnumValue;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.schema.UnknownEnumValue;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public sealed interface EnumType extends SmithyEnum, SerializableShape permits EnumType.OptionOneType, EnumType.OptionTwoType, EnumType.$Unknown, $HiddenEnum_6c1c4910 {
    EnumType OPTION_ONE = new OptionOneType();
    EnumType OPTION_TWO = new OptionTwoType();
    List<EnumType> $TYPES = List.of(OPTION_ONE, OPTION_TWO);

    Schema $SCHEMA = Schema.createEnum(ShapeId.from("smithy.java.codegen.types.naming#EnumType"),
        Set.of("option-one", "option-two"), EnumType.class
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
     * <p>Use {@link #fromValue} when reading values from a service, since a newer service may
     * return values that this client does not know.
     *
     * @param value value to create {@code EnumType} from.
     * @throws IllegalArgumentException if value does not match a known value.
     */
    static EnumType from(String value) {
        var result = $lookup(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value: ".concat(value));
        }
        return result;
    }

    /**
     * Returns the {@link EnumType} constant with the specified value, or an unknown variant
     * if no constant matches.
     *
     * <p>This method never throws for unrecognized values. Use it when reading values from a
     * service, since a newer service may return values that this client does not know.
     * Use {@link #from} when the value must be one of the known constants.
     *
     * @param value value to create {@code EnumType} from.
     */
    static EnumType fromValue(String value) {
        var result = $lookup(value);
        return result != null ? result : new $Unknown(value);
    }

    private static EnumType $lookup(String value) {
        return switch (value) {
            case "option-one" -> OPTION_ONE;
            case "option-two" -> OPTION_TWO;
            default -> null;
        };
    }

    final class OptionOneType extends EnumValue implements EnumType {
        private OptionOneType() {
            super("option-one");
        }
    }

    final class OptionTwoType extends EnumValue implements EnumType {
        private OptionTwoType() {
            super("option-two");
        }
    }

    final class $Unknown extends UnknownEnumValue implements EnumType {
        public $Unknown(String value) {
            super(value);
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
            return fromValue(value);
        }

        @Override
        public Builder deserialize(ShapeDeserializer de) {
            return value(de.readString($SCHEMA));
        }
    }
}
