

package io.smithy.codegen.test.model;

import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class StringsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#StringsInput");

    private static final SdkSchema SCHEMA_REQUIRED_STRING = SdkSchema.memberBuilder("requiredString", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_STRING = SdkSchema.memberBuilder("defaultString", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from("default")
            )
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_STRING = SdkSchema.memberBuilder("optionalString", PreludeSchemas.STRING)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_STRING,
            SCHEMA_DEFAULT_STRING,
            SCHEMA_OPTIONAL_STRING
        )
        .build();

    private transient final String requiredString;
    private transient final String defaultString;
    private transient final String optionalString;

    private StringsInput(Builder builder) {
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
        StringsInput that = (StringsInput) other;
        return Objects.equals(requiredString, that.requiredString)
               && Objects.equals(defaultString, that.defaultString)
               && Objects.equals(optionalString, that.optionalString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredString, defaultString, optionalString);
    }

    @Override
    public SdkSchema schema() {
        return SCHEMA;
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
     * Builder for {@link StringsInput}.
     */
    public static final class Builder implements SdkShapeBuilder<StringsInput> {
        private static final String DEFAULT_STRING_DEFAULT = "default";
        private String requiredString;
        private String defaultString = DEFAULT_STRING_DEFAULT;
        private String optionalString;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

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
        public StringsInput build() {
            tracker.validate();
            return new StringsInput(this);
        }

        @Override
        public SdkShapeBuilder<StringsInput> errorCorrection() {
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
            public void accept(Builder builder, SdkSchema member, ShapeDeserializer de) {
                switch (member.memberIndex()) {
                    case 0 -> builder.requiredString(de.readString(member));
                    case 1 -> builder.defaultString(de.readString(member));
                    case 2 -> builder.optionalString(de.readString(member));
                }
            }
        }
    }
}

