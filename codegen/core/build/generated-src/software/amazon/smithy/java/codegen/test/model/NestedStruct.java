

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class NestedStruct implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.common#NestedStruct");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("fieldA", PreludeSchemas.STRING)
        .putMember("fieldB", PreludeSchemas.INTEGER)
        .build();

    private static final Schema SCHEMA_FIELD_A = SCHEMA.member("fieldA");
    private static final Schema SCHEMA_FIELD_B = SCHEMA.member("fieldB");

    private transient final String fieldA;
    private transient final Integer fieldB;

    private NestedStruct(Builder builder) {
        this.fieldA = builder.fieldA;
        this.fieldB = builder.fieldB;
    }

    public String fieldA() {
        return fieldA;
    }

    public Integer fieldB() {
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
        NestedStruct that = (NestedStruct) other;
        return Objects.equals(this.fieldA, that.fieldA)
               && Objects.equals(this.fieldB, that.fieldB);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fieldA, fieldB);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
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
     * Builder for {@link NestedStruct}.
     */
    public static final class Builder implements ShapeBuilder<NestedStruct> {
        private String fieldA;
        private Integer fieldB;

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
        public NestedStruct build() {
            return new NestedStruct(this);
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
                    case 0 -> builder.fieldA(de.readString(member));
                    case 1 -> builder.fieldB(de.readInteger(member));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.fieldA(this.fieldA);
        builder.fieldB(this.fieldB);
        return builder;
    }

}

