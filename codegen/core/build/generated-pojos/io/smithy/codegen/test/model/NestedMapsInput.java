

package io.smithy.codegen.test.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
public final class NestedMapsInput implements SerializableShape {
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
        this.mapOfStringMap = builder.mapOfStringMap != null ? Collections.unmodifiableMap(builder.mapOfStringMap) : null;
        this.mapOfMapOfStringMap = builder.mapOfMapOfStringMap != null ? Collections.unmodifiableMap(builder.mapOfMapOfStringMap) : null;
        this.mapOfStringList = builder.mapOfStringList != null ? Collections.unmodifiableMap(builder.mapOfStringList) : null;
        this.mapOfMapList = builder.mapOfMapList != null ? Collections.unmodifiableMap(builder.mapOfMapList) : null;
    }

    public Map<String, Map<String, String>> mapOfStringMap() {
        return mapOfStringMap != null ? mapOfStringMap : Collections.emptyMap();
    }

    public boolean hasMapOfStringMap() {
        return mapOfStringMap != null;
    }

    public Map<String, Map<String, Map<String, String>>> mapOfMapOfStringMap() {
        return mapOfMapOfStringMap != null ? mapOfMapOfStringMap : Collections.emptyMap();
    }

    public boolean hasMapOfMapOfStringMap() {
        return mapOfMapOfStringMap != null;
    }

    public Map<String, List<String>> mapOfStringList() {
        return mapOfStringList != null ? mapOfStringList : Collections.emptyMap();
    }

    public boolean hasMapOfStringList() {
        return mapOfStringList != null;
    }

    public Map<String, List<Map<String, String>>> mapOfMapList() {
        return mapOfMapList != null ? mapOfMapList : Collections.emptyMap();
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
        serializer.writeStruct(SCHEMA, this, InnerSerializer.INSTANCE);
    }

    static final class InnerSerializer implements BiConsumer<NestedMapsInput, ShapeSerializer> {
        static final InnerSerializer INSTANCE = new InnerSerializer();

        @Override
        public void accept(NestedMapsInput shape, ShapeSerializer serializer) {
            if (shape.mapOfStringMap != null) {
                serializer.writeMap(SCHEMA_MAP_OF_STRING_MAP, shape.mapOfStringMap, SharedSchemas.MapOfStringMapSerializer.INSTANCE);
            }
            if (shape.mapOfMapOfStringMap != null) {
                serializer.writeMap(SCHEMA_MAP_OF_MAP_OF_STRING_MAP, shape.mapOfMapOfStringMap, SharedSchemas.MapOfMapOfStringMapSerializer.INSTANCE);
            }
            if (shape.mapOfStringList != null) {
                serializer.writeMap(SCHEMA_MAP_OF_STRING_LIST, shape.mapOfStringList, SharedSchemas.MapOfStringListSerializer.INSTANCE);
            }
            if (shape.mapOfMapList != null) {
                serializer.writeMap(SCHEMA_MAP_OF_MAP_LIST, shape.mapOfMapList, SharedSchemas.MapOfMapListSerializer.INSTANCE);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link NestedMapsInput}.
     */
    public static final class Builder implements SdkShapeBuilder<NestedMapsInput> {
        private Map<String, Map<String, String>> mapOfStringMap;
        private Map<String, Map<String, Map<String, String>>> mapOfMapOfStringMap;
        private Map<String, List<String>> mapOfStringList;
        private Map<String, List<Map<String, String>>> mapOfMapList;

        private final PresenceTracker tracker = PresenceTracker.of(SCHEMA);

        private Builder() {}

        public Builder mapOfStringMap(Map<String, Map<String, String>> mapOfStringMap) {
            this.mapOfStringMap = mapOfStringMap != null ? new LinkedHashMap<>(mapOfStringMap) : null;
            return this;
        }

        public Builder putAllMapOfStringMap(Map<String, Map<String, String>> mapOfStringMap) {
            if (this.mapOfStringMap == null) {
                this.mapOfStringMap = new LinkedHashMap<>(mapOfStringMap);
            } else {
                this.mapOfStringMap.putAll(mapOfStringMap);
            }
            return this;
        }

        public Builder putMapOfStringMap(String key, Map<String, String> value) {
           if (this.mapOfStringMap == null) {
               this.mapOfStringMap = new LinkedHashMap<>();
           }
           this.mapOfStringMap.put(key, value);
           return this;
        }

        public Builder mapOfMapOfStringMap(Map<String, Map<String, Map<String, String>>> mapOfMapOfStringMap) {
            this.mapOfMapOfStringMap = mapOfMapOfStringMap != null ? new LinkedHashMap<>(mapOfMapOfStringMap) : null;
            return this;
        }

        public Builder putAllMapOfMapOfStringMap(Map<String, Map<String, Map<String, String>>> mapOfMapOfStringMap) {
            if (this.mapOfMapOfStringMap == null) {
                this.mapOfMapOfStringMap = new LinkedHashMap<>(mapOfMapOfStringMap);
            } else {
                this.mapOfMapOfStringMap.putAll(mapOfMapOfStringMap);
            }
            return this;
        }

        public Builder putMapOfMapOfStringMap(String key, Map<String, Map<String, String>> value) {
           if (this.mapOfMapOfStringMap == null) {
               this.mapOfMapOfStringMap = new LinkedHashMap<>();
           }
           this.mapOfMapOfStringMap.put(key, value);
           return this;
        }

        public Builder mapOfStringList(Map<String, List<String>> mapOfStringList) {
            this.mapOfStringList = mapOfStringList != null ? new LinkedHashMap<>(mapOfStringList) : null;
            return this;
        }

        public Builder putAllMapOfStringList(Map<String, List<String>> mapOfStringList) {
            if (this.mapOfStringList == null) {
                this.mapOfStringList = new LinkedHashMap<>(mapOfStringList);
            } else {
                this.mapOfStringList.putAll(mapOfStringList);
            }
            return this;
        }

        public Builder putMapOfStringList(String key, List<String> value) {
           if (this.mapOfStringList == null) {
               this.mapOfStringList = new LinkedHashMap<>();
           }
           this.mapOfStringList.put(key, value);
           return this;
        }

        public Builder mapOfMapList(Map<String, List<Map<String, String>>> mapOfMapList) {
            this.mapOfMapList = mapOfMapList != null ? new LinkedHashMap<>(mapOfMapList) : null;
            return this;
        }

        public Builder putAllMapOfMapList(Map<String, List<Map<String, String>>> mapOfMapList) {
            if (this.mapOfMapList == null) {
                this.mapOfMapList = new LinkedHashMap<>(mapOfMapList);
            } else {
                this.mapOfMapList.putAll(mapOfMapList);
            }
            return this;
        }

        public Builder putMapOfMapList(String key, List<Map<String, String>> value) {
           if (this.mapOfMapList == null) {
               this.mapOfMapList = new LinkedHashMap<>();
           }
           this.mapOfMapList.put(key, value);
           return this;
        }

        @Override
        public NestedMapsInput build() {
            tracker.validate();
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
                    case 0 -> builder.mapOfStringMap(SharedSchemas.deserializeMapOfStringMap(member, de));
                    case 1 -> builder.mapOfMapOfStringMap(SharedSchemas.deserializeMapOfMapOfStringMap(member, de));
                    case 2 -> builder.mapOfStringList(SharedSchemas.deserializeMapOfStringList(member, de));
                    case 3 -> builder.mapOfMapList(SharedSchemas.deserializeMapOfMapList(member, de));
                }
            }
        }
    }
}

