

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
public final class RecursiveStructA implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.recursion#RecursiveStructA");

    static final SchemaBuilder SCHEMA_BUILDER = Schema.structureBuilder(ID);
    static final Schema SCHEMA = SCHEMA_BUILDER
        .putMember("b", RecursiveStructB.SCHEMA_BUILDER)
        .build();

    private static final Schema SCHEMA_B = SCHEMA.member("b");

    private transient final RecursiveStructB b;

    private RecursiveStructA(Builder builder) {
        this.b = builder.b;
    }

    public RecursiveStructB b() {
        return b;
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
        RecursiveStructA that = (RecursiveStructA) other;
        return Objects.equals(this.b, that.b);
    }

    @Override
    public int hashCode() {
        return Objects.hash(b);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (b != null) {
            serializer.writeStruct(SCHEMA_B, b);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link RecursiveStructA}.
     */
    public static final class Builder implements ShapeBuilder<RecursiveStructA> {
        private RecursiveStructB b;

        private Builder() {}

        public Builder b(RecursiveStructB b) {
            this.b = b;
            return this;
        }

        @Override
        public RecursiveStructA build() {
            return new RecursiveStructA(this);
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
                    case 0 -> builder.b(RecursiveStructB.builder().deserialize(de).build());
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.b(this.b);
        return builder;
    }

}

