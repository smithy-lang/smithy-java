/**
 * Header Line 1
 * Header Line 2
 */

package software.amazon.smithy.java.runtime.example.codegen.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyBuilder;
import software.amazon.smithy.utils.SmithyGenerated;
import software.amazon.smithy.utils.SmithyUnstableApi;


@SmithyGenerated
public final class PutPersonInput implements SerializableShape {
    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonInput");

    private static final SdkSchema SCHEMA_NAME = SdkSchema.memberBuilder(0, "name", PreludeSchemas.STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_FAVORITE_COLOR = SdkSchema.memberBuilder(1, "favoriteColor", PreludeSchemas.STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_AGE = SdkSchema.memberBuilder(2, "age", PreludeSchemas.INTEGER)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_BIRTHDAY = SdkSchema.memberBuilder(3, "birthday", SharedSchemas.BIRTHDAY)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_BINARY = SdkSchema.memberBuilder(4, "binary", PreludeSchemas.BLOB)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_NOT_REQUIRED_BOOL = SdkSchema.memberBuilder(5, "notRequiredBool", PreludeSchemas.BOOLEAN)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_REQUIRED_BOOL = SdkSchema.memberBuilder(6, "requiredBool", PreludeSchemas.BOOLEAN)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_QUERY_PARAMS = SdkSchema.memberBuilder(7, "queryParams", SharedSchemas.MAP_LIST_STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_BOOLEAN = SdkSchema.memberBuilder(8, "defaultBoolean", PreludeSchemas.BOOLEAN)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_LIST = SdkSchema.memberBuilder(9, "defaultList", SharedSchemas.LIST_OF_STRING)
        .id(ID)
        .build();

    private static final SdkSchema SCHEMA_DEFAULT_MAP = SdkSchema.memberBuilder(10, "defaultMap", SharedSchemas.MAP_STRING_STRING)
        .id(ID)
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
    private final int age;
    private final Instant birthday;
    private final byte[] binary;
    private final boolean notRequiredBool;
    private final Boolean requiredBool;
    private final java.util.Map<String, List<String>> queryParams;
    private final Boolean defaultBoolean;
    private final List<String> defaultList;
    private final java.util.Map<String, String> defaultMap;

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

    public java.util.Map<String, List<String>> queryParams() {
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

    public java.util.Map<String, String> defaultMap() {
        return defaultMap != null ? defaultMap : Collections.emptyMap();
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
               && Objects.equals(requiredBool, that.requiredBool)
               && Objects.equals(queryParams, that.queryParams)
               && Objects.equals(defaultBoolean, that.defaultBoolean)
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
        private java.util.Map<String, List<String>> queryParams;
        private Boolean defaultBoolean = true;
        private List<String> defaultList = new ArrayList<>();
        private java.util.Map<String, String> defaultMap = new LinkedHashMap<>();

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

        public Builder queryParams(java.util.Map<String, List<String>> queryParams) {
            this.queryParams = queryParams != null ? new LinkedHashMap<>(queryParams) : null;
            return this;
        }

        public Builder putAllQueryParams(java.util.Map<String, List<String>> queryParams) {
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

        public Builder defaultMap(java.util.Map<String, String> defaultMap) {
            this.defaultMap = defaultMap != null ? new LinkedHashMap<>(defaultMap) : null;
            return this;
        }

        public Builder putAllDefaultMap(java.util.Map<String, String> defaultMap) {
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

