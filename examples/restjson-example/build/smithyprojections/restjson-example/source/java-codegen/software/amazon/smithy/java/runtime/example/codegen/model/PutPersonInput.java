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
            SCHEMA_DEFAULT_MAP
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
               && Objects.equals(defaultMap, that.defaultMap);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, favoriteColor, age, birthday, notRequiredBool, requiredBool, queryParams, defaultBoolean, defaultList, defaultMap);
        result = 31 * result + Arrays.hashCode(binary);
        return result;

    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, st -> {
            st.writeString(SCHEMA_NAME, name);
            ShapeSerializer.writeIfNotNull(st, SCHEMA_FAVORITE_COLOR, favoriteColor);
            ShapeSerializer.writeIfNotNull(st, SCHEMA_AGE, age);
            st.writeTimestamp(SCHEMA_BIRTHDAY, birthday);
            ShapeSerializer.writeIfNotNull(st, SCHEMA_BINARY, binary);
            ShapeSerializer.writeIfNotNull(st, SCHEMA_NOT_REQUIRED_BOOL, notRequiredBool);
            st.writeBoolean(SCHEMA_REQUIRED_BOOL, requiredBool);
            if (queryParams != null) {
                st.writeMap(SCHEMA_QUERY_PARAMS, stm -> {
                    var SCHEMA_QUERY_PARAMS_KEY = SCHEMA_QUERY_PARAMS.member("key");
                    var SCHEMA_QUERY_PARAMS_VALUE = SCHEMA_QUERY_PARAMS.member("value");
                    queryParams.forEach((k, queryParamsVal) -> stm.writeEntry(SCHEMA_QUERY_PARAMS_KEY, k, stmv -> {
                        var SCHEMA_QUERY_PARAMS_VALUE_MEMBER = SCHEMA_QUERY_PARAMS_VALUE.member("member");
                        stmv.writeList(SCHEMA_QUERY_PARAMS_VALUE, stmvl -> queryParamsVal.forEach(queryParamsValElem -> {
                            stmvl.writeString(SCHEMA_QUERY_PARAMS_VALUE_MEMBER, queryParamsValElem);
                        }));
                    }));
                });
            };
            st.writeBoolean(SCHEMA_DEFAULT_BOOLEAN, defaultBoolean);
            var SCHEMA_DEFAULT_LIST_MEMBER = SCHEMA_DEFAULT_LIST.member("member");
            st.writeList(SCHEMA_DEFAULT_LIST, stl -> defaultList.forEach(defaultListElem -> {
                stl.writeString(SCHEMA_DEFAULT_LIST_MEMBER, defaultListElem);
            }));
            st.writeMap(SCHEMA_DEFAULT_MAP, stm -> {
                var SCHEMA_DEFAULT_MAP_KEY = SCHEMA_DEFAULT_MAP.member("key");
                var SCHEMA_DEFAULT_MAP_VALUE = SCHEMA_DEFAULT_MAP.member("value");
                defaultMap.forEach((k, defaultMapVal) -> stm.writeEntry(SCHEMA_DEFAULT_MAP_KEY, k, stmv -> {
                    stmv.writeString(SCHEMA_DEFAULT_MAP_VALUE, defaultMapVal);
                }));
            });
        });
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

        @Override
        public PutPersonInput build() {
            return new PutPersonInput(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            // PLACEHOLDER. Needs implementation
            return this;
        }
    }
}

