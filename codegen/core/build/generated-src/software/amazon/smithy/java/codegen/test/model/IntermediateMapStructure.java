

package software.amazon.smithy.java.codegen.test.model;

import java.util.Collections;
import java.util.Map;
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
public final class IntermediateMapStructure implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.recursion#IntermediateMapStructure");

    static final SchemaBuilder SCHEMA_BUILDER = Schema.structureBuilder(ID);
    static final Schema SCHEMA = SCHEMA_BUILDER
        .putMember("foo", SharedSchemas.RECURSIVE_MAP_BUILDER)
        .build();

    private static final Schema SCHEMA_FOO = SCHEMA.member("foo");

    private transient final Map<String, IntermediateMapStructure> foo;

    private IntermediateMapStructure(Builder builder) {
        this.foo = builder.foo == null ? null : Collections.unmodifiableMap(builder.foo);
    }

    public Map<String, IntermediateMapStructure> foo() {
        if (foo == null) {
            return Collections.emptyMap();
        }
        return foo;
    }

    public boolean hasFoo() {
        return foo != null;
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
        IntermediateMapStructure that = (IntermediateMapStructure) other;
        return Objects.equals(this.foo, that.foo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(foo);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (foo != null) {
            serializer.writeMap(SCHEMA_FOO, foo, SharedSerde.RecursiveMapSerializer.INSTANCE);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link IntermediateMapStructure}.
     */
    public static final class Builder implements ShapeBuilder<IntermediateMapStructure> {
        private Map<String, IntermediateMapStructure> foo;

        private Builder() {}

        public Builder foo(Map<String, IntermediateMapStructure> foo) {
            this.foo = foo;
            return this;
        }

        @Override
        public IntermediateMapStructure build() {
            return new IntermediateMapStructure(this);
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
                    case 0 -> builder.foo(SharedSerde.deserializeRecursiveMap(member, de));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.foo(this.foo);
        return builder;
    }

}

