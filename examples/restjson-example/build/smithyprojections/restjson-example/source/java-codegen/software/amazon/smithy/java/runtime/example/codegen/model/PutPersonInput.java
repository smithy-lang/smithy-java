/**
 * Header Line 1
 * Header Line 2
 */

package software.amazon.smithy.java.runtime.example.codegen.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
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
            SCHEMA_QUERY_PARAMS
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

    private PutPersonInput(Builder builder) {
        this.name = SmithyBuilder.requiredState("name", builder.name);
        this.favoriteColor = builder.favoriteColor;
        this.age = builder.age;
        this.birthday = builder.birthday;
        this.binary = builder.binary;
        this.notRequiredBool = builder.notRequiredBool;
        this.requiredBool = SmithyBuilder.requiredState("requiredBool", builder.requiredBool);
        this.queryParams = builder.queryParams != null ? Collections.unmodifiableMap(builder.queryParams) : null;
    }

    public String name() {
        return name;
    }

    public String favoriteColor() {
        return favoriteColor;
    }

    public Integer age() {
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

    public Boolean notRequiredBool() {
        return notRequiredBool;
    }

    public boolean requiredBool() {
        return requiredBool;
    }

    public Map<String, List<String>> queryParams() {
        return queryParams != null ? queryParams : Collections.emptyMap();
    }

    public boolean hasQueryParams() {
        return queryParams != null;
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
               && Objects.equals(queryParams, that.queryParams);

    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, favoriteColor, age, birthday, notRequiredBool, requiredBool, queryParams);
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
        private Instant birthday;
        private byte[] binary;
        private Boolean notRequiredBool;
        private boolean requiredBool;
        private Map<String, List<String>> queryParams;

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

        @Override
        public PutPersonInput build() {
            return new PutPersonInput(this);
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, (member, de) -> {
                switch (SCHEMA.lookupMemberIndex(member)) {
                    case 0 -> name(de.readString(member));
                    case 1 -> favoriteColor(de.readString(member));
                    case 2 -> age(de.readInteger(member));
                    case 3 -> birthday(de.readTimestamp(member));
                    case 4 -> binary(de.readBlob(member));
                    case 5 -> notRequiredBool(de.readBoolean(member));
                    case 6 -> requiredBool(de.readBoolean(member));
                    case 7 -> {
                                  Map<String, List<String>> result = new LinkedHashMap<>();
                                  var valueSchema = member.member("value");
                                  de.readStringMap(member, (key, v) -> {
                                      var nestedSchema = valueSchema.member("member");
                                      var resultNested = result.computeIfAbsent(key, k -> new ArrayList<>());
                                      v.readList(valueSchema, nl -> {
                                          resultNested.add(nl.readString(nestedSchema));
                                      });

                                  });
                                  queryParams(result);
                              }
                }
            });
            return this;
        }
    }
}

