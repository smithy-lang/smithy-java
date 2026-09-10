/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json.smithy;

import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.serde.SerializationException;

final class MapWriteConsumer implements BiConsumer<Object, Object> {

    private final GeneratedJsonCodec codec;
    private final JsonCodegenWriter writer;
    private final int mapId;
    private final boolean sparse;

    MapWriteConsumer(GeneratedJsonCodec codec, JsonCodegenWriter writer, int mapId, boolean sparse) {
        this.codec = codec;
        this.writer = writer;
        this.mapId = mapId;
        this.sparse = sparse;
    }

    @Override
    public void accept(Object key, Object value) {
        writer.dynamicField((String) key);
        if (value == null) {
            if (sparse) {
                writer.writeNull();
            } else {
                throw new SerializationException("Null value found in dense map");
            }
        } else {
            codec.writeMapValue(mapId, value, writer);
        }
    }
}
