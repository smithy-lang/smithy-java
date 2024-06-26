

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
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class IntegerMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#IntegerMembersInput");

    private static final Schema SCHEMA_REQUIRED_INT = Schema.memberBuilder("requiredInt", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final Schema SCHEMA_OPTIONAL_INT = Schema.memberBuilder("optionalInt", PreludeSchemas.INTEGER)
        .id(ID)
        .build();

    private static final Schema SCHEMA_DEFAULT_INT = Schema.memberBuilder("defaultInt", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(
            new DefaultTrait(Node.from(1))
        )
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_INT,
            SCHEMA_OPTIONAL_INT,
            SCHEMA_DEFAULT_INT
        )
        .build();

    private transient final int requiredInt;
    private transient final Integer optionalInt;
    private transient final int defaultInt;

    private IntegerMembersInput(Builder builder) {
        this.requiredInt = builder.requiredInt;
        this.optionalInt = builder.optionalInt;
        this.defaultInt = builder.defaultInt;
    }

    public int requiredInt() {
        return requiredInt;
    }

    public Integer optionalInt() {
        return optionalInt;
    }

    public int defaultInt() {
        return defaultInt;
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
        IntegerMembersInput that = (IntegerMembersInput) other;
        return this.requiredInt == that.requiredInt
               && Objects.equals(this.optionalInt, that.optionalInt)
               && this.defaultInt == that.defaultInt;
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredInt, optionalInt, defaultInt);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeInteger(SCHEMA_REQUIRED_INT, requiredInt);

        if (optionalInt != null) {
            serializer.writeInteger(SCHEMA_OPTIONAL_INT, optionalInt);
        }

        serializer.writeInteger(SCHEMA_DEFAULT_INT, defaultInt);

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link IntegerMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<IntegerMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private int requiredInt;
        private Integer optionalInt;
        private int defaultInt = 1;

        private Builder() {}

        public Builder requiredInt(int requiredInt) {
            this.requiredInt = requiredInt;
            tracker.setMember(SCHEMA_REQUIRED_INT);
            return this;
        }

        public Builder optionalInt(int optionalInt) {
            this.optionalInt = optionalInt;
            return this;
        }

        public Builder defaultInt(int defaultInt) {
            this.defaultInt = defaultInt;
            return this;
        }

        @Override
        public IntegerMembersInput build() {
            tracker.validate();
            return new IntegerMembersInput(this);
        }

        @Override
        public ShapeBuilder<IntegerMembersInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_REQUIRED_INT)) {
                tracker.setMember(SCHEMA_REQUIRED_INT);
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
                    case 0 -> builder.requiredInt(de.readInteger(member));
                    case 1 -> builder.optionalInt(de.readInteger(member));
                    case 2 -> builder.defaultInt(de.readInteger(member));
                }
            }

        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredInt(this.requiredInt);
        builder.optionalInt(this.optionalInt);
        builder.defaultInt(this.defaultInt);
        return builder;
    }

}

