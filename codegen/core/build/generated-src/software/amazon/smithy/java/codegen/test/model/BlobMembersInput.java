

package software.amazon.smithy.java.codegen.test.model;

import java.nio.ByteBuffer;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class BlobMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#BlobMembersInput");

    private static final Schema SCHEMA_REQUIRED_BLOB = Schema.memberBuilder("requiredBlob", PreludeSchemas.BLOB)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final Schema SCHEMA_OPTIONAL_BLOB = Schema.memberBuilder("optionalBlob", PreludeSchemas.BLOB)
        .id(ID)
        .build();

    private static final Schema SCHEMA_STREAMING_BLOB = Schema.memberBuilder("streamingBlob", SharedSchemas.STREAMING_BLOB)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_BLOB,
            SCHEMA_STREAMING_BLOB,
            SCHEMA_OPTIONAL_BLOB
        )
        .build();

    private transient final ByteBuffer requiredBlob;
    private transient final ByteBuffer optionalBlob;
    private transient final DataStream streamingBlob;

    private BlobMembersInput(Builder builder) {
        this.requiredBlob = builder.requiredBlob.asReadOnlyBuffer();
        this.optionalBlob = builder.optionalBlob == null ? null : builder.optionalBlob.asReadOnlyBuffer();
        this.streamingBlob = builder.streamingBlob;
    }

    public ByteBuffer requiredBlob() {
        return requiredBlob;
    }

    public ByteBuffer optionalBlob() {
        return optionalBlob;
    }

    public DataStream streamingBlob() {
        return streamingBlob;
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
        BlobMembersInput that = (BlobMembersInput) other;
        return Objects.equals(this.requiredBlob, that.requiredBlob)
               && Objects.equals(this.optionalBlob, that.optionalBlob)
               && Objects.equals(this.streamingBlob, that.streamingBlob);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredBlob, optionalBlob, streamingBlob);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeBlob(SCHEMA_REQUIRED_BLOB, requiredBlob);

        if (optionalBlob != null) {
            serializer.writeBlob(SCHEMA_OPTIONAL_BLOB, optionalBlob);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link BlobMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<BlobMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private ByteBuffer requiredBlob;
        private ByteBuffer optionalBlob;
        private DataStream streamingBlob = DataStream.ofEmpty();

        private Builder() {}

        public Builder requiredBlob(ByteBuffer requiredBlob) {
            this.requiredBlob = Objects.requireNonNull(requiredBlob, "requiredBlob cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_BLOB);
            return this;
        }

        public Builder optionalBlob(ByteBuffer optionalBlob) {
            this.optionalBlob = optionalBlob;
            return this;
        }

        @Override
        public void setDataStream(DataStream stream) {
            streamingBlob(stream);
        }

        public Builder streamingBlob(DataStream streamingBlob) {
            this.streamingBlob = Objects.requireNonNull(streamingBlob, "streamingBlob cannot be null");
            tracker.setMember(SCHEMA_STREAMING_BLOB);
            return this;
        }

        @Override
        public BlobMembersInput build() {
            tracker.validate();
            return new BlobMembersInput(this);
        }

        @Override
        public ShapeBuilder<BlobMembersInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_REQUIRED_BLOB)) {
                requiredBlob(ByteBuffer.allocate(0));
            }
            if (!tracker.checkMember(SCHEMA_STREAMING_BLOB)) {
                streamingBlob(DataStream.ofEmpty());
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
                    case 0 -> builder.requiredBlob(de.readBlob(member));
                    case 2 -> builder.optionalBlob(de.readBlob(member));
                }
            }

        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredBlob(this.requiredBlob);
        builder.optionalBlob(this.optionalBlob);
        builder.streamingBlob(this.streamingBlob);
        return builder;
    }

}

