

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
public final class RecursiveStructsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.recursion#RecursiveStructsInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("recursiveStructs", RecursiveStructA.SCHEMA_BUILDER)
        .build();

    private static final Schema SCHEMA_RECURSIVE_STRUCTS = SCHEMA.member("recursiveStructs");

    private transient final RecursiveStructA recursiveStructs;

    private RecursiveStructsInput(Builder builder) {
        this.recursiveStructs = builder.recursiveStructs;
    }

    public RecursiveStructA recursiveStructs() {
        return recursiveStructs;
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
        RecursiveStructsInput that = (RecursiveStructsInput) other;
        return Objects.equals(this.recursiveStructs, that.recursiveStructs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recursiveStructs);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (recursiveStructs != null) {
            serializer.writeStruct(SCHEMA_RECURSIVE_STRUCTS, recursiveStructs);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link RecursiveStructsInput}.
     */
    public static final class Builder implements ShapeBuilder<RecursiveStructsInput> {
        private RecursiveStructA recursiveStructs;

        private Builder() {}

        public Builder recursiveStructs(RecursiveStructA recursiveStructs) {
            this.recursiveStructs = recursiveStructs;
            return this;
        }

        @Override
        public RecursiveStructsInput build() {
            return new RecursiveStructsInput(this);
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
                    case 0 -> builder.recursiveStructs(RecursiveStructA.builder().deserialize(de).build());
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.recursiveStructs(this.recursiveStructs);
        return builder;
    }

}

