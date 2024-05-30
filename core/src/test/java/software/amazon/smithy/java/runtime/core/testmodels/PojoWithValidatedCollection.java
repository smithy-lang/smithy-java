/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.testmodels;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.PreludeSchemas;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SdkShapeBuilder;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.ToStringSerializer;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.RequiredTrait;

public final class PojoWithValidatedCollection implements SerializableStruct {

    public static final ShapeId ID = ShapeId.from("smithy.example#PojoWithValidatedCollection");

    private static final ShapeId MAP_OF_VALIDATED_POJO_ID = ShapeId.from("smithy.example#MapOfValidatedPojo");
    private static final SdkSchema MAP_OF_VALIDATED_POJO_KEY = SdkSchema.memberBuilder("key", PreludeSchemas.STRING)
        .id(MAP_OF_VALIDATED_POJO_ID)
        .build();
    private static final SdkSchema MAP_OF_VALIDATED_POJO_VALUE = SdkSchema.memberBuilder("value", ValidatedPojo.SCHEMA)
        .id(MAP_OF_VALIDATED_POJO_ID)
        .build();
    private static final SdkSchema MAP_OF_VALIDATED_POJO = SdkSchema.builder()
        .type(ShapeType.MAP)
        .id(MAP_OF_VALIDATED_POJO_ID)
        .members(MAP_OF_VALIDATED_POJO_KEY, MAP_OF_VALIDATED_POJO_VALUE)
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
    public void serialize(ShapeSerializer encoder) {
        encoder.writeStruct(SCHEMA, this);
    }

    @Override
    public void serializeMembers(ShapeSerializer st) {
        st.writeList(SCHEMA_LIST, list, InnerListSerializer.INSTANCE);
        st.writeMap(SCHEMA_MAP, map, InnerMapSerializer.INSTANCE);
    }

    private static final class InnerListSerializer implements BiConsumer<List<ValidatedPojo>, ShapeSerializer> {
        private static final InnerListSerializer INSTANCE = new InnerListSerializer();

        @Override
        public void accept(List<ValidatedPojo> value, ShapeSerializer ser) {
            for (var entry : value) {
                entry.serialize(ser);
            }
        }
    }

    private static final class InnerMapSerializer implements BiConsumer<Map<String, ValidatedPojo>, MapSerializer> {
        private static final InnerMapSerializer INSTANCE = new InnerMapSerializer();

        @Override
        public void accept(Map<String, ValidatedPojo> map, MapSerializer ser) {
            for (var entry : map.entrySet()) {
                ser.writeEntry(MAP_OF_VALIDATED_POJO_KEY, entry.getKey(), entry.getValue(), ValidatedPojo::serialize);
            }
        }
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
