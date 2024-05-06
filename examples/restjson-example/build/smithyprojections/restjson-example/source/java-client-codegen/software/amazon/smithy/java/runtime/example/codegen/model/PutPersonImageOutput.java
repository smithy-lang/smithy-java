/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.codegen.model;

import java.util.Objects;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class PutPersonImageOutput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonImageOutput");

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .build();

    private PutPersonImageOutput(Builder builder) {
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
        serializer.writeStruct(SCHEMA, this, InnerSerializer.INSTANCE);
    }

    private static final class InnerSerializer implements BiConsumer<PutPersonImageOutput, ShapeSerializer> {
        private static final InnerSerializer INSTANCE = new InnerSerializer();

        @Override
        public void accept(PutPersonImageOutput shape, ShapeSerializer serializer) {

        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link PutPersonImageOutput}.
     */
    public static final class Builder implements SdkShapeBuilder<PutPersonImageOutput> {

        private static final InnerDeserializer INNER_DESERIALIZER = new InnerDeserializer();

        private Builder() {}

        @Override
        public PutPersonImageOutput build() {
            return new PutPersonImageOutput(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, InnerDeserializer.INSTANCE);
            return this;
        }

        private static final class InnerDeserializer implements ShapeDeserializer.StructMemberConsumer<Builder> {
            private static final InnerDeserializer INSTANCE = new InnerDeserializer();
            @Override
            public void accept(Builder builder, SdkSchema member, ShapeDeserializer de) {

            }
        }
    }
}

