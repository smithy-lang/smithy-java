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
public sealed interface TextType extends SmithyEnum, SerializableShape permits TextType.TextTypeValue, TextType.$Unknown, $HiddenEnum_6c1c4910 {
    TextType TEXT = new TextTypeValue();
    List<TextType> $TYPES = List.of(TEXT);

    Schema $SCHEMA = Schema.createEnum(ShapeId.from("smithy.java.codegen.types.naming#TextType"),
        Set.of("TEXT"), TextType.class
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
    static TextType unknown(String value) {
        return new $Unknown(value);
    }

    /**
     * Returns an unmodifiable list containing the constants of this enum type, in the order declared.
     */
    static List<TextType> values() {
        return $TYPES;
    }

    /**
     * Returns a {@link TextType} constant with the specified value.
     *
     * <p>Use {@link #fromValue} when reading values from a service, since a newer service may
     * return values that this client does not know.
     *
     * @param value value to create {@code TextType} from.
     * @throws IllegalArgumentException if value does not match a known value.
     */
    static TextType from(String value) {
        var result = $lookup(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value: ".concat(value));
        }
        return result;
    }

    /**
     * Returns the {@link TextType} constant with the specified value, or an unknown variant
     * if no constant matches.
     *
     * <p>This method never throws for unrecognized values. Use it when reading values from a
     * service, since a newer service may return values that this client does not know.
     * Use {@link #from} when the value must be one of the known constants.
     *
     * @param value value to create {@code TextType} from.
     */
    static TextType fromValue(String value) {
        var result = $lookup(value);
        return result != null ? result : new $Unknown(value);
    }

    private static TextType $lookup(String value) {
        return switch (value) {
            case "TEXT" -> TEXT;
            default -> null;
        };
    }

    final class TextTypeValue extends EnumValue implements TextType {
        private TextTypeValue() {
            super("TEXT");
        }
    }

    final class $Unknown extends UnknownEnumValue implements TextType {
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
     * Builder for {@link TextType}.
     */
    final class Builder implements ShapeBuilder<TextType> {
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
        public TextType build() {
            return fromValue(value);
        }

        @Override
        public Builder deserialize(ShapeDeserializer de) {
            return value(de.readString($SCHEMA));
        }
    }
}
