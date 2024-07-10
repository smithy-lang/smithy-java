

package software.amazon.smithy.java.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class StringMembersInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures#StringMembersInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("requiredString", PreludeSchemas.STRING,
            new RequiredTrait())
        .putMember("defaultString", PreludeSchemas.STRING,
            new DefaultTrait(Node.from("default")))
        .putMember("optionalString", PreludeSchemas.STRING)
        .build();

    private static final Schema SCHEMA_REQUIRED_STRING = SCHEMA.member("requiredString");
    private static final Schema SCHEMA_DEFAULT_STRING = SCHEMA.member("defaultString");
    private static final Schema SCHEMA_OPTIONAL_STRING = SCHEMA.member("optionalString");

    private transient final String requiredString;
    private transient final String defaultString;
    private transient final String optionalString;

    private StringMembersInput(Builder builder) {
        this.requiredString = builder.requiredString;
        this.defaultString = builder.defaultString;
        this.optionalString = builder.optionalString;
    }

    public String requiredString() {
        return requiredString;
    }

    public String defaultString() {
        return defaultString;
    }

    public String optionalString() {
        return optionalString;
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
        StringMembersInput that = (StringMembersInput) other;
        return Objects.equals(this.requiredString, that.requiredString)
               && Objects.equals(this.defaultString, that.defaultString)
               && Objects.equals(this.optionalString, that.optionalString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredString, defaultString, optionalString);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_REQUIRED_STRING, requiredString);
        serializer.writeString(SCHEMA_DEFAULT_STRING, defaultString);
        if (optionalString != null) {
            serializer.writeString(SCHEMA_OPTIONAL_STRING, optionalString);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link StringMembersInput}.
     */
    public static final class Builder implements ShapeBuilder<StringMembersInput> {
        private static final String DEFAULT_STRING_DEFAULT = "default";
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private String requiredString;
        private String defaultString = DEFAULT_STRING_DEFAULT;
        private String optionalString;

        private Builder() {}

        public Builder requiredString(String requiredString) {
            this.requiredString = Objects.requireNonNull(requiredString, "requiredString cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_STRING);
            return this;
        }

        public Builder defaultString(String defaultString) {
            this.defaultString = Objects.requireNonNull(defaultString, "defaultString cannot be null");
            return this;
        }

        public Builder optionalString(String optionalString) {
            this.optionalString = optionalString;
            return this;
        }

        @Override
        public StringMembersInput build() {
            tracker.validate();
            return new StringMembersInput(this);
        }

        @Override
        public ShapeBuilder<StringMembersInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }
            if (!tracker.checkMember(SCHEMA_REQUIRED_STRING)) {
                requiredString("");
            }
            return this;
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
                    case 0 -> builder.requiredString(de.readString(member));
                    case 1 -> builder.defaultString(de.readString(member));
                    case 2 -> builder.optionalString(de.readString(member));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredString(this.requiredString);
        builder.defaultString(this.defaultString);
        builder.optionalString(this.optionalString);
        return builder;
    }

}

