/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import java.io.OutputStream;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;

public interface JsonSerdeProvider {

    int getPriority();

    String getName();

    ShapeDeserializer newDeserializer(byte[] source, JsonCodec.Settings settings);

    ShapeSerializer newSerializer(OutputStream sink, JsonCodec.Settings settings);

}
