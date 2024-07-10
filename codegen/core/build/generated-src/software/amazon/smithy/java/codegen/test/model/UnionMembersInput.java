

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class UnionMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#UnionMembersInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("requiredUnion", NestedUnion.SCHEMA,
            new RequiredTrait())
        .putMember("optionalUnion", NestedUnion.SCHEMA)
        .build();

    private static final Schema SCHEMA_REQUIRED_UNION = SCHEMA.member("requiredUnion");
    private static final Schema SCHEMA_OPTIONAL_UNION = SCHEMA.member("optionalUnion");

    private transient final NestedUnion requiredUnion;
    private transient final NestedUnion optionalUnion;

    private UnionMembersInput(Builder builder) {
        this.requiredUnion = builder.requiredUnion;
        this.optionalUnion = builder.optionalUnion;
    }

    public NestedUnion requiredUnion() {
        return requiredUnion;
    }

    public NestedUnion optionalUnion() {
        return optionalUnion;
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
        UnionMembersInput that = (UnionMembersInput) other;
        return Objects.equals(this.requiredUnion, that.requiredUnion)
               && Objects.equals(this.optionalUnion, that.optionalUnion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredUnion, optionalUnion);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA_REQUIRED_UNION, requiredUnion);
        if (optionalUnion != null) {
            serializer.writeStruct(SCHEMA_OPTIONAL_UNION, optionalUnion);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link UnionMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<UnionMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private NestedUnion requiredUnion;
        private NestedUnion optionalUnion;

        private Builder() {}

        public Builder requiredUnion(NestedUnion requiredUnion) {
            this.requiredUnion = Objects.requireNonNull(requiredUnion, "requiredUnion cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_UNION);
            return this;
        }

        public Builder optionalUnion(NestedUnion optionalUnion) {
            this.optionalUnion = optionalUnion;
            return this;
        }

        @Override
        public UnionMembersInput build() {
            tracker.validate();
            return new UnionMembersInput(this);
        }

        @Override
        public ShapeBuilder<UnionMembersInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
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
                    case 0 -> builder.requiredUnion(NestedUnion.builder().deserialize(de).build());
                    case 1 -> builder.optionalUnion(NestedUnion.builder().deserialize(de).build());
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredUnion(this.requiredUnion);
        builder.optionalUnion(this.optionalUnion);
        return builder;
    }

}

