/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.json;

import java.io.OutputStream;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.core.serde.ShapeSerializer;

public interface JsonSerdeProvider {

    int getPriority();

    String getName();

    ShapeDeserializer newDeserializer(byte[] source, JsonSettings settings);

    ShapeDeserializer newDeserializer(ByteBuffer source, JsonSettings settings);

    ShapeSerializer newSerializer(OutputStream sink, JsonSettings settings);

}
