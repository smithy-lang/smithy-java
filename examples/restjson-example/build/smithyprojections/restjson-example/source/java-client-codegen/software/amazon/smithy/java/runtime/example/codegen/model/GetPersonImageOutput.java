/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.codegen.model;

import java.util.Objects;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.DataStream;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpPayloadTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class GetPersonImageOutput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#GetPersonImageOutput");

    private static final SdkSchema SCHEMA_NAME = SdkSchema.memberBuilder("name", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new HttpHeaderTrait("Person-Name"),
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_IMAGE = SdkSchema.memberBuilder("image", SharedSchemas.STREAM)
        .id(ID)
        .traits(
            new HttpPayloadTrait(),
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.nullNode()
            )
        )
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_NAME,
            SCHEMA_IMAGE
        )
        .build();

    private final String name;
    private final DataStream image;

    private GetPersonImageOutput(Builder builder) {
        this.name = SmithyBuilder.requiredState("name", builder.name);
        this.image = builder.image;
    }

    public String name() {
        return name;
    }

    public DataStream image() {
        return image;
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
        GetPersonImageOutput that = (GetPersonImageOutput) other;
        return Objects.equals(name, that.name)
               && Objects.equals(image, that.image);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, image);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this, InnerSerializer.INSTANCE);
    }

    private static final class InnerSerializer implements BiConsumer<GetPersonImageOutput, ShapeSerializer> {
        private static final InnerSerializer INSTANCE = new InnerSerializer();

        @Override
        public void accept(GetPersonImageOutput shape, ShapeSerializer serializer) {
            serializer.writeString(SCHEMA_NAME, shape.name);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link GetPersonImageOutput}.
     */
    public static final class Builder implements SdkShapeBuilder<GetPersonImageOutput> {
        private String name;
        private DataStream image = DataStream.ofEmpty();

        private static final InnerDeserializer INNER_DESERIALIZER = new InnerDeserializer();

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder image(DataStream image) {
            this.image = image;
            return this;
        }

        @Override
        public void setDataStream(DataStream stream) {
            image(stream);
        }

        @Override
        public GetPersonImageOutput build() {
            return new GetPersonImageOutput(this);
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
                switch (member.memberIndex()) {
                    case 0 -> builder.name(de.readString(member));
                }
            }
        }
    }
}

