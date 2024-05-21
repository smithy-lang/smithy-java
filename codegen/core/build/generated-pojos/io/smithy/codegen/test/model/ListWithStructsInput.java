

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
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class ListWithStructsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#ListWithStructsInput");

    private static final SdkSchema SCHEMA_LIST_OF_STRUCTS = SdkSchema.memberBuilder("listOfStructs", SharedSchemas.LIST_OF_STRUCT)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_LIST_OF_STRUCTS
        )
        .build();

    private transient final List<Nested> listOfStructs;

    private ListWithStructsInput(Builder builder) {
        this.listOfStructs = builder.listOfStructs;
    }

    public List<Nested> listOfStructs() {
        if (listOfStructs == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(listOfStructs);
    }

    public boolean hasListOfStructs() {
        return listOfStructs != null;
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
        ListWithStructsInput that = (ListWithStructsInput) other;
        return Objects.equals(listOfStructs, that.listOfStructs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listOfStructs);
    }

    @Override
    public SdkSchema schema() {
        return SCHEMA;
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (listOfStructs != null) {
            serializer.writeList(SCHEMA_LIST_OF_STRUCTS, listOfStructs, SharedSerde.ListOfStructSerializer.INSTANCE);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ListWithStructsInput}.
     */
    public static final class Builder implements SdkShapeBuilder<ListWithStructsInput> {
        private List<Nested> listOfStructs;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder listOfStructs(List<Nested> listOfStructs) {
            this.listOfStructs = listOfStructs;
            return this;
        }

        @Override
        public ListWithStructsInput build() {
            tracker.validate();
            return new ListWithStructsInput(this);
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
                    case 0 -> builder.listOfStructs(SharedSerde.deserializeListOfStruct(member, de));
                }
            }
        }
    }
}

