/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.http.binding;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.SpecificShapeSerializer;

/**
 * Serializes prefixed HTTP headers.
 * <p>
 * This serializer expects a map of string to string. Each written header is sent to the given headerConsumer,
 * with a header name concatenated with the prefix.
 */
final class HttpPrefixHeadersSerializer extends SpecificShapeSerializer {

    private final String prefix;
    private final BiConsumer<String, String> headerConsumer;

    HttpPrefixHeadersSerializer(String prefix, BiConsumer<String, String> headerConsumer) {
        this.prefix = prefix;
        this.headerConsumer = headerConsumer;
    }

    @Override
    public void writeMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
        consumer.accept(new MapSerializer() {
            @Override
            public void writeEntry(SdkSchema keySchema, String key, Consumer<ShapeSerializer> valueSerializer) {
                valueSerializer.accept(new SpecificShapeSerializer() {
                    @Override
                    public void writeString(SdkSchema schema, String value) {
                        headerConsumer.accept(prefix + key, value);
                    }
                });
            }

            @Override
            public void writeEntry(SdkSchema keySchema, int key, Consumer<ShapeSerializer> valueSerializer) {
                throw new UnsupportedOperationException("Prefix headers expects maps with string keys: " + schema);
            }

            @Override
            public void writeEntry(SdkSchema keySchema, long key, Consumer<ShapeSerializer> valueSerializer) {
                throw new UnsupportedOperationException("Prefix headers expects maps with string keys: " + schema);
            }
        });
    }
}
