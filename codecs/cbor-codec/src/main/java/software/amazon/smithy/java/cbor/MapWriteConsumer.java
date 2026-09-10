/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.util.function.BiConsumer;

final class MapWriteConsumer implements BiConsumer<Object, Object> {

    private final GeneratedCborCodec codec;
    private final CborSerializer writer;
    private final int mapId;

    MapWriteConsumer(GeneratedCborCodec codec, CborSerializer writer, int mapId) {
        this.codec = codec;
        this.writer = writer;
        this.mapId = mapId;
    }

    @Override
    public void accept(Object key, Object value) {
        writer.generatedWriteMapKey((String) key);
        if (value == null) {
            writer.writeNull(null);
        } else {
            codec.writeMapValue(mapId, value, writer);
        }
    }
}
