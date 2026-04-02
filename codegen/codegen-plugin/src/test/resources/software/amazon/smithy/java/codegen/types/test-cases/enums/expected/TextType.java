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
public sealed interface TextType extends SmithyEnum, SerializableShape {
    TextType TEXT = new TextTypeValue();
    List<TextType> $TYPES = List.of(TEXT);

    Schema $SCHEMA = Schema.createEnum(ShapeId.from("smithy.java.codegen.types.naming#TextType"),
        Set.of(TEXT.getValue()), TextType.class
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
     * @param value value to create {@code TextType} from.
     * @throws IllegalArgumentException if value does not match a known value.
     */
    static TextType from(String value) {
        return switch (value) {
            case "TEXT" -> TEXT;
            default -> throw new IllegalArgumentException("Unknown value: " + value);
        };
    }

    final class TextTypeValue implements TextType {
        private TextTypeValue() {}

        @Override
        public String getValue() {
            return "TEXT";
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    record $Unknown(String value) implements TextType {
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

        private final class $Hidden implements TextType {
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
            return switch (value) {
                case "TEXT" -> TEXT;
                default -> new $Unknown(value);
            };
        }

        @Override
        public Builder deserialize(ShapeDeserializer de) {
            return value(de.readString($SCHEMA));
        }
    }
}
