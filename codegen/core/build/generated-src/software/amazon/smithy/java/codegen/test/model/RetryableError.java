

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.RetryableTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class RetryableError extends ModeledApiException {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.traits#RetryableError");

    static final Schema SCHEMA = Schema.structureBuilder(ID,
        new ErrorTrait("client"),
        RetryableTrait.builder().throttling(false).build())
        .putMember("message", PreludeSchemas.STRING,
            new RequiredTrait())
        .build();

    private static final Schema SCHEMA_MESSAGE = SCHEMA.member("message");

    private RetryableError(Builder builder) {
        super(ID, builder.message);
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_MESSAGE, getMessage());
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link RetryableError}.
     */
    public static final class Builder implements ShapeBuilder<RetryableError> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private String message;

        private Builder() {}

        public Builder message(String message) {
            this.message = Objects.requireNonNull(message, "message cannot be null");
            tracker.setMember(SCHEMA_MESSAGE);
            return this;
        }

        @Override
        public RetryableError build() {
            tracker.validate();
            return new RetryableError(this);
        }

        @Override
        public ShapeBuilder<RetryableError> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_MESSAGE)) {
                message("");
            }
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
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

