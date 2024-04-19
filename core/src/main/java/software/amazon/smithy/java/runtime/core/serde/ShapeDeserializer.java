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
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

/**
 * Deserializes a shape by emitted the Smithy data model from the shape, aided by schemas.
 */
public interface ShapeDeserializer {

    boolean readBoolean(Schema schema);

    byte[] readBlob(Schema schema);

    byte readByte(Schema schema);

    short readShort(Schema schema);

    int readInteger(Schema schema);

    long readLong(Schema schema);

    float readFloat(Schema schema);

    double readDouble(Schema schema);

    BigInteger readBigInteger(Schema schema);

    BigDecimal readBigDecimal(Schema schema);

    String readString(Schema schema);

    Document readDocument();

    Instant readTimestamp(Schema schema);

    void readStruct(Schema schema, BiConsumer<Schema, ShapeDeserializer> eachEntry);

    void readList(Schema schema, Consumer<ShapeDeserializer> eachElement);

    void readStringMap(Schema schema, BiConsumer<String, ShapeDeserializer> eachEntry);

    void readIntMap(Schema schema, BiConsumer<Integer, ShapeDeserializer> eachEntry);

    void readLongMap(Schema schema, BiConsumer<Long, ShapeDeserializer> eachEntry);
}
