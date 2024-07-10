

package software.amazon.smithy.java.codegen.test.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.SmithyGenerated;

@SmithyGenerated
public final class NestedListsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.lists#NestedListsInput");

    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember("listOfLists", SharedSchemas.LIST_OF_STRING_LIST)
        .putMember("listOfListOfList", SharedSchemas.LIST_OF_LIST_OF_STRING_LIST)
        .putMember("listOfMaps", SharedSchemas.LIST_OF_MAPS)
        .build();

    private static final Schema SCHEMA_LIST_OF_LISTS = SCHEMA.member("listOfLists");
    private static final Schema SCHEMA_LIST_OF_LIST_OF_LIST = SCHEMA.member("listOfListOfList");
    private static final Schema SCHEMA_LIST_OF_MAPS = SCHEMA.member("listOfMaps");

    private transient final List<List<String>> listOfLists;
    private transient final List<List<List<String>>> listOfListOfList;
    private transient final List<Map<String, String>> listOfMaps;

    private NestedListsInput(Builder builder) {
        this.listOfLists = builder.listOfLists == null ? null : Collections.unmodifiableList(builder.listOfLists);
        this.listOfListOfList = builder.listOfListOfList == null ? null : Collections.unmodifiableList(builder.listOfListOfList);
        this.listOfMaps = builder.listOfMaps == null ? null : Collections.unmodifiableList(builder.listOfMaps);
    }

    public List<List<String>> listOfLists() {
        if (listOfLists == null) {
            return Collections.emptyList();
        }
        return listOfLists;
    }

    public boolean hasListOfLists() {
        return listOfLists != null;
    }

    public List<List<List<String>>> listOfListOfList() {
        if (listOfListOfList == null) {
            return Collections.emptyList();
        }
        return listOfListOfList;
    }

    public boolean hasListOfListOfList() {
        return listOfListOfList != null;
    }

    public List<Map<String, String>> listOfMaps() {
        if (listOfMaps == null) {
            return Collections.emptyList();
        }
        return listOfMaps;
    }

    public boolean hasListOfMaps() {
        return listOfMaps != null;
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
        NestedListsInput that = (NestedListsInput) other;
        return Objects.equals(this.listOfLists, that.listOfLists)
               && Objects.equals(this.listOfListOfList, that.listOfListOfList)
               && Objects.equals(this.listOfMaps, that.listOfMaps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listOfLists, listOfListOfList, listOfMaps);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (listOfLists != null) {
            serializer.writeList(SCHEMA_LIST_OF_LISTS, listOfLists, SharedSerde.ListOfStringListSerializer.INSTANCE);
        }
        if (listOfListOfList != null) {
            serializer.writeList(SCHEMA_LIST_OF_LIST_OF_LIST, listOfListOfList, SharedSerde.ListOfListOfStringListSerializer.INSTANCE);
        }
        if (listOfMaps != null) {
            serializer.writeList(SCHEMA_LIST_OF_MAPS, listOfMaps, SharedSerde.ListOfMapsSerializer.INSTANCE);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link NestedListsInput}.
     */
    public static final class Builder implements ShapeBuilder<NestedListsInput> {
        private List<List<String>> listOfLists;
        private List<List<List<String>>> listOfListOfList;
        private List<Map<String, String>> listOfMaps;

        private Builder() {}

        public Builder listOfLists(List<List<String>> listOfLists) {
            this.listOfLists = listOfLists;
            return this;
        }

        public Builder listOfListOfList(List<List<List<String>>> listOfListOfList) {
            this.listOfListOfList = listOfListOfList;
            return this;
        }

        public Builder listOfMaps(List<Map<String, String>> listOfMaps) {
            this.listOfMaps = listOfMaps;
            return this;
        }

        @Override
        public NestedListsInput build() {
            return new NestedListsInput(this);
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
                    case 0 -> builder.listOfLists(SharedSerde.deserializeListOfStringList(member, de));
                    case 1 -> builder.listOfListOfList(SharedSerde.deserializeListOfListOfStringList(member, de));
                    case 2 -> builder.listOfMaps(SharedSerde.deserializeListOfMaps(member, de));
                }
            }
        }
    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.listOfLists(this.listOfLists);
        builder.listOfListOfList(this.listOfListOfList);
        builder.listOfMaps(this.listOfMaps);
        return builder;
    }

}

