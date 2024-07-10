

package software.amazon.smithy.java.codegen.test.model;

import java.util.Collections;
import java.util.List;
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
public final class RecursiveListsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.recursion#RecursiveListsInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("recursiveList", SharedSchemas.RECURSIVE_LIST_BUILDER)
        .build();

    private static final Schema SCHEMA_RECURSIVE_LIST = SCHEMA.member("recursiveList");

    private transient final List<IntermediateListStructure> recursiveList;

    private RecursiveListsInput(Builder builder) {
        this.recursiveList = builder.recursiveList == null ? null : Collections.unmodifiableList(builder.recursiveList);
    }

    public List<IntermediateListStructure> recursiveList() {
        if (recursiveList == null) {
            return Collections.emptyList();
        }
        return recursiveList;
    }

    public boolean hasRecursiveList() {
        return recursiveList != null;
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
        RecursiveListsInput that = (RecursiveListsInput) other;
        return Objects.equals(this.recursiveList, that.recursiveList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recursiveList);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (recursiveList != null) {
            serializer.writeList(SCHEMA_RECURSIVE_LIST, recursiveList, SharedSerde.RecursiveListSerializer.INSTANCE);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link RecursiveListsInput}.
     */
    public static final class Builder implements ShapeBuilder<RecursiveListsInput> {
        private List<IntermediateListStructure> recursiveList;

        private Builder() {}

        public Builder recursiveList(List<IntermediateListStructure> recursiveList) {
            this.recursiveList = recursiveList;
            return this;
        }

        @Override
        public RecursiveListsInput build() {
            return new RecursiveListsInput(this);
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
                    case 0 -> builder.recursiveList(SharedSerde.deserializeRecursiveList(member, de));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.recursiveList(this.recursiveList);
        return builder;
    }

}

