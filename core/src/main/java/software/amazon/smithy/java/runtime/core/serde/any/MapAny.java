/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import java.util.Map;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.model.shapes.ShapeType;

final class MapAny implements Any {

    private final SdkSchema schema;
    private final Map<Any, Any> value;

    MapAny(Map<Any, Any> value, SdkSchema schema) {
        this.value = value;
        this.schema = schema;
    }

    @Override
    public SdkSchema getSchema() {
        return schema;
    }

    @Override
    public ShapeType getType() {
        return ShapeType.MAP;
    }

    @Override
    public Map<Any, Any> asMap() {
        return value;
    }

    @Override
    public void serialize(ShapeSerializer encoder) {
        encoder.beginMap(schema, s -> {
            for (var entry : value.entrySet()) {
                Any key = entry.getKey();
                Consumer<ShapeSerializer> valueSer = ser -> entry.getValue().serialize(ser);
                switch (key.getType()) {
                    case STRING -> s.entry(entry.getKey().asString(), valueSer);
                    case INTEGER -> s.entry(entry.getKey().asInteger(), valueSer);
                    case LONG -> s.entry(entry.getKey().asLong(), valueSer);
                    default -> throw new UnsupportedOperationException("Unexpected document map key: " + key.getType());
                }
            }
        });
    }
}
