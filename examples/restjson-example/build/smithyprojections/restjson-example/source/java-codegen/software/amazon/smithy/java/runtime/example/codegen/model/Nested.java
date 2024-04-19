/**
 * Header Line 1
 * Header Line 2
 */

package software.amazon.smithy.java.runtime.example.codegen.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
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
public final class Nested implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#Nested");

    private static final SdkSchema SCHEMA_FIELD_A = SdkSchema.memberBuilder(0, "fieldA", PreludeSchemas.STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_FIELD_B = SdkSchema.memberBuilder(1, "fieldB", PreludeSchemas.INTEGER)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_FIELD_A,
            SCHEMA_FIELD_B
        )
        .build();

    private final String fieldA;
    private final Integer fieldB;

    private Nested(Builder builder) {
        this.fieldA = builder.fieldA;
        this.fieldB = builder.fieldB;
    }

    public String fieldA() {
        return fieldA;
    }

    public Integer fieldB() {
        return fieldB;
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
        Nested that = (Nested) other;
        return Objects.equals(fieldA, that.fieldA)
               && Objects.equals(fieldB, that.fieldB);

    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldA, fieldB);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        // Placeholder. Do nothing
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link Nested}.
     */
    public static final class Builder implements SdkShapeBuilder<Nested> {
        private String fieldA;
        private Integer fieldB;

        private Builder() {}

        public Builder fieldA(String fieldA) {
            this.fieldA = fieldA;
            return this;
        }

        public Builder fieldB(int fieldB) {
            this.fieldB = fieldB;
            return this;
        }

        @Override
        public Nested build() {
            return new Nested(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, (member, de) -> {
                switch (SCHEMA.lookupMemberIndex(member)) {
                    case 0 -> fieldA(de.readString(member));
                    case 1 -> fieldB(de.readInteger(member));
                }
            });
            return this;
        }
    }
}

