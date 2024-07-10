

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class NamingInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.naming#NamingInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("other", PreludeSchemas.STRING)
        .putMember("builder", BuilderShape.SCHEMA)
        .putMember("inner", InnerDeserializerShape.SCHEMA)
        .putMember("type", TypeShape.SCHEMA)
        .build();

    private static final Schema SCHEMA_OTHER = SCHEMA.member("other");
    private static final Schema SCHEMA_BUILDER_MEMBER = SCHEMA.member("builder");
    private static final Schema SCHEMA_INNER = SCHEMA.member("inner");
    private static final Schema SCHEMA_TYPE_MEMBER = SCHEMA.member("type");

    private transient final String other;
    private transient final BuilderShape builderMember;
    private transient final InnerDeserializerShape inner;
    private transient final TypeShape typeMember;

    private NamingInput(Builder builder) {
        this.other = builder.other;
        this.builderMember = builder.builderMember;
        this.inner = builder.inner;
        this.typeMember = builder.typeMember;
    }

    public String other() {
        return other;
    }

    public BuilderShape builderMember() {
        return builderMember;
    }

    public InnerDeserializerShape inner() {
        return inner;
    }

    public TypeShape typeMember() {
        return typeMember;
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
        NamingInput that = (NamingInput) other;
        return Objects.equals(this.other, that.other)
               && Objects.equals(this.builderMember, that.builderMember)
               && Objects.equals(this.inner, that.inner)
               && Objects.equals(this.typeMember, that.typeMember);
    }

    @Override
    public int hashCode() {
        return Objects.hash(other, builderMember, inner, typeMember);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (other != null) {
            serializer.writeString(SCHEMA_OTHER, other);
        }
        if (builderMember != null) {
            serializer.writeStruct(SCHEMA_BUILDER_MEMBER, builderMember);
        }
        if (inner != null) {
            serializer.writeStruct(SCHEMA_INNER, inner);
        }
        if (typeMember != null) {
            serializer.writeStruct(SCHEMA_TYPE_MEMBER, typeMember);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link NamingInput}.
     */
    public static final class Builder implements ShapeBuilder<NamingInput> {
        private String other;
        private BuilderShape builderMember;
        private InnerDeserializerShape inner;
        private TypeShape typeMember;

        private Builder() {}

        public Builder other(String other) {
            this.other = other;
            return this;
        }

        public Builder builderMember(BuilderShape builderMember) {
            this.builderMember = builderMember;
            return this;
        }

        public Builder inner(InnerDeserializerShape inner) {
            this.inner = inner;
            return this;
        }

        public Builder typeMember(TypeShape typeMember) {
            this.typeMember = typeMember;
            return this;
        }

        @Override
        public NamingInput build() {
            return new NamingInput(this);
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
                    case 0 -> builder.other(de.readString(member));
                    case 1 -> builder.builderMember(BuilderShape.builder().deserialize(de).build());
                    case 2 -> builder.inner(InnerDeserializerShape.builder().deserialize(de).build());
                    case 3 -> builder.typeMember(TypeShape.builder().deserialize(de).build());
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.other(this.other);
        builder.builderMember(this.builderMember);
        builder.inner(this.inner);
        builder.typeMember(this.typeMember);
        return builder;
    }

}

