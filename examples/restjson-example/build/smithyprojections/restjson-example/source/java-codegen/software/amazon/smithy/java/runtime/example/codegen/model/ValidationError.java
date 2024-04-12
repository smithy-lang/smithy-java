/**
 * Header Line 1
 * Header Line 2
 */

package software.amazon.smithy.java.runtime.example.codegen.model;

import software.amazon.smithy.java.runtime.core.schema.ModeledSdkException;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.RequiredTrait;


public final class ValidationError extends ModeledSdkException {
    public static final ShapeId ID = ShapeId.from("smithy.example#ValidationError");

    private static final SdkSchema SCHEMA_MESSAGE = SdkSchema.memberBuilder(0, "message", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new RequiredTrait()

        )
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .traits(
            new ErrorTrait("client")
        )
        .members(
            SCHEMA_MESSAGE
        )
        .build();

    private ValidationError(Builder builder) {
        super(ID, builder.message);
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        // Placeholder. Do nothing
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ValidationError}.
     */
    public static final class Builder implements SdkShapeBuilder<ValidationError> {
        private String message;

        private Builder() {}

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        @Override
        public ValidationError build() {
            return new ValidationError(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            // PLACEHOLDER. Needs implementation
            return this;
        }
    }
}

