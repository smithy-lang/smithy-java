

package io.smithy.codegen.test.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
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
public final class NestedMapsInput implements SerializableStruct {
    public static final ShapeId ID = ShapeId.from("smithy.java.codegen.test.structures.members#NestedMapsInput");

    private static final SdkSchema SCHEMA_MAP_OF_STRING_MAP = SdkSchema.memberBuilder("mapOfStringMap", SharedSchemas.MAP_OF_STRING_MAP)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_MAP_OF_MAP_OF_STRING_MAP = SdkSchema.memberBuilder("mapOfMapOfStringMap", SharedSchemas.MAP_OF_MAP_OF_STRING_MAP)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_MAP_OF_STRING_LIST = SdkSchema.memberBuilder("mapOfStringList", SharedSchemas.MAP_OF_STRING_LIST)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_MAP_OF_MAP_LIST = SdkSchema.memberBuilder("mapOfMapList", SharedSchemas.MAP_OF_MAP_LIST)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_MAP_OF_STRING_MAP,
            SCHEMA_MAP_OF_MAP_OF_STRING_MAP,
            SCHEMA_MAP_OF_STRING_LIST,
            SCHEMA_MAP_OF_MAP_LIST
        )
        .build();

    private transient final Map<String, Map<String, String>> mapOfStringMap;
    private transient final Map<String, Map<String, Map<String, String>>> mapOfMapOfStringMap;
    private transient final Map<String, List<String>> mapOfStringList;
    private transient final Map<String, List<Map<String, String>>> mapOfMapList;

    private NestedMapsInput(Builder builder) {
        this.mapOfStringMap = builder.mapOfStringMap;
        this.mapOfMapOfStringMap = builder.mapOfMapOfStringMap;
        this.mapOfStringList = builder.mapOfStringList;
        this.mapOfMapList = builder.mapOfMapList;
    }

    public Map<String, Map<String, String>> mapOfStringMap() {
        if (mapOfStringMap == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(mapOfStringMap);
    }

    public boolean hasMapOfStringMap() {
        return mapOfStringMap != null;
    }

    public Map<String, Map<String, Map<String, String>>> mapOfMapOfStringMap() {
        if (mapOfMapOfStringMap == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(mapOfMapOfStringMap);
    }

    public boolean hasMapOfMapOfStringMap() {
        return mapOfMapOfStringMap != null;
    }

    public Map<String, List<String>> mapOfStringList() {
        if (mapOfStringList == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(mapOfStringList);
    }

    public boolean hasMapOfStringList() {
        return mapOfStringList != null;
    }

    public Map<String, List<Map<String, String>>> mapOfMapList() {
        if (mapOfMapList == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(mapOfMapList);
    }

    public boolean hasMapOfMapList() {
        return mapOfMapList != null;
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
        NestedMapsInput that = (NestedMapsInput) other;
        return Objects.equals(mapOfStringMap, that.mapOfStringMap)
               && Objects.equals(mapOfMapOfStringMap, that.mapOfMapOfStringMap)
               && Objects.equals(mapOfStringList, that.mapOfStringList)
               && Objects.equals(mapOfMapList, that.mapOfMapList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mapOfStringMap, mapOfMapOfStringMap, mapOfStringList, mapOfMapList);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        if (mapOfStringMap != null) {
            serializer.writeMap(SCHEMA_MAP_OF_STRING_MAP, mapOfStringMap, SharedSerde.MapOfStringMapSerializer.INSTANCE);
        }

        if (mapOfMapOfStringMap != null) {
            serializer.writeMap(SCHEMA_MAP_OF_MAP_OF_STRING_MAP, mapOfMapOfStringMap, SharedSerde.MapOfMapOfStringMapSerializer.INSTANCE);
        }

        if (mapOfStringList != null) {
            serializer.writeMap(SCHEMA_MAP_OF_STRING_LIST, mapOfStringList, SharedSerde.MapOfStringListSerializer.INSTANCE);
        }

        if (mapOfMapList != null) {
            serializer.writeMap(SCHEMA_MAP_OF_MAP_LIST, mapOfMapList, SharedSerde.MapOfMapListSerializer.INSTANCE);
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link NestedMapsInput}.
     */
    public static final class Builder implements SdkShapeBuilder<NestedMapsInput> {
        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);
        private Map<String, Map<String, String>> mapOfStringMap;
        private Map<String, Map<String, Map<String, String>>> mapOfMapOfStringMap;
        private Map<String, List<String>> mapOfStringList;
        private Map<String, List<Map<String, String>>> mapOfMapList;

        private Builder() {}

        public Builder mapOfStringMap(Map<String, Map<String, String>> mapOfStringMap) {
            this.mapOfStringMap = mapOfStringMap;
            return this;
        }

        public Builder mapOfMapOfStringMap(Map<String, Map<String, Map<String, String>>> mapOfMapOfStringMap) {
            this.mapOfMapOfStringMap = mapOfMapOfStringMap;
            return this;
        }

        public Builder mapOfStringList(Map<String, List<String>> mapOfStringList) {
            this.mapOfStringList = mapOfStringList;
            return this;
        }

        public Builder mapOfMapList(Map<String, List<Map<String, String>>> mapOfMapList) {
            this.mapOfMapList = mapOfMapList;
            return this;
        }

        @Override
        public NestedMapsInput build() {
            return new NestedMapsInput(this);
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
                    case 0 -> builder.mapOfStringMap(SharedSerde.deserializeMapOfStringMap(member, de));
                    case 1 -> builder.mapOfMapOfStringMap(SharedSerde.deserializeMapOfMapOfStringMap(member, de));
                    case 2 -> builder.mapOfStringList(SharedSerde.deserializeMapOfStringList(member, de));
                    case 3 -> builder.mapOfMapList(SharedSerde.deserializeMapOfMapList(member, de));
                }
            }
        }

    }

    public Builder toBuilder() {
        var builder =  new Builder();
        builder.mapOfStringMap(this.mapOfStringMap);
        builder.mapOfMapOfStringMap(this.mapOfMapOfStringMap);
        builder.mapOfStringList(this.mapOfStringList);
        builder.mapOfMapList(this.mapOfMapList);
        return builder;
    }

}

