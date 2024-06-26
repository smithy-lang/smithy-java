

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class BuilderShape implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.naming#Builder");

    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .build();

    private BuilderShape(Builder builder) {
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
        return other != null && getClass() == other.getClass();
    }

    @Override
    public int hashCode() {
        return Objects.hash();
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link BuilderShape}.
     */
    public static final class Builder implements ShapeBuilder<BuilderShape> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        @Override
        public BuilderShape build() {
            return new BuilderShape(this);
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

            }

        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        return builder;
    }

}

