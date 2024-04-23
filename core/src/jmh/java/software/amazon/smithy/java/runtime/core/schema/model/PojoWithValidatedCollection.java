/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.RequiredTrait;

public final class PojoWithValidatedCollection implements SerializableShape {

    public static final ShapeId ID = ShapeId.from("smithy.example#PojoWithValidatedCollection");

    private static final SdkSchema MAP_OF_VALIDATED_POJO = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id("smithy.example#MapOfValidatedPojo")
        .members(
            SdkSchema.memberBuilder("key", PreludeSchemas.STRING),
            SdkSchema.memberBuilder("value", ValidatedPojo.SCHEMA)
        )
        .build();
    private static final SdkSchema LIST_OF_VALIDATED_POJO = SdkSchema.builder()
        .type(ShapeType.LIST)
        .id("smithy.example#ListOfValidatedPojo")
        .members(SdkSchema.memberBuilder("member", ValidatedPojo.SCHEMA))
        .build();
    private static final SdkSchema SCHEMA_MAP = SdkSchema.memberBuilder("map", MAP_OF_VALIDATED_POJO)
        .id(ID)
        .traits(new RequiredTrait())
        .build();
    private static final SdkSchema SCHEMA_LIST = SdkSchema
        .memberBuilder("list", LIST_OF_VALIDATED_POJO)
        .id(ID)
        .traits(new RequiredTrait())
        .build();
    static final SdkSchema SCHEMA = SdkSchema.builder()
        .id(ID)
        .type(ShapeType.STRUCTURE)
        .members(SCHEMA_MAP, SCHEMA_LIST)
        .build();

    private final Map<String, ValidatedPojo> map;
    private final List<ValidatedPojo> list;

    private PojoWithValidatedCollection(Builder builder) {
        this.map = builder.map;
        this.list = builder.list;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Map<String, ValidatedPojo> map() {
        return map;
    }

    public List<ValidatedPojo> list() {
        return list;
    }

    @Override
    public String toString() {
        return ToStringSerializer.serialize(this);
    }

    @Override
    public void serialize(ShapeSerializer serializer) {
        serializer.writeStruct(SCHEMA, st -> {
            st.writeList(SCHEMA_LIST, ser -> {
                for (var entry : list) {
                    entry.serialize(ser);
                }
            });
            st.writeMap(SCHEMA_MAP, ser -> {
                var keyMember = SCHEMA_MAP.member("key");
                for (var entry : map.entrySet()) {
                    ser.writeEntry(keyMember, entry.getKey(), entry.getValue()::serialize);
                }
            });
        });
    }

    public static final class Builder implements SdkShapeBuilder<PojoWithValidatedCollection> {

        private Map<String, ValidatedPojo> map = Collections.emptyMap();
        private List<ValidatedPojo> list = Collections.emptyList();

        private Builder() {}

        @Override
        public PojoWithValidatedCollection build() {
            return new PojoWithValidatedCollection(this);
        }

        public Builder map(Map<String, ValidatedPojo> map) {
            this.map = map;
            return this;
        }

        public Builder list(List<ValidatedPojo> list) {
            this.list = list;
            return this;
        }

        @Override
        public Builder deserialize(ShapeDeserializer decoder) {
            throw new UnsupportedOperationException("Not implemented");
        }
    }
}
