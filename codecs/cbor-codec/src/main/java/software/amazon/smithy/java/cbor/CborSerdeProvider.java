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

public interface CborSerdeProvider {
    int getPriority();

    String getName();

    ShapeDeserializer newDeserializer(byte[] source, CborSettings settings);

    ShapeDeserializer newDeserializer(ByteBuffer source, CborSettings settings);

    ShapeSerializer newSerializer(OutputStream sink, CborSettings settings);

    ByteBuffer serialize(SerializableStruct struct, CborSettings settings);
}
