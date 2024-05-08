/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;

/**
 * Serializes a map shape.
 */
public interface MapSerializer {
    /**
     * Writes a string key for the map, and the valueSerializer is called and required to serialize the value.
     *
     * @param keySchema       Schema of the map key. The same schema should be provided for every map key entry.
     * @param key             Key to write.
     * @param state           State to pass to {@code valueSerializer}.
     * @param valueSerializer Serializer used to serialize the map value. A value must be serialized.
     */
    <T> void writeEntry(SdkSchema keySchema, String key, T state, BiConsumer<T, ShapeSerializer> valueSerializer);
}
