/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.testmodels;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpLabelTrait;
import software.amazon.smithy.model.traits.LengthTrait;
import software.amazon.smithy.model.traits.RangeTrait;
import software.amazon.smithy.model.traits.RequiredTrait;

public final class Person implements SerializableShape {

    public static final ShapeId ID = ShapeId.from("smithy.example#Person");
    private static final SdkSchema SCHEMA_NAME = SdkSchema.memberBuilder("name", PreludeSchemas.STRING)
        .id(ID)
        .traits(new HttpLabelTrait(), new RequiredTrait(), LengthTrait.builder().max(7L).build())
        .build();
    private static final SdkSchema SCHEMA_FAVORITE_COLOR = SdkSchema
        .memberBuilder("favoriteColor", PreludeSchemas.STRING)
        .id(ID)
        .build();
    private static final SdkSchema SCHEMA_AGE = SdkSchema.memberBuilder("age", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(RangeTrait.builder().max(BigDecimal.valueOf(150)).build())
        .build();
    private static final SdkSchema SCHEMA_BIRTHDAY = SdkSchema.memberBuilder("birthday", SharedSchemas.BIRTHDAY)
        .id(ID)
        .build();
    private static final SdkSchema SCHEMA_BINARY = SdkSchema.memberBuilder("binary", PreludeSchemas.BLOB)
        .id(ID)
        .build();
    private static final SdkSchema SCHEMA_QUERY_PARAMS = SdkSchema
        .memberBuilder("queryParams", SharedSchemas.MAP_LIST_STRING)
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
            SCHEMA_QUERY_PARAMS
        )
        .build();
    private static final SdkSchema SCHEMA_QUERY_PARAMS_KEY = SharedSchemas.MAP_LIST_STRING.member("key");
    private static final SdkSchema SCHEMA_QUERY_PARAMS_VALUE = SharedSchemas.MAP_LIST_STRING.member("value");
    private static final SdkSchema LIST_OF_STRING_MEMBER = SharedSchemas.LIST_OF_STRING.member("member");

    private final String name;
    private final int age;
    private final Instant birthday;
    private final String favoriteColor;
    private final byte[] binary;
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
        serializer.writeStruct(SCHEMA, this, Person::writeShape);
    }

    private static void writeShape(Person shape, ShapeSerializer serializer) {
        serializer.writeString(SCHEMA_NAME, shape.name);
        serializer.writeInteger(SCHEMA_AGE, shape.age);
        ShapeSerializer.writeIfNotNull(serializer, SCHEMA_FAVORITE_COLOR, shape.favoriteColor);
        ShapeSerializer.writeIfNotNull(serializer, SCHEMA_BINARY, shape.binary);
        ShapeSerializer.writeIfNotNull(serializer, SCHEMA_BIRTHDAY, shape.birthday);
        if (!shape.queryParams.isEmpty()) {
            serializer.writeMap(SCHEMA_QUERY_PARAMS, shape, Person::writeQueryParamsMember);
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
        serializer.writeList(SCHEMA_QUERY_PARAMS_VALUE, values, Person::writeQueryParamsMemberEntryElement);
    }

    private static void writeQueryParamsMemberEntryElement(List<String> listValue, ShapeSerializer serializer) {
        for (var queryParamsEntryValue : listValue) {
            serializer.writeString(LIST_OF_STRING_MEMBER, queryParamsEntryValue);
        }
    }

    public static final class Builder implements SdkShapeBuilder<Person> {

        private String name;
        private int age = 0;
        private Instant birthday;
        private String favoriteColor;
        private byte[] binary;
        private Map<String, List<String>> queryParams = Collections.emptyMap();

        private Builder() {}

        @Override
        public Person build() {
            return new Person(this);
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
                    default -> {
                        // TODO: Log periodically
                    }
                }
            });
            return this;
        }
    }
}
