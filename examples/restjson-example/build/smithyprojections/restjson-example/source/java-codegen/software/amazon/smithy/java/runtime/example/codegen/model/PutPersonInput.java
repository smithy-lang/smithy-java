/*
 * Example file license header.
 * File header line two
 */


package software.amazon.smithy.java.runtime.example.codegen.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpQueryParamsTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.SmithyGenerated;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyGenerated
public final class PutPersonInput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonInput");

    private static final SdkSchema SCHEMA_NAME = SdkSchema.memberBuilder("name", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new HttpLabelTrait(),
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_FAVORITE_COLOR = SdkSchema.memberBuilder("favoriteColor", PreludeSchemas.STRING)
        .id(ID)
        .traits(
            new HttpQueryTrait("favoriteColor")
        )
        .build();

    private static final SdkSchema SCHEMA_AGE = SdkSchema.memberBuilder("age", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(
            new JsonNameTrait("Age")
        )
        .build();

    private static final SdkSchema SCHEMA_BIRTHDAY = SdkSchema.memberBuilder("birthday", SharedSchemas.BIRTHDAY)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from("1985-04-12T23:20:50.52Z")
            )
        )
        .build();

    private static final SdkSchema SCHEMA_BINARY = SdkSchema.memberBuilder("binary", PreludeSchemas.BLOB)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_NOT_REQUIRED_BOOL = SdkSchema.memberBuilder("notRequiredBool", PreludeSchemas.BOOLEAN)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_REQUIRED_BOOL = SdkSchema.memberBuilder("requiredBool", PreludeSchemas.BOOLEAN)
        .id(ID)
        .traits(
            new RequiredTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_QUERY_PARAMS = SdkSchema.memberBuilder("queryParams", SharedSchemas.MAP_LIST_STRING)
        .id(ID)
        .traits(
            new HttpQueryParamsTrait()
        )
        .build();

    private static final SdkSchema SCHEMA_QUERY_PARAMS_KEY = SCHEMA_QUERY_PARAMS.member("key");
    private static final SdkSchema SCHEMA_QUERY_PARAMS_VALUE = SCHEMA_QUERY_PARAMS.member("value");
    private static final SdkSchema SCHEMA_QUERY_PARAMS_VALUE_MEMBER = SCHEMA_QUERY_PARAMS_VALUE.member("member");
    private static final SdkSchema SCHEMA_DEFAULT_BOOLEAN = SdkSchema.memberBuilder("defaultBoolean", PreludeSchemas.BOOLEAN)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.from(true)
            )
        )
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_LIST = SdkSchema.memberBuilder("defaultList", SharedSchemas.LIST_OF_STRING)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                ArrayNode.builder()
                    .build()
            )
        )
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_LIST_MEMBER = SCHEMA_DEFAULT_LIST.member("member");
    private static final SdkSchema SCHEMA_DEFAULT_MAP = SdkSchema.memberBuilder("defaultMap", SharedSchemas.MAP_STRING_STRING)
        .id(ID)
        .traits(
            new DefaultTrait.Provider().createTrait(
                ShapeId.from("smithy.api#default"),
                Node.objectNodeBuilder()
                    .build()
            )
        )
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_MAP_KEY = SCHEMA_DEFAULT_MAP.member("key");
    private static final SdkSchema SCHEMA_DEFAULT_MAP_VALUE = SCHEMA_DEFAULT_MAP.member("value");
    private static final SdkSchema SCHEMA_NESTED_MAP = SdkSchema.memberBuilder("nestedMap", SharedSchemas.MAP_OF_STRING_MAP)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_NESTED_MAP_KEY = SCHEMA_NESTED_MAP.member("key");
    private static final SdkSchema SCHEMA_NESTED_MAP_VALUE = SCHEMA_NESTED_MAP.member("value");
    private static final SdkSchema SCHEMA_NESTED_MAP_VALUE_KEY = SCHEMA_NESTED_MAP_VALUE.member("key");
    private static final SdkSchema SCHEMA_NESTED_MAP_VALUE_VALUE = SCHEMA_NESTED_MAP_VALUE.member("value");
    private static final SdkSchema SCHEMA_NESTED_LIST = SdkSchema.memberBuilder("nestedList", SharedSchemas.LIST_OF_STRING_LIST)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_NESTED_LIST_MEMBER = SCHEMA_NESTED_LIST.member("member");
    private static final SdkSchema SCHEMA_NESTED_LIST_MEMBER_MEMBER = SCHEMA_NESTED_LIST_MEMBER.member("member");
    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_NAME,
            SCHEMA_FAVORITE_COLOR,
            SCHEMA_AGE,
            SCHEMA_BIRTHDAY,
            SCHEMA_BINARY,
            SCHEMA_NOT_REQUIRED_BOOL,
            SCHEMA_REQUIRED_BOOL,
            SCHEMA_QUERY_PARAMS,
            SCHEMA_DEFAULT_BOOLEAN,
            SCHEMA_DEFAULT_LIST,
            SCHEMA_DEFAULT_MAP,
            SCHEMA_NESTED_MAP,
            SCHEMA_NESTED_LIST
        )
        .build();

    private final String name;
    private final String favoriteColor;
    private final Integer age;
    private final Instant birthday;
    private final byte[] binary;
    private final Boolean notRequiredBool;
    private final boolean requiredBool;
    private final Map<String, List<String>> queryParams;
    private final boolean defaultBoolean;
    private final List<String> defaultList;
    private final Map<String, String> defaultMap;
    private final Map<String, Map<String, String>> nestedMap;
    private final List<List<String>> nestedList;

    private PutPersonInput(Builder builder) {
        this.name = SmithyBuilder.requiredState("name", builder.name);
        this.favoriteColor = builder.favoriteColor;
        this.age = builder.age;
        this.birthday = SmithyBuilder.requiredState("birthday", builder.birthday);
        this.binary = builder.binary;
        this.notRequiredBool = builder.notRequiredBool;
        this.requiredBool = SmithyBuilder.requiredState("requiredBool", builder.requiredBool);
        this.queryParams = builder.queryParams != null ? Collections.unmodifiableMap(builder.queryParams) : null;
        this.defaultBoolean = SmithyBuilder.requiredState("defaultBoolean", builder.defaultBoolean);
        this.defaultList = Collections.unmodifiableList(builder.defaultList);
        this.defaultMap = Collections.unmodifiableMap(builder.defaultMap);
        this.nestedMap = builder.nestedMap != null ? Collections.unmodifiableMap(builder.nestedMap) : null;
        this.nestedList = builder.nestedList != null ? Collections.unmodifiableList(builder.nestedList) : null;
    }

    public String name() {
        return name;
    }

    public String favoriteColor() {
        return favoriteColor;
    }

    public int age() {
        return age;
    }

    public Instant birthday() {
        return birthday;
    }

    /**
     * This is a binary blob! Yay!
     * It has quite a few documentation traits added to it.
     *
     * @see <a href="https://www.example.com/">Homepage</a>
     * @see <a href="https://www.example.com/api-ref">API Reference</a>
     * @since 1.3.4.5.6
     * @deprecated As of 1.4.5.6. This shape is no longer used.
     */
    @SmithyUnstableApi
    @Deprecated(since = "1.4.5.6")
    public byte[] binary() {
        return binary;
    }

    public boolean notRequiredBool() {
        return notRequiredBool;
    }

    public Boolean requiredBool() {
        return requiredBool;
    }

    public Map<String, List<String>> queryParams() {
        return queryParams != null ? queryParams : Collections.emptyMap();
    }

    public boolean hasQueryParams() {
        return queryParams != null;
    }

    public Boolean defaultBoolean() {
        return defaultBoolean;
    }

    public List<String> defaultList() {
        return defaultList;
    }

    public boolean hasDefaultList() {
        return defaultList != null;
    }

    public Map<String, String> defaultMap() {
        return defaultMap;
    }

    public boolean hasDefaultMap() {
        return defaultMap != null;
    }

    public Map<String, Map<String, String>> nestedMap() {
        return nestedMap != null ? nestedMap : Collections.emptyMap();
    }

    public boolean hasNestedMap() {
        return nestedMap != null;
    }

    public List<List<String>> nestedList() {
        return nestedList != null ? nestedList : Collections.emptyList();
    }

    public boolean hasNestedList() {
        return nestedList != null;
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
        PutPersonInput that = (PutPersonInput) other;
        return Objects.equals(name, that.name)
               && Objects.equals(favoriteColor, that.favoriteColor)
               && Objects.equals(age, that.age)
               && Objects.equals(birthday, that.birthday)
               && Arrays.equals(binary, that.binary)
               && Objects.equals(notRequiredBool, that.notRequiredBool)
               && requiredBool == that.requiredBool
               && Objects.equals(queryParams, that.queryParams)
               && defaultBoolean == that.defaultBoolean
               && Objects.equals(defaultList, that.defaultList)
               && Objects.equals(defaultMap, that.defaultMap)
               && Objects.equals(nestedMap, that.nestedMap)
               && Objects.equals(nestedList, that.nestedList);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, favoriteColor, age, birthday, notRequiredBool, requiredBool, queryParams, defaultBoolean, defaultList, defaultMap, nestedMap, nestedList);
        result = 31 * result + Arrays.hashCode(binary);
        return result;

    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        // Placeholder. Do nothing
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link PutPersonInput}.
     */
    public static final class Builder implements SdkShapeBuilder<PutPersonInput> {
        private String name;
        private String favoriteColor;
        private Integer age;
        private Instant birthday = Instant.parse("1985-04-12T23:20:50.52Z");
        private byte[] binary;
        private Boolean notRequiredBool;
        private boolean requiredBool;
        private Map<String, List<String>> queryParams;
        private boolean defaultBoolean = true;
        private List<String> defaultList = new ArrayList<>();
        private Map<String, String> defaultMap = new LinkedHashMap<>();
        private Map<String, Map<String, String>> nestedMap;
        private List<List<String>> nestedList;

        private Builder() {}

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder favoriteColor(String favoriteColor) {
            this.favoriteColor = favoriteColor;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public Builder birthday(Instant birthday) {
            this.birthday = birthday;
            return this;
        }

        public Builder binary(byte[] binary) {
            this.binary = binary;
            return this;
        }

        public Builder notRequiredBool(boolean notRequiredBool) {
            this.notRequiredBool = notRequiredBool;
            return this;
        }

        public Builder requiredBool(boolean requiredBool) {
            this.requiredBool = requiredBool;
            return this;
        }

        public Builder queryParams(Map<String, List<String>> queryParams) {
            this.queryParams = queryParams != null ? new LinkedHashMap<>(queryParams) : null;
            return this;
        }

        public Builder putAllQueryParams(Map<String, List<String>> queryParams) {
            if (this.queryParams == null) {
                this.queryParams = new LinkedHashMap<>(queryParams);
            } else {
                this.queryParams.putAll(queryParams);
            }
            return this;
        }

        public Builder putQueryParams(String key, List<String> value) {
           if (this.queryParams == null) {
               this.queryParams = new LinkedHashMap<>();
           }
           this.queryParams.put(key, value);
           return this;
        }

        public Builder defaultBoolean(boolean defaultBoolean) {
            this.defaultBoolean = defaultBoolean;
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

        public Builder defaultMap(Map<String, String> defaultMap) {
            this.defaultMap = defaultMap != null ? new LinkedHashMap<>(defaultMap) : null;
            return this;
        }

        public Builder putAllDefaultMap(Map<String, String> defaultMap) {
            if (this.defaultMap == null) {
                this.defaultMap = new LinkedHashMap<>(defaultMap);
            } else {
                this.defaultMap.putAll(defaultMap);
            }
            return this;
        }

        public Builder putDefaultMap(String key, String value) {
           if (this.defaultMap == null) {
               this.defaultMap = new LinkedHashMap<>();
           }
           this.defaultMap.put(key, value);
           return this;
        }

        public Builder nestedMap(Map<String, Map<String, String>> nestedMap) {
            this.nestedMap = nestedMap != null ? new LinkedHashMap<>(nestedMap) : null;
            return this;
        }

        public Builder putAllNestedMap(Map<String, Map<String, String>> nestedMap) {
            if (this.nestedMap == null) {
                this.nestedMap = new LinkedHashMap<>(nestedMap);
            } else {
                this.nestedMap.putAll(nestedMap);
            }
            return this;
        }

        public Builder putNestedMap(String key, Map<String, String> value) {
           if (this.nestedMap == null) {
               this.nestedMap = new LinkedHashMap<>();
           }
           this.nestedMap.put(key, value);
           return this;
        }

        public Builder nestedList(Collection<List<String>> nestedList) {
            this.nestedList = nestedList != null ? new ArrayList<>(nestedList) : null;
            return this;
        }

        public Builder addAllNestedList(Collection<List<String>> nestedList) {
            if (this.nestedList == null) {
                this.nestedList = new ArrayList<>(nestedList);
            } else {
                this.nestedList.addAll(nestedList);
            }
            return this;
        }

        public Builder nestedList(List<String> nestedList) {
            if (this.nestedList == null) {
                this.nestedList = new ArrayList<>();
            }
            this.nestedList.add(nestedList);
            return this;
        }

        public Builder nestedList(List<String>... nestedList) {
            if (this.nestedList == null) {
                this.nestedList = new ArrayList<>();
            }
            Collections.addAll(this.nestedList, nestedList);
            return this;
        }

        @Override
        public PutPersonInput build() {
            return new PutPersonInput(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, (member, de) -> {
                switch (member.memberIndex()) {
                    case 0 -> name(de.readString(member));
                    case 1 -> favoriteColor(de.readString(member));
                    case 2 -> age(de.readInteger(member));
                    case 3 -> birthday(de.readTimestamp(member));
                    case 4 -> binary(de.readBlob(member));
                    case 5 -> notRequiredBool(de.readBoolean(member));
                    case 6 -> requiredBool(de.readBoolean(member));
                    case 7 -> queryParams(SharedSchemas.deserializeMapListString(member, de));
                    case 8 -> defaultBoolean(de.readBoolean(member));
                    case 9 -> defaultList(SharedSchemas.deserializeListOfString(member, de));
                    case 10 -> defaultMap(SharedSchemas.deserializeMapStringString(member, de));
                    case 11 -> nestedMap(SharedSchemas.deserializeMapOfStringMap(member, de));
                    case 12 -> nestedList(SharedSchemas.deserializeListOfStringList(member, de));
                }
            });
            return this;
        }
    }
}

