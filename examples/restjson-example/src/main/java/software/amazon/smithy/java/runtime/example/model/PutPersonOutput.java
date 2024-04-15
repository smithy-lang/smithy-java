/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.example.model;

import java.time.Instant;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.HttpHeaderTrait;
import software.amazon.smithy.model.traits.HttpResponseCodeTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.RequiredTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

// Example of a potentially generated shape.
public final class PutPersonOutput implements SerializableShape {

    static final ShapeId ID = ShapeId.from("smithy.example#PutPersonOutput");
    private static final SdkSchema SCHEMA_NAME = SdkSchema.memberBuilder(0, "name", PreludeSchemas.STRING)
        .id(ID)
        .traits(new RequiredTrait())
        .build();
    private static final SdkSchema SCHEMA_FAVORITE_COLOR = SdkSchema
        .memberBuilder(1, "favoriteColor", PreludeSchemas.STRING)
        .id(ID)
        .traits(new HttpHeaderTrait("X-Favorite-Color"))
        .build();
    private static final SdkSchema SCHEMA_AGE = SdkSchema.memberBuilder(2, "age", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(new JsonNameTrait("Age"))
        .build();
    private static final SdkSchema SCHEMA_BIRTHDAY = SdkSchema.memberBuilder(3, "birthday", SharedSchemas.BIRTHDAY)
        .id(ID)
        .traits(new TimestampFormatTrait(TimestampFormatTrait.DATE_TIME))
        .build();
    private static final SdkSchema SCHEMA_STATUS = SdkSchema.memberBuilder(4, "status", PreludeSchemas.INTEGER)
        .id(ID)
        .traits(new HttpResponseCodeTrait())
        .build();
    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(SCHEMA_NAME, SCHEMA_FAVORITE_COLOR, SCHEMA_AGE, SCHEMA_BIRTHDAY, SCHEMA_STATUS)
        .build();

    private final String name;
    private final int age;
    private final Instant birthday;
    private final String favoriteColor;
    private final int status;

    private PutPersonOutput(Builder builder) {
        this.name = builder.name;
        this.age = builder.age;
        this.birthday = builder.birthday;
        this.favoriteColor = builder.favoriteColor;
        this.status = builder.status;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getAge() {
        return age;
    }

    public String getName() {
        return name;
    }

    public Instant getBirthday() {
        return birthday;
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, st -> {
            ShapeSerializer.writeIfNotNull(st, SCHEMA_NAME, name);
            st.writeInteger(SCHEMA_AGE, age);
            ShapeSerializer.writeIfNotNull(st, SCHEMA_BIRTHDAY, birthday);
            ShapeSerializer.writeIfNotNull(st, SCHEMA_FAVORITE_COLOR, favoriteColor);
            st.writeInteger(SCHEMA_STATUS, status);
        });
    }

    public static final class Builder implements SdkShapeBuilder<PutPersonOutput> {

        private String name;
        private int age = 0;
        private Instant birthday;
        private String favoriteColor;
        private int status;

        private Builder() {
        }

        @Override
        public PutPersonOutput build() {
            return new PutPersonOutput(this);
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

        public Builder status(int status) {
            this.status = status;
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            decoder.readStruct(SCHEMA, (member, de) -> {
                int index = member.memberIndex() == -1
                    ? SCHEMA.member(member.memberName()).memberIndex()
                    : member.memberIndex();
                switch (index) {
                    case 0 -> name(de.readString(member));
                    case 1 -> favoriteColor(de.readString(member));
                    case 2 -> age(de.readInteger(member));
                    case 3 -> birthday(de.readTimestamp(member));
                    case 4 -> status(de.readInteger(member));
                }
            });
            return this;
        }
    }
}
