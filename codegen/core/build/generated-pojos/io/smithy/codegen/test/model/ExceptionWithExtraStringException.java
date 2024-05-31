

package io.smithy.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.ModeledSdkException;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class ExceptionWithExtraStringException extends ModeledSdkException {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.exceptions#ExceptionWithExtraStringException");

    private static final SdkSchema SCHEMA_MESSAGE = SdkSchema.memberBuilder("message", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_EXTRA = SdkSchema.memberBuilder("extra", PreludeSchemas.STRING)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .traits(
            new ErrorTrait("server")
        )
        .members(
            SCHEMA_MESSAGE,
            SCHEMA_EXTRA
        )
        .build();

    private transient final String extra;

    private ExceptionWithExtraStringException(Builder builder) {
        super(ID, builder.message);
        this.extra = builder.extra;
    }

    public String extra() {
        return extra;
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

        if (extra != null) {
            serializer.writeString(SCHEMA_EXTRA, extra);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ExceptionWithExtraStringException}.
     */
    public static final class Builder implements SdkShapeBuilder<ExceptionWithExtraStringException> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private String message;
        private String extra;

        private Builder() {}

        public Builder message(String message) {
            this.message = Objects.requireNonNull(message, "message cannot be null");
            tracker.setMember(SCHEMA_MESSAGE);
            return this;
        }

        public Builder extra(String extra) {
            this.extra = extra;
            return this;
        }

        @Override
        public ExceptionWithExtraStringException build() {
            tracker.validate();
            return new ExceptionWithExtraStringException(this);
        }

        @Override
        public SdkShapeBuilder<ExceptionWithExtraStringException> errorCorrection() {
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
            public void accept(Builder builder, SdkSchema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.message(de.readString(member));
                    case 1 -> builder.extra(de.readString(member));
                }
            }
        }

    }

}

