

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
public final class StructuresInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#StructuresInput");

    private static final SdkSchema SCHEMA_REQUIRED_STRUCT = SdkSchema.memberBuilder("requiredStruct", Nested.SCHEMA)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_STRUCT = SdkSchema.memberBuilder("optionalStruct", Nested.SCHEMA)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_STRUCT,
            SCHEMA_OPTIONAL_STRUCT
        )
        .build();

    private transient final Nested requiredStruct;
    private transient final Nested optionalStruct;

    private StructuresInput(Builder builder) {
        this.requiredStruct = builder.requiredStruct;
        this.optionalStruct = builder.optionalStruct;
    }

    public Nested requiredStruct() {
        return requiredStruct;
    }

    public Nested optionalStruct() {
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
        StructuresInput that = (StructuresInput) other;
        return Objects.equals(requiredStruct, that.requiredStruct)
               && Objects.equals(optionalStruct, that.optionalStruct);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredStruct, optionalStruct);
    }

    @Override
    public SdkSchema schema() {
        return SCHEMA;
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
     * Builder for {@link StructuresInput}.
     */
    public static final class Builder implements SdkShapeBuilder<StructuresInput> {
        private Nested requiredStruct;
        private Nested optionalStruct;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder requiredStruct(Nested requiredStruct) {
            this.requiredStruct = Objects.requireNonNull(requiredStruct, "requiredStruct cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_STRUCT);
            return this;
        }

        public Builder optionalStruct(Nested optionalStruct) {
            this.optionalStruct = optionalStruct;
            return this;
        }

        @Override
        public StructuresInput build() {
            tracker.validate();
            return new StructuresInput(this);
        }

        @Override
        public SdkShapeBuilder<StructuresInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }

            if (!tracker.checkMember(SCHEMA_REQUIRED_STRUCT)) {
                requiredStruct(Nested.builder().build());
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
                    case 0 -> builder.requiredStruct(Nested.builder().deserialize(de).build());
                    case 1 -> builder.optionalStruct(Nested.builder().deserialize(de).build());
                }
            }
        }
    }
}

