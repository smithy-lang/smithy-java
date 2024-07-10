

package software.amazon.smithy.java.codegen.test.model;

import java.util.Collections;
import java.util.Map;
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
public final class RecursiveMapsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.recursion#RecursiveMapsInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("recursiveMap", SharedSchemas.RECURSIVE_MAP_BUILDER)
        .build();

    private static final Schema SCHEMA_RECURSIVE_MAP = SCHEMA.member("recursiveMap");

    private transient final Map<String, IntermediateMapStructure> recursiveMap;

    private RecursiveMapsInput(Builder builder) {
        this.recursiveMap = builder.recursiveMap == null ? null : Collections.unmodifiableMap(builder.recursiveMap);
    }

    public Map<String, IntermediateMapStructure> recursiveMap() {
        if (recursiveMap == null) {
            return Collections.emptyMap();
        }
        return recursiveMap;
    }

    public boolean hasRecursiveMap() {
        return recursiveMap != null;
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
        RecursiveMapsInput that = (RecursiveMapsInput) other;
        return Objects.equals(this.recursiveMap, that.recursiveMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recursiveMap);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (recursiveMap != null) {
            serializer.writeMap(SCHEMA_RECURSIVE_MAP, recursiveMap, SharedSerde.RecursiveMapSerializer.INSTANCE);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link RecursiveMapsInput}.
     */
    public static final class Builder implements ShapeBuilder<RecursiveMapsInput> {
        private Map<String, IntermediateMapStructure> recursiveMap;

        private Builder() {}

        public Builder recursiveMap(Map<String, IntermediateMapStructure> recursiveMap) {
            this.recursiveMap = recursiveMap;
            return this;
        }

        @Override
        public RecursiveMapsInput build() {
            return new RecursiveMapsInput(this);
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
                    case 0 -> builder.recursiveMap(SharedSerde.deserializeRecursiveMap(member, de));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.recursiveMap(this.recursiveMap);
        return builder;
    }

}

