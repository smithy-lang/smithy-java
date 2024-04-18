/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.util.function.Consumer;
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
     * @param valueSerializer Serializer used to serialize the map value. A value must be serialized.
     */
    void writeEntry(SdkSchema keySchema, String key, Consumer<ShapeSerializer> valueSerializer);

    /**
     * Writes an integer key for the map, and the valueSerializer is called and required to serialize the value.
     *
     * @param keySchema       Schema of the map key. The same schema should be provided for every map key entry.
     * @param key             Key to write.
     * @param valueSerializer Serializer used to serialize the map value. A value must be serialized.
     */
    void writeEntry(SdkSchema keySchema, int key, Consumer<ShapeSerializer> valueSerializer);

    /**
     * Writes a long key for the map, and the valueSerializer is called and required to serialize the value.
     *
     * @param keySchema       Schema of the map key. The same schema should be provided for every map key entry.
     * @param key             Key to write.
     * @param valueSerializer Serializer used to serialize the map value. A value must be serialized.
     */
    void writeEntry(SdkSchema keySchema, long key, Consumer<ShapeSerializer> valueSerializer);
}
