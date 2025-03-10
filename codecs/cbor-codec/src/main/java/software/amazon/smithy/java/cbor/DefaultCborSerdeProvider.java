/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.cbor;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

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
        return new CborDeserializer(source);
    }

    @Override
    public ShapeDeserializer newDeserializer(ByteBuffer source, Rpcv2CborCodec.Settings settings) {
        return new CborDeserializer(source);
    }

    @Override
    public ShapeSerializer newSerializer(OutputStream sink, Rpcv2CborCodec.Settings settings) {
        return new CborSerializer(new Sink.OutputStreamSink(sink));
    }

    @Override
    public ByteBuffer serialize(SerializableStruct struct, Rpcv2CborCodec.Settings settings) {
        var sink = new Sink.ResizingSink();
        var serializer = new CborSerializer(sink);
        struct.serialize(serializer);
        return sink.finish();
    }
}
