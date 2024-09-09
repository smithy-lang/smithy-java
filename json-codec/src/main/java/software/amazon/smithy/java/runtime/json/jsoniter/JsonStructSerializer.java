/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json.jsoniter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.java.runtime.json.JsonFieldMapper;
import software.amazon.smithy.java.runtime.json.jsoniter.internal.JsonException;

final class JsonStructSerializer implements ShapeSerializer {

    private final JsonIterSerializer parent;
    private boolean firstValue;

    JsonStructSerializer(JsonIterSerializer parent, boolean isFirstValue) {
        this.parent = parent;
        this.firstValue = isFirstValue;
    }

    void startMember(Schema member) {
        try {
            // Write commas when needed.
            if (firstValue) {
                firstValue = false;
            } else {
                parent.stream.writeMore();
            }
            if (parent.settings.fieldMapper() == JsonFieldMapper.UseMemberName.INSTANCE) {
                parent.stream.writeRawObjectField(member.memberName());
            } else {
                parent.stream.writeObjectField(parent.settings.fieldMapper().memberToField(member));
            }
        } catch (JsonException | IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void writeStruct(Schema member, SerializableStruct struct) {
        startMember(member);
        parent.writeStruct(member, struct);
    }

    @Override
    public <T> void writeList(Schema member, T listState, int size, BiConsumer<T, ShapeSerializer> consumer) {
        startMember(member);
        parent.writeList(member, listState, size, consumer);
    }

    @Override
    public <T> void writeMap(Schema member, T mapState, int size, BiConsumer<T, MapSerializer> consumer) {
        startMember(member);
        parent.writeMap(member, mapState, size, consumer);
    }

    @Override
    public void writeBoolean(Schema member, boolean value) {
        startMember(member);
        parent.writeBoolean(member, value);
    }

    @Override
    public void writeByte(Schema member, byte value) {
        startMember(member);
        parent.writeByte(member, value);
    }

    @Override
    public void writeShort(Schema member, short value) {
        startMember(member);
        parent.writeShort(member, value);
    }

    @Override
    public void writeInteger(Schema member, int value) {
        startMember(member);
        parent.writeInteger(member, value);
    }

    @Override
    public void writeLong(Schema member, long value) {
        startMember(member);
        parent.writeLong(member, value);
    }

    @Override
    public void writeFloat(Schema member, float value) {
        startMember(member);
        parent.writeFloat(member, value);
    }

    @Override
    public void writeDouble(Schema member, double value) {
        startMember(member);
        parent.writeDouble(member, value);
    }

    @Override
    public void writeBigInteger(Schema member, BigInteger value) {
        startMember(member);
        parent.writeBigInteger(member, value);
    }

    @Override
    public void writeBigDecimal(Schema member, BigDecimal value) {
        startMember(member);
        parent.writeBigDecimal(member, value);
    }

    @Override
    public void writeString(Schema member, String value) {
        startMember(member);
        parent.writeString(member, value);
    }

    @Override
    public void writeBlob(Schema member, ByteBuffer value) {
        startMember(member);
        parent.writeBlob(member, value);
    }

    @Override
    public void writeTimestamp(Schema member, Instant value) {
        startMember(member);
        parent.writeTimestamp(member, value);
    }

    @Override
    public void writeDocument(Schema member, Document value) {
        startMember(member);
        parent.writeDocument(member, value);
    }

    @Override
    public void writeNull(Schema member) {
        startMember(member);
        parent.writeNull(member);
    }
}
