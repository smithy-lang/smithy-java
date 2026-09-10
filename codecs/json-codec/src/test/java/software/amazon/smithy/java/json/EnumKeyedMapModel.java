/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.schema.PreludeSchemas;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ShapeBuilder;
import software.amazon.smithy.java.core.schema.SmithyEnum;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeId;

public final class EnumKeyedMapModel {
    private static final Schema KEY = Schema.createEnum(
            ShapeId.from("smithy.java.json.test#EnumMapKey"),
            Set.of("A"),
            Key.class);
    private static final Schema MAP = Schema.mapBuilder(ShapeId.from("smithy.java.json.test#EnumKeyedMap"))
            .putMember("key", KEY)
            .putMember("value", PreludeSchemas.STRING)
            .build();
    private static final Schema VALUE = Schema.structureBuilder(ShapeId.from("smithy.java.json.test#EnumMapValue"))
            .shapeClass(Value.class)
            .builderSupplier(Value.Builder::new)
            .putMember("values", MAP)
            .build();

    private EnumKeyedMapModel() {}

    public record Key(String value) implements SmithyEnum {
        @Override
        public String getValue() {
            return value;
        }
    }

    public record Value(Map<Key, String> values) implements SerializableStruct {
        @Override
        public Schema schema() {
            return VALUE;
        }

        @Override
        public void serializeMembers(ShapeSerializer serializer) {
            throw new UnsupportedOperationException();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMemberValue(Schema member) {
            return (T) values;
        }

        public static final class Builder implements ShapeBuilder<Value> {
            private Map<Key, String> values;

            public Builder values(Map<Key, String> values) {
                this.values = values;
                return this;
            }

            @Override
            public Value build() {
                return new Value(values);
            }

            @Override
            public ShapeBuilder<Value> deserialize(ShapeDeserializer decoder) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Schema schema() {
                return VALUE;
            }
        }
    }
}
