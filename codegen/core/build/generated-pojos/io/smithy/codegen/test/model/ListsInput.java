

package io.smithy.codegen.test.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
public final class ListsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#ListsInput");

    private static final SdkSchema SCHEMA_REQUIRED_LIST = SdkSchema.memberBuilder("requiredList", SharedSchemas.LIST_OF_STRINGS)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_LIST_WITH_DEFAULT = SdkSchema.memberBuilder("listWithDefault", SharedSchemas.LIST_OF_STRINGS)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                ArrayNode.builder()
                    .build()
            )
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_LIST = SdkSchema.memberBuilder("optionalList", SharedSchemas.LIST_OF_STRINGS)
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

    private transient final List<String> requiredList;
    private transient final List<String> listWithDefault;
    private transient final List<String> optionalList;

    private ListsInput(Builder builder) {
        this.requiredList = builder.requiredList;
        this.listWithDefault = builder.listWithDefault;
        this.optionalList = builder.optionalList;
    }

    /**
     * Required list with no default value
     */
    public List<String> requiredList() {

        return Collections.unmodifiableList(requiredList);
    }

    public boolean hasRequiredList() {
        return true;
    }

    /**
     * List with a default value. Lists can only ever have empty defaults.
     */
    public List<String> listWithDefault() {

        return Collections.unmodifiableList(listWithDefault);
    }

    public boolean hasListWithDefault() {
        return true;
    }

    /**
     * Optional list
     */
    public List<String> optionalList() {
        if (optionalList == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(optionalList);
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
        ListsInput that = (ListsInput) other;
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
        serializer.writeList(SCHEMA_REQUIRED_LIST, requiredList, SharedSerde.ListOfStringsSerializer.INSTANCE);

        serializer.writeList(SCHEMA_LIST_WITH_DEFAULT, listWithDefault, SharedSerde.ListOfStringsSerializer.INSTANCE);

        if (optionalList != null) {
            serializer.writeList(SCHEMA_OPTIONAL_LIST, optionalList, SharedSerde.ListOfStringsSerializer.INSTANCE);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ListsInput}.
     */
    public static final class Builder implements SdkShapeBuilder<ListsInput> {
        private List<String> requiredList;
        private List<String> listWithDefault = Collections.emptyList();
        private List<String> optionalList;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder requiredList(List<String> requiredList) {
            this.requiredList = Objects.requireNonNull(requiredList, "requiredList cannot be null");
            tracker.setMember(SCHEMA_REQUIRED_LIST);
            return this;
        }

        public Builder listWithDefault(List<String> listWithDefault) {
            this.listWithDefault = Objects.requireNonNull(listWithDefault, "listWithDefault cannot be null");
            return this;
        }

        public Builder optionalList(List<String> optionalList) {
            this.optionalList = optionalList;
            return this;
        }

        @Override
        public ListsInput build() {
            tracker.validate();
            return new ListsInput(this);
        }

        @Override
        public SdkShapeBuilder<ListsInput> errorCorrection() {
            if (tracker.allSet()) {
                return this;
            }

            if (!tracker.checkMember(SCHEMA_REQUIRED_LIST)) {
                requiredList(Collections.emptyList());
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
                    case 0 -> builder.requiredList(SharedSerde.deserializeListOfStrings(member, de));
                    case 1 -> builder.listWithDefault(SharedSerde.deserializeListOfStrings(member, de));
                    case 2 -> builder.optionalList(SharedSerde.deserializeListOfStrings(member, de));
                }
            }
        }
    }
}

