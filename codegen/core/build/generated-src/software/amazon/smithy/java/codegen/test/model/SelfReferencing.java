

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SchemaBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class SelfReferencing implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.recursion#SelfReferencing");

    static final SchemaBuilder SCHEMA_BUILDER = Schema.structureBuilder(ID);
    static final Schema SCHEMA = SCHEMA_BUILDER
        .putMember("self", SelfReferencing.SCHEMA_BUILDER)
        .build();

    private static final Schema SCHEMA_SELF = SCHEMA.member("self");

    private transient final SelfReferencing self;

    private SelfReferencing(Builder builder) {
        this.self = builder.self;
    }

    public SelfReferencing self() {
        return self;
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
        SelfReferencing that = (SelfReferencing) other;
        return Objects.equals(this.self, that.self);
    }

    @Override
    public int hashCode() {
        return Objects.hash(self);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (self != null) {
            serializer.writeStruct(SCHEMA_SELF, self);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link SelfReferencing}.
     */
    public static final class Builder implements ShapeBuilder<SelfReferencing> {
        private SelfReferencing self;

        private Builder() {}

        public Builder self(SelfReferencing self) {
            this.self = self;
            return this;
        }

        @Override
        public SelfReferencing build() {
            return new SelfReferencing(this);
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
                    case 0 -> builder.self(SelfReferencing.builder().deserialize(de).build());
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.self(this.self);
        return builder;
    }

}

