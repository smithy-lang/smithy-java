

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class ByteMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#ByteMembersInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("requiredByte", PreludeSchemas.BYTE,
            new RequiredTrait())
        .putMember("optionalByte", PreludeSchemas.BYTE)
        .putMember("defaultByte", PreludeSchemas.BYTE,
            new DefaultTrait(Node.from(1)))
        .build();

    private static final Schema SCHEMA_REQUIRED_BYTE = SCHEMA.member("requiredByte");
    private static final Schema SCHEMA_OPTIONAL_BYTE = SCHEMA.member("optionalByte");
    private static final Schema SCHEMA_DEFAULT_BYTE = SCHEMA.member("defaultByte");

    private transient final byte requiredByte;
    private transient final Byte optionalByte;
    private transient final byte defaultByte;

    private ByteMembersInput(Builder builder) {
        this.requiredByte = builder.requiredByte;
        this.optionalByte = builder.optionalByte;
        this.defaultByte = builder.defaultByte;
    }

    public byte requiredByte() {
        return requiredByte;
    }

    public Byte optionalByte() {
        return optionalByte;
    }

    public byte defaultByte() {
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
        ByteMembersInput that = (ByteMembersInput) other;
        return this.requiredByte == that.requiredByte
               && Objects.equals(this.optionalByte, that.optionalByte)
               && this.defaultByte == that.defaultByte;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredByte, optionalByte, defaultByte);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
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
     * Builder for {@link ByteMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<ByteMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private byte requiredByte;
        private Byte optionalByte;
        private byte defaultByte = 1;

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
        public ByteMembersInput build() {
            tracker.validate();
            return new ByteMembersInput(this);
        }

        @Override
        public ShapeBuilder<ByteMembersInput> errorCorrection() {
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
            public void accept(Builder builder, Schema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.requiredByte(de.readByte(member));
                    case 1 -> builder.optionalByte(de.readByte(member));
                    case 2 -> builder.defaultByte(de.readByte(member));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredByte(this.requiredByte);
        builder.optionalByte(this.optionalByte);
        builder.defaultByte(this.defaultByte);
        return builder;
    }

}

