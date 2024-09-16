/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.cbor;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

final class DefaultCborSerdeProvider implements CborSerdeProvider {
    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public String getName() {
        return "cbor";
    }

    @Override
    public ShapeDeserializer newDeserializer(byte[] source, Rpcv2CborCodec.Settings settings) {
        return new CborDeserializer(source, settings);
    }

    @Override
    public ShapeDeserializer newDeserializer(ByteBuffer source, Rpcv2CborCodec.Settings settings) {
        return new CborDeserializer(source, settings);
    }

    @Override
    public ShapeSerializer newSerializer(OutputStream sink, Rpcv2CborCodec.Settings settings) {
        return new CborSerializer(sink);
    }
}
