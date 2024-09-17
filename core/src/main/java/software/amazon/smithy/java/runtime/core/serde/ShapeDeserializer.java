/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

/**
 * Deserializes a shape by emitted the Smithy data model from the shape, aided by schemas.
 */
public interface ShapeDeserializer extends AutoCloseable {

    @Override
    default void close() {}

    /**
     * Attempt to read a boolean value.
     *
     * @param schema Schema of the shape.
     * @return the read value.
     */
    boolean readBoolean(Schema schema);

    /**
     * Attempt to read a blob value.
     *
     * @param schema Schema of the shape.
     * @return the read value.
     */
    ByteBuffer readBlob(Schema schema);

    /**
     * Attempt to read a byte value.
     *
     * @param schema Schema of the shape.
     * @return the read value.
     */
    byte readByte(Schema schema);

    /**
     * Attempt to read a short value.
     *
     * @param schema Schema of the shape.
     * @return the read value.
     */
    short readShort(Schema schema);

    /**
     * Attempt to read an integer or intEnum value.
     *
     * @param schema Schema of the shape.
     * @return the read value.
     */
    int readInteger(Schema schema);

    /**
     * Attempt to read a long value.
     *
     * @param schema Schema of the shape.
     * @return the read value.
     */
    long readLong(Schema schema);

    /**
     * Attempt to read a float value.
     *
     * @param schema Schema of the shape.
     * @return the read value.
     */
    float readFloat(Schema schema);

    /**
     * Attempt to read a double value.
     *
     * @param schema Schema of the shape.
     * @return the read value.
     */
    double readDouble(Schema schema);

    /**
     * Attempt to read a bigInteger value.
     *
     * @param schema Schema of the shape.
     * @return the read value.
     */
    BigInteger readBigInteger(Schema schema);

    /**
     * Attempt to read a bigDecimal value.
     *
     * @param schema Schema of the shape.
     * @return the read value.
     */
    BigDecimal readBigDecimal(Schema schema);

    /**
     * Attempt to read a string or enum value.
     *
     * @param schema Schema of the shape.
     * @return the read value.
     */
    String readString(Schema schema);

    /**
     * Attempt to read a document value.
     *
     * @return the read value.
     */
    Document readDocument();

    /**
     * Attempt to read a timestamp value.
     *
     * @return the read value.
     */
    Instant readTimestamp(Schema schema);

    /**
     * Attempt to read a structure or union value.
     *
     * @param schema   Schema of the shape.
     * @param state    State to pass to the consumer.
     * @param consumer Consumer that receives the state, member schema, and deserializer.
     */
    <T> void readStruct(Schema schema, T state, StructMemberConsumer<T> consumer);

    /**
     * Attempt to read a list value.
     *
     * @param schema   Schema of the shape.
     * @param state    State to pass to the consumer.
     * @param consumer Consumer that receives the state and deserializer.
     */
    <T> void readList(Schema schema, T state, ListMemberConsumer<T> consumer);

    /**
     * If the value about to be read is a list or map, returns the number of entries it contains.
     *
     * <p>This method returns -1 if the size of the container is unknown or if the next value is not a list or
     * container.
     *
     * @return the number of entries in the next list or map to read, or -1 when unknown.
     */
    default int containerSize() {
        return -1;
    }

    /**
     * Attempt to read a map value.
     *
     * @param schema   Schema of the shape.
     * @param state    State to pass to the consumer.
     * @param consumer Consumer that receives the state, map key, and deserializer.
     */
    <T> void readStringMap(Schema schema, T state, MapMemberConsumer<String, T> consumer);

    /**
     *
     * Attempt to see if this value is null. Useful for sparse collections.
     *
     * @return true if null
     */
    boolean isNull();

    /**
     * Read (skip) the null value. Only makes sense after {@link #isNull()}.
     *
     * @return null
     */
    default <T> T readNull() {
        return null;
    }

    /**
     * Read a data stream from the deserializer.
     *
     * @param schema Schema of the data stream to read.
     * @return the data stream.
     */
    default DataStream readDataStream(Schema schema) {
        throw new UnsupportedOperationException("Cannot read data stream from this deserializer");
    }

    /**
     * Read an event stream from the deserializer.
     *
     * @param schema Schema of the event stream to read.
     * @return the event stream.
     */
    default Flow.Publisher<? extends SerializableStruct> readEventStream(Schema schema) {
        throw new UnsupportedOperationException("Cannot read event stream from this deserializer");
    }

    /**
     * Consumer of a structure member.
     *
     * @param <T> Passed in state value to avoid capturing external state.
     */
    @FunctionalInterface
    interface StructMemberConsumer<T> {
        void accept(T state, Schema memberSchema, ShapeDeserializer memberDeserializer);

        default void unknownMember(T state, String memberName) {}
    }

    /**
     * Consumer of list member.
     *
     * @param <T> Passed in state value to avoid capturing external state.
     */
    @FunctionalInterface
    interface ListMemberConsumer<T> {
        void accept(T state, ShapeDeserializer memberDeserializer);
    }

    /**
     * Consumer of map member.
     *
     * @param <T> Passed in state value to avoid capturing external state.
     */
    @FunctionalInterface
    interface MapMemberConsumer<K, T> {
        void accept(T state, K key, ShapeDeserializer memberDeserializer);
    }
}
