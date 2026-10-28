package software.amazon.smithy.java.example.standalone.model;

import java.util.List;
import java.util.Set;
import software.amazon.smithy.java.core.schema.IntEnumValue;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.SmithyIntEnum;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public sealed interface IntEnumType extends SmithyIntEnum, SerializableShape permits IntEnumType.FirstType, IntEnumType.SecondType, IntEnumType.FifthType, IntEnumType.$Unknown, $HiddenIntEnum_a50bb5c2 {
    IntEnumType FIRST = new FirstType();
    IntEnumType SECOND = new SecondType();
    IntEnumType FIFTH = new FifthType();
    List<IntEnumType> $TYPES = List.of(FIRST, SECOND, FIFTH);

    Schema $SCHEMA = Schema.createIntEnum(ShapeId.from("smithy.java.codegen.types.naming#IntEnumType"),
        Set.of(1, 2, 5), IntEnumType.class
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
     * <p>Use {@link #fromValue} when reading values from a service, since a newer service may
     * return values that this client does not know.
     *
     * @param value value to create {@code IntEnumType} from.
     * @throws IllegalArgumentException if value does not match a known value.
     */
    static IntEnumType from(int value) {
        var result = $lookup(value);
        if (result == null) {
            throw new IllegalArgumentException("Unknown value: ".concat(Integer.toString(value)));
        }
        return result;
    }

    /**
     * Returns the {@link IntEnumType} constant with the specified value, or an unknown variant
     * if no constant matches.
     *
     * <p>This method never throws for unrecognized values. Use it when reading values from a
     * service, since a newer service may return values that this client does not know.
     * Use {@link #from} when the value must be one of the known constants.
     *
     * @param value value to create {@code IntEnumType} from.
     */
    static IntEnumType fromValue(int value) {
        var result = $lookup(value);
        return result != null ? result : new $Unknown(value);
    }

    private static IntEnumType $lookup(int value) {
        return switch (value) {
            case 1 -> FIRST;
            case 2 -> SECOND;
            case 5 -> FIFTH;
            default -> null;
        };
    }

    final class FirstType extends IntEnumValue implements IntEnumType {
        private FirstType() {
            super(1);
        }
    }

    final class SecondType extends IntEnumValue implements IntEnumType {
        private SecondType() {
            super(2);
        }
    }

    final class FifthType extends IntEnumValue implements IntEnumType {
        private FifthType() {
            super(5);
        }
    }

    record $Unknown(int value) implements IntEnumType {

        @Override
        public int getValue() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof $Unknown that && value == that.value;
        }

        @Override
        public int hashCode() {
            return value;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
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
            return fromValue(value);
        }

        @Override
        public Builder deserialize(ShapeDeserializer de) {
            return value(de.readInteger($SCHEMA));
        }
    }
}
