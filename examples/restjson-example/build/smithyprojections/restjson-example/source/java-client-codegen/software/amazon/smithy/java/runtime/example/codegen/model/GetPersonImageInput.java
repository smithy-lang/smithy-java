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
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class GetPersonImageInput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#GetPersonImageInput");

    private static final SdkSchema SCHEMA_NAME = SdkSchema.memberBuilder("name", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new HttpLabelTrait(),
            new RequiredTrait()
        )
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_NAME
        )
        .build();

    private final String name;

    private GetPersonImageInput(Builder builder) {
        this.name = SmithyBuilder.requiredState("name", builder.name);
    }

    public String name() {
        return name;
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
        GetPersonImageInput that = (GetPersonImageInput) other;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this, InnerSerializer.INSTANCE);
    }

    private static final class InnerSerializer implements BiConsumer<GetPersonImageInput, ShapeSerializer> {
        private static final InnerSerializer INSTANCE = new InnerSerializer();

        @Override
        public void accept(GetPersonImageInput shape, ShapeSerializer serializer) {
            serializer.writeString(SCHEMA_NAME, shape.name);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link GetPersonImageInput}.
     */
    public static final class Builder implements SdkShapeBuilder<GetPersonImageInput> {
        private String name;

        private static final InnerDeserializer INNER_DESERIALIZER = new InnerDeserializer();

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        @Override
        public GetPersonImageInput build() {
            return new GetPersonImageInput(this);
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

