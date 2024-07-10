

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class SelfReferenceInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.recursion#SelfReferenceInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("selfReferentialShape", SelfReferencing.SCHEMA_BUILDER)
        .build();

    private static final Schema SCHEMA_SELF_REFERENTIAL_SHAPE = SCHEMA.member("selfReferentialShape");

    private transient final SelfReferencing selfReferentialShape;

    private SelfReferenceInput(Builder builder) {
        this.selfReferentialShape = builder.selfReferentialShape;
    }

    public SelfReferencing selfReferentialShape() {
        return selfReferentialShape;
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
        SelfReferenceInput that = (SelfReferenceInput) other;
        return Objects.equals(this.selfReferentialShape, that.selfReferentialShape);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selfReferentialShape);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (selfReferentialShape != null) {
            serializer.writeStruct(SCHEMA_SELF_REFERENTIAL_SHAPE, selfReferentialShape);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link SelfReferenceInput}.
     */
    public static final class Builder implements ShapeBuilder<SelfReferenceInput> {
        private SelfReferencing selfReferentialShape;

        private Builder() {}

        public Builder selfReferentialShape(SelfReferencing selfReferentialShape) {
            this.selfReferentialShape = selfReferentialShape;
            return this;
        }

        @Override
        public SelfReferenceInput build() {
            return new SelfReferenceInput(this);
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
                    case 0 -> builder.selfReferentialShape(SelfReferencing.builder().deserialize(de).build());
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.selfReferentialShape(this.selfReferentialShape);
        return builder;
    }

}

