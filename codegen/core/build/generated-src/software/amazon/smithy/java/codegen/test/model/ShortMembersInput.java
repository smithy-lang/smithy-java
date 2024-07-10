

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
public final class ShortMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#ShortMembersInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("requiredShort", PreludeSchemas.SHORT,
            new RequiredTrait())
        .putMember("optionalShort", PreludeSchemas.SHORT)
        .putMember("defaultShort", PreludeSchemas.SHORT,
            new DefaultTrait(Node.from(1)))
        .build();

    private static final Schema SCHEMA_REQUIRED_SHORT = SCHEMA.member("requiredShort");
    private static final Schema SCHEMA_OPTIONAL_SHORT = SCHEMA.member("optionalShort");
    private static final Schema SCHEMA_DEFAULT_SHORT = SCHEMA.member("defaultShort");

    private transient final short requiredShort;
    private transient final Short optionalShort;
    private transient final short defaultShort;

    private ShortMembersInput(Builder builder) {
        this.requiredShort = builder.requiredShort;
        this.optionalShort = builder.optionalShort;
        this.defaultShort = builder.defaultShort;
    }

    public short requiredShort() {
        return requiredShort;
    }

    public Short optionalShort() {
        return optionalShort;
    }

    public short defaultShort() {
        return defaultShort;
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
        ShortMembersInput that = (ShortMembersInput) other;
        return this.requiredShort == that.requiredShort
               && Objects.equals(this.optionalShort, that.optionalShort)
               && this.defaultShort == that.defaultShort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredShort, optionalShort, defaultShort);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeShort(SCHEMA_REQUIRED_SHORT, requiredShort);
        if (optionalShort != null) {
            serializer.writeShort(SCHEMA_OPTIONAL_SHORT, optionalShort);
        }
        serializer.writeShort(SCHEMA_DEFAULT_SHORT, defaultShort);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ShortMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<ShortMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private short requiredShort;
        private Short optionalShort;
        private short defaultShort = 1;

        private Builder() {}

        public Builder requiredShort(short requiredShort) {
            this.requiredShort = requiredShort;
            tracker.setMember(SCHEMA_REQUIRED_SHORT);
            return this;
        }

        public Builder optionalShort(short optionalShort) {
            this.optionalShort = optionalShort;
            return this;
        }

        public Builder defaultShort(short defaultShort) {
            this.defaultShort = defaultShort;
            return this;
        }

        @Override
        public ShortMembersInput build() {
            tracker.validate();
            return new ShortMembersInput(this);
        }

        @Override
        public ShapeBuilder<ShortMembersInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_REQUIRED_SHORT)) {
                tracker.setMember(SCHEMA_REQUIRED_SHORT);
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
                    case 0 -> builder.requiredShort(de.readShort(member));
                    case 1 -> builder.optionalShort(de.readShort(member));
                    case 2 -> builder.defaultShort(de.readShort(member));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredShort(this.requiredShort);
        builder.optionalShort(this.optionalShort);
        builder.defaultShort(this.defaultShort);
        return builder;
    }

}

