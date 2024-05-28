

package io.smithy.codegen.test.model;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class SetsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#SetsInput");

    private static final SdkSchema SCHEMA_REQUIRED_LIST = SdkSchema.memberBuilder("requiredList", SharedSchemas.SET_OF_STRINGS)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_LIST_WITH_DEFAULT = SdkSchema.memberBuilder("listWithDefault", SharedSchemas.SET_OF_STRINGS)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                ArrayNode.builder()
                    .build()
            )
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_LIST = SdkSchema.memberBuilder("optionalList", SharedSchemas.SET_OF_STRINGS)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_REQUIRED_LIST,
            SCHEMA_LIST_WITH_DEFAULT,
            SCHEMA_OPTIONAL_LIST
        )
        .build();

    private transient final Set<String> requiredList;
    private transient final Set<String> listWithDefault;
    private transient final Set<String> optionalList;

    private SetsInput(Builder builder) {
        this.requiredList = builder.requiredList;
        this.listWithDefault = builder.listWithDefault;
        this.optionalList = builder.optionalList;
    }

    /**
     * Required set with no default value
     */
    public Set<String> requiredList() {
        return Collections.unmodifiableSet(requiredList);
    }

    public boolean hasRequiredList() {
        return true;
    }

    /**
     * Set with a default value. Sets can only ever have empty defaults.
     */
    public Set<String> listWithDefault() {
        return Collections.unmodifiableSet(listWithDefault);
    }

    public boolean hasListWithDefault() {
        return true;
    }

    /**
     * Optional set
     */
    public Set<String> optionalList() {
        if (optionalList == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(optionalList);
    }

    public boolean hasOptionalList() {
        return optionalList != null;
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
        SetsInput that = (SetsInput) other;
        return Objects.equals(requiredList, that.requiredList)
               && Objects.equals(listWithDefault, that.listWithDefault)
               && Objects.equals(optionalList, that.optionalList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requiredList, listWithDefault, optionalList);
    }

    @Override
    public SdkSchema schema() {
        return SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeList(SCHEMA_REQUIRED_LIST, requiredList, SharedSerde.SetOfStringsSerializer.INSTANCE);

        serializer.writeList(SCHEMA_LIST_WITH_DEFAULT, listWithDefault, SharedSerde.SetOfStringsSerializer.INSTANCE);

        if (optionalList != null) {
            serializer.writeList(SCHEMA_OPTIONAL_LIST, optionalList, SharedSerde.SetOfStringsSerializer.INSTANCE);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link SetsInput}.
     */
    public static final class Builder implements SdkShapeBuilder<SetsInput> {
        private Set<String> requiredList;
        private Set<String> listWithDefault = Collections.emptySet();
        private Set<String> optionalList;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder requiredList(Set<String> requiredList) {
            this.requiredList = Objects.requireNonNull(requiredList, "requiredList cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_LIST);
            return this;
        }

        public Builder listWithDefault(Set<String> listWithDefault) {
            this.listWithDefault = Objects.requireNonNull(listWithDefault, "listWithDefault cannot be null");
            return this;
        }

        public Builder optionalList(Set<String> optionalList) {
            this.optionalList = optionalList;
            return this;
        }

        @Override
        public SetsInput build() {
            tracker.validate();
            return new SetsInput(this);
        }

        @Override
        public SdkShapeBuilder<SetsInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }

            if (!tracker.checkMember(SCHEMA_REQUIRED_LIST)) {
                requiredList(Collections.emptySet());
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
                    case 0 -> builder.requiredList(SharedSerde.deserializeSetOfStrings(member, de));
                    case 1 -> builder.listWithDefault(SharedSerde.deserializeSetOfStrings(member, de));
                    case 2 -> builder.optionalList(SharedSerde.deserializeSetOfStrings(member, de));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.requiredList(this.requiredList);
        builder.listWithDefault(this.listWithDefault);
        builder.optionalList(this.optionalList);
        return builder;
    }

}

