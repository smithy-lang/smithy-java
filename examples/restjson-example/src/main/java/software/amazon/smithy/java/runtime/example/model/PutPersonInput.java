/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.HttpQueryParamsTrait;
import software.amazon.smithy.model.traits.HttpQueryTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

// Example of a potentially generated shape.
public final class PutPersonInput implements SerializableShape {

    public static final ShapeId ID = ShapeId.from("smithy.example#PutPersonInput");
    private static final SdkSchema SCHEMA_NAME = SdkSchema.memberBuilder(0, "name", SharedSchemas.STRING)
            .id(ID)
            .traits(new HttpLabelTrait(), new RequiredTrait())
            .build();
    private static final SdkSchema SCHEMA_FAVORITE_COLOR = SdkSchema
            .memberBuilder(1, "favoriteColor", SharedSchemas.STRING)
            .id(ID)
            .traits(new HttpQueryTrait("favoriteColor"))
            .build();
    private static final SdkSchema SCHEMA_AGE = SdkSchema.memberBuilder(2, "age", SharedSchemas.INTEGER)
            .id(ID)
            .traits(new JsonNameTrait("Age"))
            .build();
    private static final SdkSchema SCHEMA_BIRTHDAY = SdkSchema.memberBuilder(3, "birthday", SharedSchemas.BIRTHDAY)
            .id(ID)
            .build();
    private static final SdkSchema SCHEMA_BINARY = SdkSchema.memberBuilder(4, "binary", SharedSchemas.BLOB)
            .id(ID)
            .build();
    private static final SdkSchema SCHEMA_QUERY_PARAMS = SdkSchema
            .memberBuilder(5, "queryParams", SharedSchemas.MAP_LIST_STRING)
            .id(ID)
            .traits(new HttpQueryParamsTrait())
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
                    SCHEMA_QUERY_PARAMS
            )
            .build();

    private final String name;
    private final int age;
    private final Instant birthday;
    private final String favoriteColor;
    private final byte[] binary;
    private final Map<String, List<String>> queryParams;

    private PutPersonInput(Builder builder) {
        this.name = builder.name;
        this.age = builder.age;
        this.birthday = builder.birthday;
        this.favoriteColor = builder.favoriteColor;
        this.binary = builder.binary;
        this.queryParams = builder.queryParams;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int age() {
        return age;
    }

    public String name() {
        return name;
    }

    public Instant birthday() {
        return birthday;
    }

    public byte[] binary() {
        return binary;
    }

    public Map<String, List<String>> queryParams() {
        return queryParams;
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.beginStruct(SCHEMA, st -> {
            st.stringMember(SCHEMA_NAME, name);
            st.integerMember(SCHEMA_AGE, age);
            st.timestampMemberIf(SCHEMA_BIRTHDAY, birthday);
            st.stringMemberIf(SCHEMA_FAVORITE_COLOR, favoriteColor);
            st.blobMemberIf(SCHEMA_BINARY, binary);
            st.mapMember(SCHEMA_QUERY_PARAMS, m -> {
                queryParams.forEach((k, v) -> m.entry(k, mv -> {
                    mv.beginList(SharedSchemas.MAP_LIST_STRING.member("value"), mvl -> {
                        v.forEach(value -> mvl.writeString(SharedSchemas.LIST_OF_STRING.member("member"), value));
                    });
                }));
            });
        });
    }

    public static final class Builder implements SdkShapeBuilder<PutPersonInput> {

        private String name;
        private int age = 0;
        private Instant birthday;
        private String favoriteColor;
        private byte[] binary;
        private Map<String, List<String>> queryParams = Collections.emptyMap();

        private Builder() {
        }

        @Override
        public PutPersonInput build() {
            return new PutPersonInput(this);
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder birthday(Instant birthday) {
            this.birthday = birthday;
            return this;
        }

        public Builder favoriteColor(String favoriteColor) {
            this.favoriteColor = favoriteColor;
            return this;
        }

        public Builder binary(byte[] binary) {
            this.binary = binary;
            return this;
        }

        public Builder queryParams(Map<String, List<String>> queryParams) {
            this.queryParams = queryParams;
            return this;
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
                    case 5 -> {
                        Map<String, List<String>> result = new LinkedHashMap<>();
                        de.readStringMap(SCHEMA_QUERY_PARAMS, (key, v) -> {
                            v.readList(SharedSchemas.MAP_LIST_STRING.member("member"), list -> {
                                result.computeIfAbsent(key, k -> new ArrayList<>())
                                        .add(list.readString(SharedSchemas.LIST_OF_STRING.member("member")));
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
