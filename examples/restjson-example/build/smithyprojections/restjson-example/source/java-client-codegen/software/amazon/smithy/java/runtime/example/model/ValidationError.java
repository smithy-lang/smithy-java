/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.model;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.HttpErrorTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class ValidationError extends ModeledApiException {
    public static final ShapeId ID = ShapeId.from("smithy.example#ValidationError");

    static final Schema SCHEMA = Schema.structureBuilder(ID,
        new ErrorTrait("client"),
        new HttpErrorTrait.Provider().createTrait(
            ShapeId.from("smithy.api#httpError"),
            Node.from(403)
        ))
        .putMember("message", PreludeSchemas.STRING,
            new RequiredTrait())
        .build();

    private static final Schema SCHEMA_MESSAGE = SCHEMA.member("message");

    private ValidationError(Builder builder) {
        super(ID, builder.message);
    }

    @Override
    public @NonNull String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(@NonNull ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(@NonNull ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_MESSAGE, getMessage());
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ValidationError}.
     */
    public static final class Builder implements ShapeBuilder<ValidationError> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private String message;

        private Builder() {}

        public Builder message(String message) {
            this.message = Objects.requireNonNull(message, "message cannot be null");
            tracker.setMember(SCHEMA_MESSAGE);
            return this;
        }

        @Override
        public @NonNull ValidationError build() {
            tracker.validate();
            return new ValidationError(this);
        }

        @Override
        public ShapeBuilder<ValidationError> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_MESSAGE)) {
                message("");
            }
            return this;
        }

        @Override
        public Builder deserialize(@NonNull ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
            return this;
        }

        private static final class InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final InnerDeserializer INSTANCE = new InnerDeserializer();

            @Override
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.message(de.readString(member));
                }
            }
        }
    }

}

