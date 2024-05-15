

package io.smithy.codegen.test.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.PresenceTracker;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
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
public final class ListsInput implements SerializableShape {
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
        this.requiredList = Collections.unmodifiableList(builder.requiredList);
        this.listWithDefault = Collections.unmodifiableList(builder.listWithDefault);
        this.optionalList = builder.optionalList != null ? Collections.unmodifiableList(builder.optionalList) : null;
    }

    /**
     * Required list with no default value
     */
    public List<String> requiredList() {
        return requiredList;
    }

    public boolean hasRequiredList() {
        return requiredList != null;
    }

    /**
     * List with a default value. Lists can only ever have empty defaults.
     */
    public List<String> listWithDefault() {
        return listWithDefault;
    }

    public boolean hasListWithDefault() {
        return listWithDefault != null;
    }

    /**
     * Optional list
     */
    public List<String> optionalList() {
        return optionalList != null ? optionalList : Collections.emptyList();
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
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this, InnerSerializer.INSTANCE);
    }

    static final class InnerSerializer implements BiConsumer<ListsInput, ShapeSerializer> {
        static final InnerSerializer INSTANCE = new InnerSerializer();

        @Override
        public void accept(ListsInput shape, ShapeSerializer serializer) {
            serializer.writeList(SCHEMA_REQUIRED_LIST, shape.requiredList, SharedSchemas.ListOfStringsSerializer.INSTANCE);
            serializer.writeList(SCHEMA_LIST_WITH_DEFAULT, shape.listWithDefault, SharedSchemas.ListOfStringsSerializer.INSTANCE);
            if (shape.optionalList != null) {
                serializer.writeList(SCHEMA_OPTIONAL_LIST, shape.optionalList, SharedSchemas.ListOfStringsSerializer.INSTANCE);
            }
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
        private List<String> listWithDefault = new ArrayList<>();
        private List<String> optionalList;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder requiredList(Collection<String> requiredList) {
            this.requiredList = new ArrayList<>(Objects.requireNonNull(requiredList, "requiredList cannot be null"));
            tracker.setMember(SCHEMA_REQUIRED_LIST);
            return this;
        }

        public Builder addAllRequiredList(Collection<String> requiredList) {
            if (this.requiredList == null) {
                this.requiredList = new ArrayList<>(requiredList);
                tracker.setMember(SCHEMA_REQUIRED_LIST);
            } else {
                this.requiredList.addAll(requiredList);
            }
            return this;
        }

        public Builder requiredList(String requiredList) {
            if (this.requiredList == null) {
                this.requiredList = new ArrayList<>();
                tracker.setMember(SCHEMA_REQUIRED_LIST);
            }
            this.requiredList.add(requiredList);
            return this;
        }

        public Builder requiredList(String... requiredList) {
            if (this.requiredList == null) {
                this.requiredList = new ArrayList<>();
                tracker.setMember(SCHEMA_REQUIRED_LIST);
            }
            Collections.addAll(this.requiredList, requiredList);
            return this;
        }

        public Builder listWithDefault(Collection<String> listWithDefault) {
            this.listWithDefault = listWithDefault != null ? new ArrayList<>(listWithDefault) : null;
            return this;
        }

        public Builder addAllListWithDefault(Collection<String> listWithDefault) {
            if (this.listWithDefault == null) {
                this.listWithDefault = new ArrayList<>(listWithDefault);
            } else {
                this.listWithDefault.addAll(listWithDefault);
            }
            return this;
        }

        public Builder listWithDefault(String listWithDefault) {
            if (this.listWithDefault == null) {
                this.listWithDefault = new ArrayList<>();
            }
            this.listWithDefault.add(listWithDefault);
            return this;
        }

        public Builder listWithDefault(String... listWithDefault) {
            if (this.listWithDefault == null) {
                this.listWithDefault = new ArrayList<>();
            }
            Collections.addAll(this.listWithDefault, listWithDefault);
            return this;
        }

        public Builder optionalList(Collection<String> optionalList) {
            this.optionalList = optionalList != null ? new ArrayList<>(optionalList) : null;
            return this;
        }

        public Builder addAllOptionalList(Collection<String> optionalList) {
            if (this.optionalList == null) {
                this.optionalList = new ArrayList<>(optionalList);
            } else {
                this.optionalList.addAll(optionalList);
            }
            return this;
        }

        public Builder optionalList(String optionalList) {
            if (this.optionalList == null) {
                this.optionalList = new ArrayList<>();
            }
            this.optionalList.add(optionalList);
            return this;
        }

        public Builder optionalList(String... optionalList) {
            if (this.optionalList == null) {
                this.optionalList = new ArrayList<>();
            }
            Collections.addAll(this.optionalList, optionalList);
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
                    case 0 -> builder.requiredList(SharedSchemas.deserializeListOfStrings(member, de));
                    case 1 -> builder.listWithDefault(SharedSchemas.deserializeListOfStrings(member, de));
                    case 2 -> builder.optionalList(SharedSchemas.deserializeListOfStrings(member, de));
                }
            }
        }
    }
}

