/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.testmodels;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.schema.ShapeBuilder;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.JsonNameTrait;

public final class Person implements SerializableShape {

    public static final ShapeId ID = ShapeId.from("smithy.example#Person");
    private static final Schema SCHEMA_NAME = Schema.memberBuilder(0, "name", PreludeSchemas.STRING)
        .id(ID)
        .build();
    private static final Schema SCHEMA_AGE = Schema.memberBuilder(1, "age", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(new JsonNameTrait("Age"))
        .build();
    private static final Schema SCHEMA_BIRTHDAY = Schema.memberBuilder(2, "birthday", SharedSchemas.BIRTHDAY)
        .id(ID)
        .build();
    private static final Schema SCHEMA_BINARY = Schema.memberBuilder(3, "binary", PreludeSchemas.BLOB)
        .id(ID)
        .build();
    private static final Schema SCHEMA_QUERY_PARAMS = Schema
        .memberBuilder(4, "tags", SharedSchemas.MAP_LIST_STRING)
        .id(ID)
        .build();
    static final Schema SCHEMA = Schema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(
            SCHEMA_NAME,
            SCHEMA_AGE,
            SCHEMA_BIRTHDAY,
            SCHEMA_BINARY,
            SCHEMA_QUERY_PARAMS
        )
        .build();

    private final String name;
    private final int age;
    private final Instant birthday;
    private final byte[] binary;
    private final Map<String, List<String>> tags;

    private Person(Builder builder) {
        this.name = builder.name;
        this.age = builder.age;
        this.birthday = builder.birthday;
        this.binary = builder.binary;
        this.tags = builder.tags;
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

    public Map<String, List<String>> tags() {
        return tags;
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, st -> {
            st.writeString(SCHEMA_NAME, name);
            st.writeInteger(SCHEMA_AGE, age);
            ShapeSerializer.writeIfNotNull(st, SCHEMA_BIRTHDAY, birthday);
            ShapeSerializer.writeIfNotNull(st, SCHEMA_BINARY, binary);
            if (!tags.isEmpty()) {
                st.writeMap(SCHEMA_QUERY_PARAMS, m -> {
                    var keyMember = SharedSchemas.MAP_LIST_STRING.member("key");
                    var valueMember = SharedSchemas.MAP_LIST_STRING.member("value");
                    tags.forEach((k, v) -> m.writeEntry(keyMember, k, mv -> {
                        mv.writeList(valueMember, mvl -> {
                            v.forEach(value -> mvl.writeString(SharedSchemas.LIST_OF_STRING.member("member"), value));
                        });
                    }));
                });
            }
        });
    }

    public static final class Builder implements ShapeBuilder<Person> {

        private String name;
        private int age = 0;
        private Instant birthday;
        private byte[] binary;
        private Map<String, List<String>> tags = Collections.emptyMap();

        private Builder() {
        }

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

        public Builder binary(byte[] binary) {
            this.binary = binary;
            return this;
        }

        public Builder tags(Map<String, List<String>> tags) {
            this.tags = tags;
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, (member, de) -> {
                switch (SCHEMA.lookupMemberIndex(member)) {
                    case 0 -> name(de.readString(member));
                    case 1 -> age(de.readInteger(member));
                    case 2 -> birthday(de.readTimestamp(member));
                    case 3 -> binary(de.readBlob(member));
                    case 4 -> {
                        Map<String, List<String>> result = new LinkedHashMap<>();
                        de.readStringMap(SCHEMA_QUERY_PARAMS, (key, v) -> {
                            v.readList(SharedSchemas.MAP_LIST_STRING.member("member"), list -> {
                                result.computeIfAbsent(key, k -> new ArrayList<>())
                                    .add(list.readString(SharedSchemas.LIST_OF_STRING.member("member")));
                            });
                        });
                        tags(result);
                    }
                }
            });
            return this;
        }
    }
}
