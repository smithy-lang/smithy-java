

package io.smithy.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class StructureMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#StructureMembersInput");

    private static final Schema SCHEMA_REQUIRED_STRUCT = Schema.memberBuilder("requiredStruct", NestedStruct.SCHEMA)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final Schema SCHEMA_OPTIONAL_STRUCT = Schema.memberBuilder("optionalStruct", NestedStruct.SCHEMA)
        .id(ID)
        .build();

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_STRUCT,
            SCHEMA_OPTIONAL_STRUCT
        )
        .build();

    private transient final NestedStruct requiredStruct;
    private transient final NestedStruct optionalStruct;

    private StructureMembersInput(Builder builder) {
        this.requiredStruct = builder.requiredStruct;
        this.optionalStruct = builder.optionalStruct;
    }

    public NestedStruct requiredStruct() {
        return requiredStruct;
    }

    public NestedStruct optionalStruct() {
        return optionalStruct;
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
        StructureMembersInput that = (StructureMembersInput) other;
        return Objects.equals(this.requiredStruct, that.requiredStruct)
               && Objects.equals(this.optionalStruct, that.optionalStruct);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredStruct, optionalStruct);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA_REQUIRED_STRUCT, requiredStruct);

        if (optionalStruct != null) {
            serializer.writeStruct(SCHEMA_OPTIONAL_STRUCT, optionalStruct);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link StructureMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<StructureMembersInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private NestedStruct requiredStruct;
        private NestedStruct optionalStruct;

        private Builder() {}

        public Builder requiredStruct(NestedStruct requiredStruct) {
            this.requiredStruct = Objects.requireNonNull(requiredStruct, "requiredStruct cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_STRUCT);
            return this;
        }

        public Builder optionalStruct(NestedStruct optionalStruct) {
            this.optionalStruct = optionalStruct;
            return this;
        }

        @Override
        public StructureMembersInput build() {
            tracker.validate();
            return new StructureMembersInput(this);
        }

        @Override
        public ShapeBuilder<StructureMembersInput> errorCorrection() {
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
                    case 0 -> builder.requiredStruct(NestedStruct.builder().deserialize(de).build());
                    case 1 -> builder.optionalStruct(NestedStruct.builder().deserialize(de).build());
                }
            }
        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredStruct(this.requiredStruct);
        builder.optionalStruct(this.optionalStruct);
        return builder;
    }

}

