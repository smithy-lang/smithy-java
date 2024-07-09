

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
public final class RecursiveStructB implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.recursion#RecursiveStructB");

    static final SchemaBuilder SCHEMA_BUILDER = Schema.structureBuilder(ID);
    static final Schema SCHEMA = SCHEMA_BUILDER
        .putMember("a", RecursiveStructA.SCHEMA_BUILDER)
        .build();

    private static final Schema SCHEMA_A = SCHEMA.member("a");

    private transient final RecursiveStructA a;

    private RecursiveStructB(Builder builder) {
        this.a = builder.a;
    }

    public RecursiveStructA a() {
        return a;
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
        RecursiveStructB that = (RecursiveStructB) other;
        return Objects.equals(this.a, that.a);
    }

    @Override
    public int hashCode() {
        return Objects.hash(a);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (a != null) {
            serializer.writeStruct(SCHEMA_A, a);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link RecursiveStructB}.
     */
    public static final class Builder implements ShapeBuilder<RecursiveStructB> {
        private RecursiveStructA a;

        private Builder() {}

        public Builder a(RecursiveStructA a) {
            this.a = a;
            return this;
        }

        @Override
        public RecursiveStructB build() {
            return new RecursiveStructB(this);
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
                    case 0 -> builder.a(RecursiveStructA.builder().deserialize(de).build());
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.a(this.a);
        return builder;
    }

}

