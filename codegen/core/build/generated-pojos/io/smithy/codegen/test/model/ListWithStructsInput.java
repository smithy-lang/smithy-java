

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
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class ListWithStructsInput implements SerializableShape {
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
        this.listOfStructs = builder.listOfStructs != null ? Collections.unmodifiableList(builder.listOfStructs) : null;
    }

    public List<Nested> listOfStructs() {
        return listOfStructs != null ? listOfStructs : Collections.emptyList();
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
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this, InnerSerializer.INSTANCE);
    }

    static final class InnerSerializer implements BiConsumer<ListWithStructsInput, ShapeSerializer> {
        static final InnerSerializer INSTANCE = new InnerSerializer();

        @Override
        public void accept(ListWithStructsInput shape, ShapeSerializer serializer) {
            if (shape.listOfStructs != null) {
                serializer.writeList(SCHEMA_LIST_OF_STRUCTS, shape.listOfStructs, SharedSchemas.ListOfStructSerializer.INSTANCE);
            }
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

        public Builder listOfStructs(Collection<Nested> listOfStructs) {
            this.listOfStructs = listOfStructs != null ? new ArrayList<>(listOfStructs) : null;
            return this;
        }

        public Builder addAllListOfStructs(Collection<Nested> listOfStructs) {
            if (this.listOfStructs == null) {
                this.listOfStructs = new ArrayList<>(listOfStructs);
            } else {
                this.listOfStructs.addAll(listOfStructs);
            }
            return this;
        }

        public Builder listOfStructs(Nested listOfStructs) {
            if (this.listOfStructs == null) {
                this.listOfStructs = new ArrayList<>();
            }
            this.listOfStructs.add(listOfStructs);
            return this;
        }

        public Builder listOfStructs(Nested... listOfStructs) {
            if (this.listOfStructs == null) {
                this.listOfStructs = new ArrayList<>();
            }
            Collections.addAll(this.listOfStructs, listOfStructs);
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
                    case 0 -> builder.listOfStructs(SharedSchemas.deserializeListOfStruct(member, de));
                }
            }
        }
    }
}

