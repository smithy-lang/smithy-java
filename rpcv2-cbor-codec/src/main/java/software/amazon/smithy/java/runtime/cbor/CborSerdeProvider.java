/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.cbor;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

public interface CborSerdeProvider {
    int getPriority();

    String getName();

    ShapeDeserializer newDeserializer(byte[] source, Rpcv2CborCodec.Settings settings);

    ShapeDeserializer newDeserializer(ByteBuffer source, Rpcv2CborCodec.Settings settings);

    ShapeSerializer newSerializer(OutputStream sink, Rpcv2CborCodec.Settings settings);

    ByteBuffer serialize(SerializableStruct struct, Rpcv2CborCodec.Settings settings);
}
