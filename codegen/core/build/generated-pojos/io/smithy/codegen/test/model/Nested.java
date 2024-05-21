

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
public final class Nested implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#Nested");

    private static final SdkSchema SCHEMA_FIELD_A = SdkSchema.memberBuilder("fieldA", PreludeSchemas.STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_FIELD_B = SdkSchema.memberBuilder("fieldB", PreludeSchemas.INTEGER)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_FIELD_A,
            SCHEMA_FIELD_B
        )
        .build();

    private transient final String fieldA;
    private transient final Integer fieldB;

    private Nested(Builder builder) {
        this.fieldA = builder.fieldA;
        this.fieldB = builder.fieldB;
    }

    public String fieldA() {
        return fieldA;
    }

    public int fieldB() {
        return fieldB;
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
        Nested that = (Nested) other;
        return Objects.equals(fieldA, that.fieldA)
               && Objects.equals(fieldB, that.fieldB);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldA, fieldB);
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

        if (fieldB != null) {
            serializer.writeInteger(SCHEMA_FIELD_B, fieldB);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link Nested}.
     */
    public static final class Builder implements SdkShapeBuilder<Nested> {
        private String fieldA;
        private Integer fieldB;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder fieldA(String fieldA) {
            this.fieldA = fieldA;
            return this;
        }

        public Builder fieldB(int fieldB) {
            this.fieldB = fieldB;
            return this;
        }

        @Override
        public Nested build() {
            tracker.validate();
            return new Nested(this);
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
                    case 1 -> builder.fieldB(de.readInteger(member));
                }
            }
        }
    }
}

