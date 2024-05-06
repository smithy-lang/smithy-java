/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.codegen.model;

import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.ModeledSdkException;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.HttpErrorTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class ValidationError extends ModeledSdkException {
    public static final ShapeId ID = ShapeId.from("smithy.example#ValidationError");

    private static final SdkSchema SCHEMA_MESSAGE = SdkSchema.memberBuilder("message", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .traits(
            new ErrorTrait("client"),
            new HttpErrorTrait.Provider().createTrait(
                ShapeId.from("smithy.api#httpError"),
                Node.from(403)
            )
        )
        .members(
            SCHEMA_MESSAGE
        )
        .build();

   private static final InnerSerializer INNER_SERIALIZER = new InnerSerializer();

    private ValidationError(Builder builder) {
        super(ID, builder.message);
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this, INNER_SERIALIZER);
    }

    private static final class InnerSerializer implements BiConsumer<ValidationError, ShapeSerializer> {
        @Override
        public void accept(ValidationError shape, ShapeSerializer serializer) {
            serializer.writeString(SCHEMA_MESSAGE, shape.getMessage());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ValidationError}.
     */
    public static final class Builder implements SdkShapeBuilder<ValidationError> {
        private String message;

        private static final InnerDeserializer INNER_DESERIALIZER = new InnerDeserializer();

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
            decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
            return this;
        }

        private static final class InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final InnerDeserializer INSTANCE = new InnerDeserializer();
            @Override
            public void accept(Builder builder, SdkSchema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.message(de.readString(member));
                }
            }
        }
    }
}

