/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;

/**
 * Serializes httpQueryParam bindings for a map of string to string, and a map of string to string[].
 */
final class HttpQueryParamsSerializer extends SpecificShapeSerializer {

    private final MapEntrySerializer mapEntrySerializer;

    public HttpQueryParamsSerializer(BiConsumer<String, String> queryWriter) {
        mapEntrySerializer = new MapEntrySerializer(queryWriter);
    }

    @Override
    public <T> void writeMap(SdkSchema schema, T mapState, BiConsumer<T, MapSerializer> consumer) {
        consumer.accept(mapState, mapEntrySerializer);
    }

    private record MapEntrySerializer(BiConsumer<String, String> queryWriter) implements MapSerializer {
        @Override
        public <K> void writeEntry(
            SdkSchema keySchema,
            String key,
            K keyState,
            BiConsumer<K, ShapeSerializer> valueSerializer
        ) {
            valueSerializer.accept(keyState, new SpecificShapeSerializer() {
                @Override
                public void writeString(SdkSchema schema, String value) {
                    queryWriter.accept(key, value);
                }

                @Override
                public <L> void writeList(SdkSchema schema, L listState, BiConsumer<L, ShapeSerializer> consumer) {
                    consumer.accept(listState, new SpecificShapeSerializer() {
                        @Override
                        public void writeString(SdkSchema schema, String value) {
                            queryWriter.accept(key, value);
                        }
                    });
                }
            });
        }
    }
}
