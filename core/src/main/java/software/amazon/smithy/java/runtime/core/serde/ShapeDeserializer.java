/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.any.Any;

/**
 * Deserializes a shape by emitted the Smithy data model from the shape, aided by schemas.
 */
public interface ShapeDeserializer {

    void readNull(SdkSchema schema);

    boolean readBoolean(SdkSchema schema);

    byte[] readBlob(SdkSchema schema);

    byte readByte(SdkSchema schema);

    short readShort(SdkSchema schema);

    int readInteger(SdkSchema schema);

    long readLong(SdkSchema schema);

    float readFloat(SdkSchema schema);

    double readDouble(SdkSchema schema);

    BigInteger readBigInteger(SdkSchema schema);

    BigDecimal readBigDecimal(SdkSchema schema);

    String readString(SdkSchema schema);

    Any readDocument(SdkSchema schema);

    Instant readTimestamp(SdkSchema schema);

    void readStruct(SdkSchema schema, BiConsumer<SdkSchema, ShapeDeserializer> eachEntry);

    void readList(SdkSchema schema, Consumer<ShapeDeserializer> eachElement);

    void readStringMap(SdkSchema schema, BiConsumer<String, ShapeDeserializer> eachEntry);

    void readIntMap(SdkSchema schema, BiConsumer<Integer, ShapeDeserializer> eachEntry);

    void readLongMap(SdkSchema schema, BiConsumer<Long, ShapeDeserializer> eachEntry);
}
