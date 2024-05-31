

package io.smithy.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class UnionsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#UnionsInput");

    private static final SdkSchema SCHEMA_REQUIRED_UNION = SdkSchema.memberBuilder("requiredUnion", UnionType.SCHEMA)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_UNION = SdkSchema.memberBuilder("optionalUnion", UnionType.SCHEMA)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_UNION,
            SCHEMA_OPTIONAL_UNION
        )
        .build();

    private transient final UnionType requiredUnion;
    private transient final UnionType optionalUnion;

    private UnionsInput(Builder builder) {
        this.requiredUnion = builder.requiredUnion;
        this.optionalUnion = builder.optionalUnion;
    }

    public UnionType requiredUnion() {
        return requiredUnion;
    }

    public UnionType optionalUnion() {
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
        UnionsInput that = (UnionsInput) other;
        return Objects.equals(requiredUnion, that.requiredUnion)
               && Objects.equals(optionalUnion, that.optionalUnion);
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
     * Builder for {@link UnionsInput}.
     */
    public static final class Builder implements SdkShapeBuilder<UnionsInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private UnionType requiredUnion;
        private UnionType optionalUnion;

        private Builder() {}

        public Builder requiredUnion(UnionType requiredUnion) {
            this.requiredUnion = Objects.requireNonNull(requiredUnion, "requiredUnion cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_UNION);
            return this;
        }

        public Builder optionalUnion(UnionType optionalUnion) {
            this.optionalUnion = optionalUnion;
            return this;
        }

        @Override
        public UnionsInput build() {
            tracker.validate();
            return new UnionsInput(this);
        }

        @Override
        public SdkShapeBuilder<UnionsInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_REQUIRED_UNION)) {
                requiredUnion(null);
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
                    case 0 -> builder.requiredUnion(UnionType.builder().deserialize(de).build());
                    case 1 -> builder.optionalUnion(UnionType.builder().deserialize(de).build());
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

