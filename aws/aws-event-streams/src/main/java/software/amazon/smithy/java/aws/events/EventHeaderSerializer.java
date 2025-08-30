/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.events;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import software.amazon.eventstream.HeaderValue;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.SpecificShapeSerializer;

public class EventHeaderSerializer extends SpecificShapeSerializer {
    private final Map<String, HeaderValue> headers;

    public EventHeaderSerializer(Map<String, HeaderValue> headers) {
        this.headers = headers;
    }

    @Override
    public void writeBoolean(Schema schema, boolean value) {
        headers.put(schema.memberName(), HeaderValue.fromBoolean(value));
    }

    @Override
    public void writeShort(Schema schema, short value) {
        headers.put(schema.memberName(), HeaderValue.fromShort(value));
    }

    @Override
    public void writeByte(Schema schema, byte value) {
        headers.put(schema.memberName(), HeaderValue.fromByte(value));
    }

    @Override
    public void writeInteger(Schema schema, int value) {
        headers.put(schema.memberName(), HeaderValue.fromInteger(value));
    }

    @Override
    public void writeLong(Schema schema, long value) {
        headers.put(schema.memberName(), HeaderValue.fromLong(value));
    }

    @Override
    public void writeString(Schema schema, String value) {
        headers.put(schema.memberName(), HeaderValue.fromString(value));
    }

    @Override
    public void writeBlob(Schema schema, ByteBuffer value) {
        headers.put(schema.memberName(), HeaderValue.fromByteBuffer(value));
    }

    @Override
    public void writeTimestamp(Schema schema, Instant value) {
        headers.put(schema.memberName(), HeaderValue.fromTimestamp(value));
    }

    @Override
    public void writeStruct(Schema schema, SerializableStruct struct) {
        struct.serializeMembers(this);
    }
}
