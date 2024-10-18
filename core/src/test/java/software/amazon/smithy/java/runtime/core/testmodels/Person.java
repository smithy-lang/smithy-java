/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.testmodels;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

public final class Person implements SerializableStruct {

    public static final ShapeId ID = ShapeId.from("smithy.example#Person");
    static final Schema SCHEMA = Schema.structureBuilder(ID)
        .putMember(
            "name",
            PreludeSchemas.STRING,
            new HttpLabelTrait(),
            new RequiredTrait(),
            LengthTrait.builder().max(7L).build()
        )
        .putMember("favoriteColor", PreludeSchemas.STRING)
        .putMember("age", PreludeSchemas.INTEGER, RangeTrait.builder().max(BigDecimal.valueOf(150)).build())
        .putMember("birthday", SharedSchemas.BIRTHDAY)
        .putMember("binary", PreludeSchemas.BLOB)
        .putMember("queryParams", SharedSchemas.MAP_LIST_STRING)
        .build();
    private static final Schema SCHEMA_NAME = SCHEMA.member("name");
    private static final Schema SCHEMA_FAVORITE_COLOR = SCHEMA.member("favoriteColor");
    private static final Schema SCHEMA_AGE = SCHEMA.member("age");
    private static final Schema SCHEMA_BIRTHDAY = SCHEMA.member("birthday");
    private static final Schema SCHEMA_BINARY = SCHEMA.member("binary");
    private static final Schema SCHEMA_QUERY_PARAMS = SCHEMA.member("queryParams");
    private static final Schema SCHEMA_QUERY_PARAMS_KEY = SharedSchemas.MAP_LIST_STRING.member("key");
    private static final Schema SCHEMA_QUERY_PARAMS_VALUE = SharedSchemas.MAP_LIST_STRING.member("value");
    private static final Schema LIST_OF_STRING_MEMBER = SharedSchemas.LIST_OF_STRING.member("member");

    private final String name;
    private final int age;
    private final Instant birthday;
    private final String favoriteColor;
    private final ByteBuffer binary;
    private final Map<String, List<String>> queryParams;

    private Person(Builder builder) {
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

    public ByteBuffer binary() {
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
    public void serialize(ShapeSerializer encoder) {
        encoder.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_NAME, name);
        serializer.writeInteger(SCHEMA_AGE, age);
        if (favoriteColor != null) {
            serializer.writeString(SCHEMA_FAVORITE_COLOR, favoriteColor);
        }
        if (binary != null) {
            serializer.writeBlob(SCHEMA_BINARY, binary.asReadOnlyBuffer());
        }
        if (birthday != null) {
            serializer.writeTimestamp(SCHEMA_BIRTHDAY, birthday);
        }
        if (!queryParams.isEmpty()) {
            serializer.writeMap(SCHEMA_QUERY_PARAMS, this, queryParams.size(), Person::writeQueryParamsMember);
        }
    }

    private static void writeQueryParamsMember(Person shape, MapSerializer serializer) {
        for (var queryParamsEntry : shape.queryParams.entrySet()) {
            serializer.writeEntry(
                SCHEMA_QUERY_PARAMS_KEY,
                queryParamsEntry.getKey(),
                queryParamsEntry.getValue(),
                Person::writeQueryParamsMemberEntry
            );
        }
    }

    private static void writeQueryParamsMemberEntry(List<String> values, ShapeSerializer serializer) {
        serializer.writeList(
            SCHEMA_QUERY_PARAMS_VALUE,
            values,
            values.size(),
            Person::writeQueryParamsMemberEntryElement
        );
    }

    private static void writeQueryParamsMemberEntryElement(List<String> listValue, ShapeSerializer serializer) {
        for (var queryParamsEntryValue : listValue) {
            serializer.writeString(LIST_OF_STRING_MEMBER, queryParamsEntryValue);
        }
    }

    public static final class Builder implements ShapeBuilder<Person> {

        private String name;
        private int age = 0;
        private Instant birthday;
        private String favoriteColor;
        private ByteBuffer binary;
        private Map<String, List<String>> queryParams = Collections.emptyMap();

        private Builder() {}

        @Override
        public Person build() {
            return new Person(this);
        }

        @Override
        public Schema schema() {
            return SCHEMA;
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

        public Builder binary(ByteBuffer binary) {
            this.binary = binary;
            return this;
        }

        public Builder queryParams(Map<String, List<String>> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, this, (builder, member, de) -> {
                switch (member.memberIndex()) {
                    case 0 -> builder.name(de.readString(member));
                    case 1 -> builder.favoriteColor(de.readString(member));
                    case 2 -> builder.age(de.readInteger(member));
                    case 3 -> builder.birthday(de.readTimestamp(member));
                    case 4 -> builder.binary(de.readBlob(member));
                    case 5 -> {
                        Map<String, List<String>> result = new LinkedHashMap<>();
                        de.readStringMap(SCHEMA_QUERY_PARAMS, result, (mapData, key, v) -> {
                            List<String> listValue = mapData.computeIfAbsent(key, k -> new ArrayList<>());
                            v.readList(SharedSchemas.MAP_LIST_STRING.member("member"), listValue, (list, ser) -> {
                                list.add(ser.readString(SharedSchemas.LIST_OF_STRING.member("member")));
                            });
                        });
                        builder.queryParams(result);
                    }
                }
            });
            return this;
        }
    }
}
