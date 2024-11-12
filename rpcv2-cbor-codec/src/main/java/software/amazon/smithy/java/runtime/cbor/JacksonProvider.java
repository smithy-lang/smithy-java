/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.cbor;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.io.ByteBufferUtils;

final class JacksonProvider implements CborSerdeProvider {
    private static final CBORFactory FACTORY = CBORFactory.builder().build();
    private static final DefaultCborSerdeProvider DEFAULT_SERDE_PROVIDER = new DefaultCborSerdeProvider();

    @Override
    public int getPriority() {
        return 3;
    }

    @Override
    public String getName() {
        return "jackson-cbor";
    }

    @Override
    public ShapeDeserializer newDeserializer(byte[] source, Rpcv2CborCodec.Settings settings) {
        try {
            return new JacksonCborDeserializer(FACTORY.createParser(source), settings);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public ShapeDeserializer newDeserializer(ByteBuffer source, Rpcv2CborCodec.Settings settings) {
        try {
            return new JacksonCborDeserializer(FACTORY.createParser(ByteBufferUtils.getBytes(source)), settings);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public ShapeSerializer newSerializer(OutputStream sink, Rpcv2CborCodec.Settings settings) {
        return DEFAULT_SERDE_PROVIDER.newSerializer(sink, settings);
    }

    @Override
    public ByteBuffer serialize(SerializableStruct struct, Rpcv2CborCodec.Settings settings) {
        return DEFAULT_SERDE_PROVIDER.serialize(struct, settings);
    }
}
