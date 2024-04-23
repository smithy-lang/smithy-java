/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.codegen.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedSet;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.DefaultTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.SmithyGenerated;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * This Pojo has some documentation attached
 */
@SmithyGenerated
public final class DemoPojo implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#DemoPojo");

    private static final SdkSchema SCHEMA_DOCUMENTATION = SdkSchema.memberBuilder(0, "documentation", PreludeSchemas.STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_REQUIRED_PRIMITIVE = SdkSchema.memberBuilder(1, "requiredPrimitive", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_PRIMITIVE = SdkSchema.memberBuilder(2, "optionalPrimitive", PreludeSchemas.INTEGER)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_PRIMITIVE = SdkSchema.memberBuilder(3, "defaultPrimitive", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(1)
            )
        )
        .build();

    private static final SdkSchema SCHEMA_REQUIRED_LIST = SdkSchema.memberBuilder(4, "requiredList", SharedSchemas.LIST_OF_STRING)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_LIST = SdkSchema.memberBuilder(5, "optionalList", SharedSchemas.LIST_OF_STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_LIST = SdkSchema.memberBuilder(6, "defaultList", SharedSchemas.LIST_OF_STRING)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                ArrayNode.builder()
                    .build()
            )
        )
        .build();

    private static final SdkSchema SCHEMA_REQUIRED_SET = SdkSchema.memberBuilder(7, "requiredSet", SharedSchemas.SET_OF_STRING)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_SET = SdkSchema.memberBuilder(8, "optionalSet", SharedSchemas.SET_OF_STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_REQUIRED_MAP = SdkSchema.memberBuilder(9, "requiredMap", SharedSchemas.MAP_OF_INTEGERS)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_MAP = SdkSchema.memberBuilder(10, "defaultMap", SharedSchemas.MAP_OF_INTEGERS)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.objectNodeBuilder()
                    .build()
            )
        )
        .build();

    private static final SdkSchema SCHEMA_OPTIONAL_MAP = SdkSchema.memberBuilder(11, "optionalMap", SharedSchemas.MAP_OF_INTEGERS)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_STRING_WITH_JSON_NAME = SdkSchema.memberBuilder(12, "stringWithJsonName", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new JsonNameTrait("Age")
        )
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_TIMESTAMP = SdkSchema.memberBuilder(13, "defaultTimestamp", PreludeSchemas.TIMESTAMP)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from("1985-04-12T23:20:50.52Z")
            )
        )
        .build();

    private static final SdkSchema SCHEMA_NESTED_STRUCT = SdkSchema.memberBuilder(14, "nestedStruct", Nested.SCHEMA)
        .id(ID)
        .build();

    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_DOCUMENTATION,
            SCHEMA_REQUIRED_PRIMITIVE,
            SCHEMA_OPTIONAL_PRIMITIVE,
            SCHEMA_DEFAULT_PRIMITIVE,
            SCHEMA_REQUIRED_LIST,
            SCHEMA_OPTIONAL_LIST,
            SCHEMA_DEFAULT_LIST,
            SCHEMA_REQUIRED_SET,
            SCHEMA_OPTIONAL_SET,
            SCHEMA_REQUIRED_MAP,
            SCHEMA_DEFAULT_MAP,
            SCHEMA_OPTIONAL_MAP,
            SCHEMA_STRING_WITH_JSON_NAME,
            SCHEMA_DEFAULT_TIMESTAMP,
            SCHEMA_NESTED_STRUCT
        )
        .build();

    private final String documentation;
    private final int requiredPrimitive;
    private final Integer optionalPrimitive;
    private final int defaultPrimitive;
    private final List<String> requiredList;
    private final List<String> optionalList;
    private final List<String> defaultList;
    private final SequencedSet<String> requiredSet;
    private final SequencedSet<String> optionalSet;
    private final Map<String, Integer> requiredMap;
    private final Map<String, Integer> defaultMap;
    private final Map<String, Integer> optionalMap;
    private final String stringWithJsonName;
    private final Instant defaultTimestamp;
    private final Nested nestedStruct;

    private DemoPojo(Builder builder) {
        this.documentation = builder.documentation;
        this.requiredPrimitive = SmithyBuilder.requiredState("requiredPrimitive", builder.requiredPrimitive);
        this.optionalPrimitive = builder.optionalPrimitive;
        this.defaultPrimitive = SmithyBuilder.requiredState("defaultPrimitive", builder.defaultPrimitive);
        this.requiredList = Collections.unmodifiableList(builder.requiredList);
        this.optionalList = builder.optionalList != null ? Collections.unmodifiableList(builder.optionalList) : null;
        this.defaultList = Collections.unmodifiableList(builder.defaultList);
        this.requiredSet = Collections.unmodifiableSequencedSet(builder.requiredSet);
        this.optionalSet = builder.optionalSet != null ? Collections.unmodifiableSequencedSet(builder.optionalSet) : null;
        this.requiredMap = Collections.unmodifiableMap(builder.requiredMap);
        this.defaultMap = Collections.unmodifiableMap(builder.defaultMap);
        this.optionalMap = builder.optionalMap != null ? Collections.unmodifiableMap(builder.optionalMap) : null;
        this.stringWithJsonName = builder.stringWithJsonName;
        this.defaultTimestamp = SmithyBuilder.requiredState("defaultTimestamp", builder.defaultTimestamp);
        this.nestedStruct = builder.nestedStruct;
    }

    /**
     * This member shows how documentation traits work
     *
     * @see <a href="https://en.wikipedia.org/wiki/Puffin">Puffins are still cool</a>
     * @since 4.5
     * @deprecated As of sometime.
     */
    @SmithyUnstableApi
    @Deprecated(since = "sometime")
    public String documentation() {
        return documentation;
    }

    public Integer requiredPrimitive() {
        return requiredPrimitive;
    }

    public int optionalPrimitive() {
        return optionalPrimitive;
    }

    public Integer defaultPrimitive() {
        return defaultPrimitive;
    }

    public List<String> requiredList() {
        return requiredList;
    }

    public boolean hasRequiredList() {
        return requiredList != null;
    }

    public List<String> optionalList() {
        return optionalList != null ? optionalList : Collections.emptyList();
    }

    public boolean hasOptionalList() {
        return optionalList != null;
    }

    /**
     * NOTE: List and Map defaults can ONLY be empty
     */
    public List<String> defaultList() {
        return defaultList;
    }

    public boolean hasDefaultList() {
        return defaultList != null;
    }

    public SequencedSet<String> requiredSet() {
        return requiredSet;
    }

    public boolean hasRequiredSet() {
        return requiredSet != null;
    }

    public SequencedSet<String> optionalSet() {
        return optionalSet != null ? optionalSet : Collections.emptySortedSet();
    }

    public boolean hasOptionalSet() {
        return optionalSet != null;
    }

    public Map<String, Integer> requiredMap() {
        return requiredMap;
    }

    public boolean hasRequiredMap() {
        return requiredMap != null;
    }

    public Map<String, Integer> defaultMap() {
        return defaultMap;
    }

    public boolean hasDefaultMap() {
        return defaultMap != null;
    }

    public Map<String, Integer> optionalMap() {
        return optionalMap != null ? optionalMap : Collections.emptyMap();
    }

    public boolean hasOptionalMap() {
        return optionalMap != null;
    }

    public String stringWithJsonName() {
        return stringWithJsonName;
    }

    public Instant defaultTimestamp() {
        return defaultTimestamp;
    }

    public Nested nestedStruct() {
        return nestedStruct;
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
        DemoPojo that = (DemoPojo) other;
        return Objects.equals(documentation, that.documentation)
               && requiredPrimitive == that.requiredPrimitive
               && Objects.equals(optionalPrimitive, that.optionalPrimitive)
               && defaultPrimitive == that.defaultPrimitive
               && Objects.equals(requiredList, that.requiredList)
               && Objects.equals(optionalList, that.optionalList)
               && Objects.equals(defaultList, that.defaultList)
               && Objects.equals(requiredSet, that.requiredSet)
               && Objects.equals(optionalSet, that.optionalSet)
               && Objects.equals(requiredMap, that.requiredMap)
               && Objects.equals(defaultMap, that.defaultMap)
               && Objects.equals(optionalMap, that.optionalMap)
               && Objects.equals(stringWithJsonName, that.stringWithJsonName)
               && Objects.equals(defaultTimestamp, that.defaultTimestamp)
               && Objects.equals(nestedStruct, that.nestedStruct);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentation, requiredPrimitive, optionalPrimitive, defaultPrimitive, requiredList, optionalList, defaultList, requiredSet, optionalSet, requiredMap, defaultMap, optionalMap, stringWithJsonName, defaultTimestamp, nestedStruct);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        // Placeholder. Do nothing
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DemoPojo}.
     */
    public static final class Builder implements SdkShapeBuilder<DemoPojo> {
        private String documentation;
        private int requiredPrimitive;
        private Integer optionalPrimitive;
        private int defaultPrimitive = 1;
        private List<String> requiredList;
        private List<String> optionalList;
        private List<String> defaultList = new ArrayList<>();
        private SequencedSet<String> requiredSet;
        private SequencedSet<String> optionalSet;
        private Map<String, Integer> requiredMap;
        private Map<String, Integer> defaultMap = new LinkedHashMap<>();
        private Map<String, Integer> optionalMap;
        private String stringWithJsonName;
        private Instant defaultTimestamp = Instant.parse("1985-04-12T23:20:50.52Z");
        private Nested nestedStruct;

        private Builder() {}

        public Builder documentation(String documentation) {
            this.documentation = documentation;
            return this;
        }

        public Builder requiredPrimitive(int requiredPrimitive) {
            this.requiredPrimitive = requiredPrimitive;
            return this;
        }

        public Builder optionalPrimitive(int optionalPrimitive) {
            this.optionalPrimitive = optionalPrimitive;
            return this;
        }

        public Builder defaultPrimitive(int defaultPrimitive) {
            this.defaultPrimitive = defaultPrimitive;
            return this;
        }

        public Builder requiredList(Collection<String> requiredList) {
            this.requiredList = requiredList != null ? new ArrayList<>(requiredList) : null;
            return this;
        }

        public Builder addAllRequiredList(Collection<String> requiredList) {
            if (this.requiredList == null) {
                this.requiredList = new ArrayList<>(requiredList);
            } else {
                this.requiredList.addAll(requiredList);
            }
            return this;
        }

        public Builder requiredList(String requiredList) {
            if (this.requiredList == null) {
                this.requiredList = new ArrayList<>();
            }
            this.requiredList.add(requiredList);
            return this;
        }

        public Builder requiredList(String... requiredList) {
            if (this.requiredList == null) {
                this.requiredList = new ArrayList<>();
            }
            Collections.addAll(this.requiredList, requiredList);
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

        public Builder defaultList(Collection<String> defaultList) {
            this.defaultList = defaultList != null ? new ArrayList<>(defaultList) : null;
            return this;
        }

        public Builder addAllDefaultList(Collection<String> defaultList) {
            if (this.defaultList == null) {
                this.defaultList = new ArrayList<>(defaultList);
            } else {
                this.defaultList.addAll(defaultList);
            }
            return this;
        }

        public Builder defaultList(String defaultList) {
            if (this.defaultList == null) {
                this.defaultList = new ArrayList<>();
            }
            this.defaultList.add(defaultList);
            return this;
        }

        public Builder defaultList(String... defaultList) {
            if (this.defaultList == null) {
                this.defaultList = new ArrayList<>();
            }
            Collections.addAll(this.defaultList, defaultList);
            return this;
        }

        public Builder requiredSet(Collection<String> requiredSet) {
            this.requiredSet = requiredSet != null ? new LinkedHashSet<>(requiredSet) : null;
            return this;
        }

        public Builder addAllRequiredSet(Collection<String> requiredSet) {
            if (this.requiredSet == null) {
                this.requiredSet = new LinkedHashSet<>(requiredSet);
            } else {
                this.requiredSet.addAll(requiredSet);
            }
            return this;
        }

        public Builder requiredSet(String requiredSet) {
            if (this.requiredSet == null) {
                this.requiredSet = new LinkedHashSet<>();
            }
            this.requiredSet.add(requiredSet);
            return this;
        }

        public Builder requiredSet(String... requiredSet) {
            if (this.requiredSet == null) {
                this.requiredSet = new LinkedHashSet<>();
            }
            Collections.addAll(this.requiredSet, requiredSet);
            return this;
        }

        public Builder optionalSet(Collection<String> optionalSet) {
            this.optionalSet = optionalSet != null ? new LinkedHashSet<>(optionalSet) : null;
            return this;
        }

        public Builder addAllOptionalSet(Collection<String> optionalSet) {
            if (this.optionalSet == null) {
                this.optionalSet = new LinkedHashSet<>(optionalSet);
            } else {
                this.optionalSet.addAll(optionalSet);
            }
            return this;
        }

        public Builder optionalSet(String optionalSet) {
            if (this.optionalSet == null) {
                this.optionalSet = new LinkedHashSet<>();
            }
            this.optionalSet.add(optionalSet);
            return this;
        }

        public Builder optionalSet(String... optionalSet) {
            if (this.optionalSet == null) {
                this.optionalSet = new LinkedHashSet<>();
            }
            Collections.addAll(this.optionalSet, optionalSet);
            return this;
        }

        public Builder requiredMap(Map<String, Integer> requiredMap) {
            this.requiredMap = requiredMap != null ? new LinkedHashMap<>(requiredMap) : null;
            return this;
        }

        public Builder putAllRequiredMap(Map<String, Integer> requiredMap) {
            if (this.requiredMap == null) {
                this.requiredMap = new LinkedHashMap<>(requiredMap);
            } else {
                this.requiredMap.putAll(requiredMap);
            }
            return this;
        }

        public Builder putRequiredMap(String key, int value) {
           if (this.requiredMap == null) {
               this.requiredMap = new LinkedHashMap<>();
           }
           this.requiredMap.put(key, value);
           return this;
        }

        public Builder defaultMap(Map<String, Integer> defaultMap) {
            this.defaultMap = defaultMap != null ? new LinkedHashMap<>(defaultMap) : null;
            return this;
        }

        public Builder putAllDefaultMap(Map<String, Integer> defaultMap) {
            if (this.defaultMap == null) {
                this.defaultMap = new LinkedHashMap<>(defaultMap);
            } else {
                this.defaultMap.putAll(defaultMap);
            }
            return this;
        }

        public Builder putDefaultMap(String key, int value) {
           if (this.defaultMap == null) {
               this.defaultMap = new LinkedHashMap<>();
           }
           this.defaultMap.put(key, value);
           return this;
        }

        public Builder optionalMap(Map<String, Integer> optionalMap) {
            this.optionalMap = optionalMap != null ? new LinkedHashMap<>(optionalMap) : null;
            return this;
        }

        public Builder putAllOptionalMap(Map<String, Integer> optionalMap) {
            if (this.optionalMap == null) {
                this.optionalMap = new LinkedHashMap<>(optionalMap);
            } else {
                this.optionalMap.putAll(optionalMap);
            }
            return this;
        }

        public Builder putOptionalMap(String key, int value) {
           if (this.optionalMap == null) {
               this.optionalMap = new LinkedHashMap<>();
           }
           this.optionalMap.put(key, value);
           return this;
        }

        public Builder stringWithJsonName(String stringWithJsonName) {
            this.stringWithJsonName = stringWithJsonName;
            return this;
        }

        public Builder defaultTimestamp(Instant defaultTimestamp) {
            this.defaultTimestamp = defaultTimestamp;
            return this;
        }

        public Builder nestedStruct(Nested nestedStruct) {
            this.nestedStruct = nestedStruct;
            return this;
        }

        @Override
        public DemoPojo build() {
            return new DemoPojo(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            // PLACEHOLDER. Needs implementation
            return this;
        }
    }
}

