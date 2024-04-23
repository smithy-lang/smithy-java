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
 * Serializes httpQueryParam bindings for a map of string to string, and a map of string to string[].
 */
final class HttpQueryParamsSerializer extends SpecificShapeSerializer {

    private final BiConsumer<String, String> queryWriter;

    public HttpQueryParamsSerializer(BiConsumer<String, String> queryWriter) {
        this.queryWriter = queryWriter;
    }

    @Override
    public void writeMap(SdkSchema schema, Consumer<MapSerializer> consumer) {
        consumer.accept(new MapSerializer() {
            @Override
            public void writeEntry(SdkSchema keySchema, String key, Consumer<ShapeSerializer> valueSerializer) {
                valueSerializer.accept(new SpecificShapeSerializer() {
                    @Override
                    public void writeString(SdkSchema schema, String value) {
                        queryWriter.accept(key, value);
                    }

                    @Override
                    public void writeList(SdkSchema schema, Consumer<ShapeSerializer> consumer) {
                        consumer.accept(new SpecificShapeSerializer() {
                            @Override
                            public void writeString(SdkSchema schema, String value) {
                                queryWriter.accept(key, value);
                            }
                        });
                    }
                });
            }
        });
    }
}
