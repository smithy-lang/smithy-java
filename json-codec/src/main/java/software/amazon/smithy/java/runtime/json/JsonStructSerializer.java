/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.spi.JsonException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

class JsonStructSerializer implements ShapeSerializer {

    private final JsonSerializer parent;
    private boolean firstValue;

    JsonStructSerializer(JsonSerializer parent, boolean isFirstValue) {
        this.parent = parent;
        this.firstValue = isFirstValue;
    }

    void startMember(SdkSchema member) {
        try {
            // Write commas when needed.
            if (!firstValue) {
                parent.stream.writeMore();
            } else {
                firstValue = false;
            }
            parent.stream.writeObjectField(parent.fieldMapper.memberToField(member));
        } catch (JsonException | IOException e) {
            throw new SdkSerdeException(e);
        }
    }

    @Override
    public void writeStruct(SdkSchema member, SerializableStruct struct) {
        startMember(member);
        parent.writeStruct(member, struct);
    }

    @Override
    public <T> void writeList(SdkSchema member, T listState, BiConsumer<T, ShapeSerializer> consumer) {
        startMember(member);
        parent.writeList(member, listState, consumer);
    }

    @Override
    public <T> void writeMap(SdkSchema member, T mapState, BiConsumer<T, MapSerializer> consumer) {
        startMember(member);
        parent.writeMap(member, mapState, consumer);
    }

    @Override
    public void writeBoolean(SdkSchema member, boolean value) {
        startMember(member);
        parent.writeBoolean(member, value);
    }

    @Override
    public void writeByte(SdkSchema member, byte value) {
        startMember(member);
        parent.writeByte(member, value);
    }

    @Override
    public void writeShort(SdkSchema member, short value) {
        startMember(member);
        parent.writeShort(member, value);
    }

    @Override
    public void writeInteger(SdkSchema member, int value) {
        startMember(member);
        parent.writeInteger(member, value);
    }

    @Override
    public void writeLong(SdkSchema member, long value) {
        startMember(member);
        parent.writeLong(member, value);
    }

    @Override
    public void writeFloat(SdkSchema member, float value) {
        startMember(member);
        parent.writeFloat(member, value);
    }

    @Override
    public void writeDouble(SdkSchema member, double value) {
        startMember(member);
        parent.writeDouble(member, value);
    }

    @Override
    public void writeBigInteger(SdkSchema member, BigInteger value) {
        startMember(member);
        parent.writeBigInteger(member, value);
    }

    @Override
    public void writeBigDecimal(SdkSchema member, BigDecimal value) {
        startMember(member);
        parent.writeBigDecimal(member, value);
    }

    @Override
    public void writeString(SdkSchema member, String value) {
        startMember(member);
        parent.writeString(member, value);
    }

    @Override
    public void writeBlob(SdkSchema member, byte[] value) {
        startMember(member);
        parent.writeBlob(member, value);
    }

    @Override
    public void writeTimestamp(SdkSchema member, Instant value) {
        startMember(member);
        parent.writeTimestamp(member, value);
    }

    @Override
    public void writeDocument(SdkSchema member, Document value) {
        startMember(member);
        parent.writeDocument(member, value);
    }

    @Override
    public void writeNull(SdkSchema member) {
        startMember(member);
        parent.writeNull(member);
    }
}
