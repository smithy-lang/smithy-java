

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
public final class UnionAllTypesInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.unions#UnionAllTypesInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("union", UnionType.SCHEMA)
        .build();

    private static final Schema SCHEMA_UNION = SCHEMA.member("union");

    private transient final UnionType union;

    private UnionAllTypesInput(Builder builder) {
        this.union = builder.union;
    }

    public UnionType union() {
        return union;
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
        UnionAllTypesInput that = (UnionAllTypesInput) other;
        return Objects.equals(this.union, that.union);
    }

    @Override
    public int hashCode() {
        return Objects.hash(union);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (union != null) {
            serializer.writeStruct(SCHEMA_UNION, union);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link UnionAllTypesInput}.
     */
    public static final class Builder implements ShapeBuilder<UnionAllTypesInput> {
        private UnionType union;

        private Builder() {}

        public Builder union(UnionType union) {
            this.union = union;
            return this;
        }

        @Override
        public UnionAllTypesInput build() {
            return new UnionAllTypesInput(this);
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
                    case 0 -> builder.union(UnionType.builder().deserialize(de).build());
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.union(this.union);
        return builder;
    }

}

