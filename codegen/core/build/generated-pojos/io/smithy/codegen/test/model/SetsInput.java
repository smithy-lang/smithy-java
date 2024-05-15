

package io.smithy.codegen.test.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
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
public final class SetsInput implements SerializableShape {
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
        this.requiredList = Collections.unmodifiableSet(builder.requiredList);
        this.listWithDefault = Collections.unmodifiableSet(builder.listWithDefault);
        this.optionalList = builder.optionalList != null ? Collections.unmodifiableSet(builder.optionalList) : null;
    }

    /**
     * Required set with no default value
     */
    public Set<String> requiredList() {
        return requiredList;
    }

    public boolean hasRequiredList() {
        return requiredList != null;
    }

    /**
     * Set with a default value. Sets can only ever have empty defaults.
     */
    public Set<String> listWithDefault() {
        return listWithDefault;
    }

    public boolean hasListWithDefault() {
        return listWithDefault != null;
    }

    /**
     * Optional set
     */
    public Set<String> optionalList() {
        return optionalList != null ? optionalList : Collections.emptySet();
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
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this, InnerSerializer.INSTANCE);
    }

    static final class InnerSerializer implements BiConsumer<SetsInput, ShapeSerializer> {
        static final InnerSerializer INSTANCE = new InnerSerializer();

        @Override
        public void accept(SetsInput shape, ShapeSerializer serializer) {
            serializer.writeList(SCHEMA_REQUIRED_LIST, shape.requiredList, SharedSchemas.SetOfStringsSerializer.INSTANCE);
            serializer.writeList(SCHEMA_LIST_WITH_DEFAULT, shape.listWithDefault, SharedSchemas.SetOfStringsSerializer.INSTANCE);
            if (shape.optionalList != null) {
                serializer.writeList(SCHEMA_OPTIONAL_LIST, shape.optionalList, SharedSchemas.SetOfStringsSerializer.INSTANCE);
            }
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
        private Set<String> listWithDefault = new LinkedHashSet<>();
        private Set<String> optionalList;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder requiredList(Collection<String> requiredList) {
            this.requiredList = new LinkedHashSet<>(Objects.requireNonNull(requiredList, "requiredList cannot be null"));
            tracker.setMember(SCHEMA_REQUIRED_LIST);
            return this;
        }

        public Builder addAllRequiredList(Collection<String> requiredList) {
            if (this.requiredList == null) {
                this.requiredList = new LinkedHashSet<>(requiredList);
                tracker.setMember(SCHEMA_REQUIRED_LIST);
            } else {
                this.requiredList.addAll(requiredList);
            }
            return this;
        }

        public Builder requiredList(String requiredList) {
            if (this.requiredList == null) {
                this.requiredList = new LinkedHashSet<>();
                tracker.setMember(SCHEMA_REQUIRED_LIST);
            }
            this.requiredList.add(requiredList);
            return this;
        }

        public Builder requiredList(String... requiredList) {
            if (this.requiredList == null) {
                this.requiredList = new LinkedHashSet<>();
                tracker.setMember(SCHEMA_REQUIRED_LIST);
            }
            Collections.addAll(this.requiredList, requiredList);
            return this;
        }

        public Builder listWithDefault(Collection<String> listWithDefault) {
            this.listWithDefault = listWithDefault != null ? new LinkedHashSet<>(listWithDefault) : null;
            return this;
        }

        public Builder addAllListWithDefault(Collection<String> listWithDefault) {
            if (this.listWithDefault == null) {
                this.listWithDefault = new LinkedHashSet<>(listWithDefault);
            } else {
                this.listWithDefault.addAll(listWithDefault);
            }
            return this;
        }

        public Builder listWithDefault(String listWithDefault) {
            if (this.listWithDefault == null) {
                this.listWithDefault = new LinkedHashSet<>();
            }
            this.listWithDefault.add(listWithDefault);
            return this;
        }

        public Builder listWithDefault(String... listWithDefault) {
            if (this.listWithDefault == null) {
                this.listWithDefault = new LinkedHashSet<>();
            }
            Collections.addAll(this.listWithDefault, listWithDefault);
            return this;
        }

        public Builder optionalList(Collection<String> optionalList) {
            this.optionalList = optionalList != null ? new LinkedHashSet<>(optionalList) : null;
            return this;
        }

        public Builder addAllOptionalList(Collection<String> optionalList) {
            if (this.optionalList == null) {
                this.optionalList = new LinkedHashSet<>(optionalList);
            } else {
                this.optionalList.addAll(optionalList);
            }
            return this;
        }

        public Builder optionalList(String optionalList) {
            if (this.optionalList == null) {
                this.optionalList = new LinkedHashSet<>();
            }
            this.optionalList.add(optionalList);
            return this;
        }

        public Builder optionalList(String... optionalList) {
            if (this.optionalList == null) {
                this.optionalList = new LinkedHashSet<>();
            }
            Collections.addAll(this.optionalList, optionalList);
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
                    case 0 -> builder.requiredList(SharedSchemas.deserializeSetOfStrings(member, de));
                    case 1 -> builder.listWithDefault(SharedSchemas.deserializeSetOfStrings(member, de));
                    case 2 -> builder.optionalList(SharedSchemas.deserializeSetOfStrings(member, de));
                }
            }
        }
    }
}

