/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.binding;

import java.util.Set;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.serde.MapSerializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;

/**
 * Serializes prefixed HTTP headers.
 * <p>
 * This serializer expects a map of string to string. Each written header is sent to the given headerConsumer,
 * with a header name concatenated with the prefix.
 */
final class HttpPrefixHeadersSerializer extends SpecificShapeSerializer {

    private final PrefixHeadersMapSerializer prefixHeadersMapSerializer;

    HttpPrefixHeadersSerializer(
            String prefix,
            BiConsumer<String, String> headerConsumer,
            Set<String> namesFromHttpHeader
    ) {
        prefixHeadersMapSerializer = new PrefixHeadersMapSerializer(prefix, headerConsumer, namesFromHttpHeader);
    }

    @Override
    public <T> void writeMap(Schema schema, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        consumer.accept(mapState, prefixHeadersMapSerializer);
    }

    private record PrefixHeadersMapSerializer(
            String prefix,
            BiConsumer<String, String> headerConsumer,
            Set<String> namesFromHttpHeader) implements MapSerializer {
        @Override
        public <K> void writeEntry(
                Schema keySchema,
                String key,
                K keyState,
                BiConsumer<K, ShapeSerializer> valueSerializer
        ) {
            valueSerializer.accept(keyState, new SpecificShapeSerializer() {
                @Override
                public void writeString(Schema schema, String value) {
                    var headerName = prefix + key;
                    // HttpHeader takes precedence if prefixHeader's value is empty.
                    // Smithy's validator will complain if prefixHeader's value is not empty and duplicate found
                    if (!namesFromHttpHeader.contains(headerName)) {
                        headerConsumer.accept(headerName, value);
                    }
                }
            });
        }
    }
}
