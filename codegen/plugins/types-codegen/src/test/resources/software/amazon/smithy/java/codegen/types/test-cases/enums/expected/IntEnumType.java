
package software.amazon.smithy.java.example.standalone.model;

import java.util.List;
import java.util.Set;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public sealed interface IntEnumType extends SerializableShape {
    IntEnumType FIRST = new FirstType();
    IntEnumType SECOND = new SecondType();
    IntEnumType FIFTH = new FifthType();
    List<IntEnumType> $TYPES = List.of(FIRST, SECOND, FIFTH);

    Schema $SCHEMA = Schema.createIntEnum(ShapeId.from("smithy.java.codegen.types.naming#IntEnumType"),
        Set.of(FIRST.getValue(), SECOND.getValue(), FIFTH.getValue())
    );

    ShapeId $ID = $SCHEMA.id();

    int getValue();

    @Override
    default void serialize(ShapeSerializer serializer) {
        serializer.writeInteger($SCHEMA, getValue());
    }

    /**
     * Create an unknown enum variant with the given value.
     *
     * @param value value for the unknown variant.
     */
    static IntEnumType unknown(int value) {
        return new $Unknown(value);
    }

    /**
     * Returns an unmodifiable list containing the constants of this enum type, in the order declared.
     */
    static List<IntEnumType> values() {
        return $TYPES;
    }

    /**
     * Returns a {@link IntEnumType} constant with the specified value.
     *
     * @param value value to create {@code IntEnumType} from.
     * @throws IllegalArgumentException if value does not match a known value.
     */
    static IntEnumType from(int value) {
        return switch (value) {
            case 1 -> FIRST;
            case 2 -> SECOND;
            case 5 -> FIFTH;
            default -> throw new IllegalArgumentException("Unknown value: " + value);
        };
    }

    final class FirstType implements IntEnumType {
        private FirstType() {}

        @Override
        public int getValue() {
            return 1;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    final class SecondType implements IntEnumType {
        private SecondType() {}

        @Override
        public int getValue() {
            return 2;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    final class FifthType implements IntEnumType {
        private FifthType() {}

        @Override
        public int getValue() {
            return 5;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

    }

    record $Unknown(int value) implements IntEnumType {

        @Override
        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            return ToStringSerializer.serialize(this);
        }

        private final class $Hidden implements IntEnumType {
            @Override
            public int getValue() {
                return 0;
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
     * Builder for {@link IntEnumType}.
     */
    final class Builder implements ShapeBuilder<IntEnumType> {
        private int value;

        private Builder() {}

        @Override
        public Schema schema() {
            return $SCHEMA;
        }

        private Builder value(int value) {
            this.value = value;
            return this;
        }

        @Override
        public IntEnumType build() {
            return switch (value) {
                case 1 -> FIRST;
                case 2 -> SECOND;
                case 5 -> FIFTH;
                default -> new $Unknown(value);
            };
        }

        @Override
        public Builder deserialize(ShapeDeserializer de) {
            return value(de.readInteger($SCHEMA));
        }
    }
}

