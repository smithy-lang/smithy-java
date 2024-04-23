/*
 * Example file license header.
 * File header line two
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

    private static final SdkSchema SCHEMA_OPTIONAL_STRING_FIELD = SdkSchema.memberBuilder("optionalStringField", PreludeSchemas.STRING)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_OPTIONAL_STRING_FIELD
        )
        .build();

    private final String optionalStringField;

    private Nested(Builder builder) {
        this.optionalStringField = builder.optionalStringField;
    }

    public String optionalStringField() {
        return optionalStringField;
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
        return Objects.equals(optionalStringField, that.optionalStringField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(optionalStringField);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, st -> {
            ShapeSerializer.writeIfNotNull(st, SCHEMA_OPTIONAL_STRING_FIELD, optionalStringField);
        });
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link Nested}.
     */
    public static final class Builder implements SdkShapeBuilder<Nested> {
        private String optionalStringField;

        private Builder() {}

        public Builder optionalStringField(String optionalStringField) {
            this.optionalStringField = optionalStringField;
            return this;
        }

        @Override
        public Nested build() {
            return new Nested(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            // PLACEHOLDER. Needs implementation
            return this;
        }
    }
}

