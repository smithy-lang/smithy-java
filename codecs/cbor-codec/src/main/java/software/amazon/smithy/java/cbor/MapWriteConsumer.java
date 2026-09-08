/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.util.function.BiConsumer;
import software.amazon.smithy.java.core.serde.SerializationException;

final class MapWriteConsumer implements BiConsumer<Object, Object> {

    private final GeneratedCborCodec codec;
    private final CborSerializer writer;
    private final int mapId;
    private final boolean sparse;

    MapWriteConsumer(GeneratedCborCodec codec, CborSerializer writer, int mapId, boolean sparse) {
        this.codec = codec;
        this.writer = writer;
        this.mapId = mapId;
        this.sparse = sparse;
    }

    @Override
    public void accept(Object key, Object value) {
        writer.generatedWriteMapKey((String) key);
        if (value == null) {
            if (sparse) {
                writer.writeNull(null);
            } else {
                throw new SerializationException("Null value found in dense map");
            }
        } else {
            codec.writeMapValue(mapId, value, writer);
        }
    }
}
