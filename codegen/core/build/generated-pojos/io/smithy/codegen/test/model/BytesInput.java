

package io.smithy.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class BytesInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#BytesInput");

    private static final SdkSchema SCHEMA_REQUIRED_BYTE = SdkSchema.memberBuilder("requiredByte", PreludeSchemas.BYTE)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_BYTE = SdkSchema.memberBuilder("optionalByte", PreludeSchemas.BYTE)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_BYTE = SdkSchema.memberBuilder("defaultByte", PreludeSchemas.BYTE)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_BYTE,
            SCHEMA_OPTIONAL_BYTE,
            SCHEMA_DEFAULT_BYTE
        )
        .build();

    private transient final byte requiredByte;
    private transient final Byte optionalByte;
    private transient final byte defaultByte;

    private BytesInput(Builder builder) {
        this.requiredByte = builder.requiredByte;
        this.optionalByte = builder.optionalByte;
        this.defaultByte = builder.defaultByte;
    }

    public Byte requiredByte() {
        return requiredByte;
    }

    public byte optionalByte() {
        return optionalByte;
    }

    public Byte defaultByte() {
        return defaultByte;
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
        BytesInput that = (BytesInput) other;
        return requiredByte == that.requiredByte
               && Objects.equals(optionalByte, that.optionalByte)
               && defaultByte == that.defaultByte;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredByte, optionalByte, defaultByte);
    }

    @Override
    public SdkSchema schema() {
        return SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeByte(SCHEMA_REQUIRED_BYTE, requiredByte);

        if (optionalByte != null) {
            serializer.writeByte(SCHEMA_OPTIONAL_BYTE, optionalByte);
        }

        serializer.writeByte(SCHEMA_DEFAULT_BYTE, defaultByte);

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link BytesInput}.
     */
    public static final class Builder implements SdkShapeBuilder<BytesInput> {
        private byte requiredByte;
        private Byte optionalByte;
        private byte defaultByte = 1;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder requiredByte(byte requiredByte) {
            this.requiredByte = requiredByte;
            tracker.setMember(SCHEMA_REQUIRED_BYTE);
            return this;
        }

        public Builder optionalByte(byte optionalByte) {
            this.optionalByte = optionalByte;
            return this;
        }

        public Builder defaultByte(byte defaultByte) {
            this.defaultByte = defaultByte;
            return this;
        }

        @Override
        public BytesInput build() {
            tracker.validate();
            return new BytesInput(this);
        }

        @Override
        public SdkShapeBuilder<BytesInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }

            if (!tracker.checkMember(SCHEMA_REQUIRED_BYTE)) {
                tracker.setMember(SCHEMA_REQUIRED_BYTE);
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
                    case 0 -> builder.requiredByte(de.readByte(member));
                    case 1 -> builder.optionalByte(de.readByte(member));
                    case 2 -> builder.defaultByte(de.readByte(member));
                }
            }
        }
    }
}

