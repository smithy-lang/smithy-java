/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.cbor;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.runtime.core.serde.Codec;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

public final class JacksonCborCodec implements Codec {
    private static final JacksonProvider JACKSON = new JacksonProvider();
    private static final Rpcv2CborCodec.Settings SETTINGS = new Rpcv2CborCodec.Settings();

    @Override
    public ShapeSerializer createSerializer(OutputStream sink) {
        return JACKSON.newSerializer(sink, SETTINGS);
    }

    @Override
    public ShapeDeserializer createDeserializer(ByteBuffer source) {
        return JACKSON.newDeserializer(source, SETTINGS);
    }

    @Override
    public ShapeDeserializer createDeserializer(byte[] source) {
        return JACKSON.newDeserializer(source, SETTINGS);
    }
}
