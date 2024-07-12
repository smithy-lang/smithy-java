/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.model;

import edu.umd.cs.findbugs.annotations.NonNull;
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
public final class PutPersonImageOutput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonImageOutput");

    static final Schema SCHEMA = Schema.structureBuilder(ID).build();

    private PutPersonImageOutput(Builder builder) {
    }

    @Override
    public @NonNull String toString() {
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
    public void serialize(@NonNull ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(@NonNull ShapeSerializer serializer) {

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link PutPersonImageOutput}.
     */
    public static final class Builder implements ShapeBuilder<PutPersonImageOutput> {

        private Builder() {}

        @Override
        public @NonNull PutPersonImageOutput build() {
            return new PutPersonImageOutput(this);
        }

        @Override
        public Builder deserialize(@NonNull ShapeDeserializer decoder) {
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

