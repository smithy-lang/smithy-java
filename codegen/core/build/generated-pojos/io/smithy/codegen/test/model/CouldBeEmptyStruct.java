

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
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class CouldBeEmptyStruct implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#CouldBeEmptyStruct");

    private static final SdkSchema SCHEMA_FIELD_A = SdkSchema.memberBuilder("fieldA", PreludeSchemas.STRING)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_FIELD_A
        )
        .build();

    private transient final String fieldA;

    private CouldBeEmptyStruct(Builder builder) {
        this.fieldA = builder.fieldA;
    }

    public String fieldA() {
        return fieldA;
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
        CouldBeEmptyStruct that = (CouldBeEmptyStruct) other;
        return Objects.equals(fieldA, that.fieldA);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldA);
    }

    @Override
    public SdkSchema schema() {
        return SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (fieldA != null) {
            serializer.writeString(SCHEMA_FIELD_A, fieldA);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link CouldBeEmptyStruct}.
     */
    public static final class Builder implements SdkShapeBuilder<CouldBeEmptyStruct> {
        private String fieldA;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder fieldA(String fieldA) {
            this.fieldA = fieldA;
            return this;
        }

        @Override
        public CouldBeEmptyStruct build() {
            tracker.validate();
            return new CouldBeEmptyStruct(this);
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
                    case 0 -> builder.fieldA(de.readString(member));
                }
            }
        }
    }
}

