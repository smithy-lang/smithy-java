

package software.amazon.smithy.java.codegen.test.model;

import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class EmptyException extends ModeledApiException {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.exceptions#EmptyException");

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .traits(
            new ErrorTrait("server")
        )
        .build();

    private EmptyException(Builder builder) {
        super(ID, null);
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

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link EmptyException}.
     */
    public static final class Builder implements ShapeBuilder<EmptyException> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        @Override
        public EmptyException build() {
            return new EmptyException(this);
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

            }

        }

    }

}

