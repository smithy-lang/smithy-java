/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.serde.any;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.SdkSchema;
import software.amazon.smithy.java.runtime.core.serde.ShapeDeserializer;

/**
 * This essentially is responsible for sending the value of an Any into a builder.
 */
final class AnyDeserializer implements ShapeDeserializer {

    private final Any value;

    AnyDeserializer(Any value) {
        this.value = value;
    }

    @Override
    public void readNull(SdkSchema schema) {
        // do nothing.
    }

    @Override
    public boolean readBoolean(SdkSchema schema) {
        return value.asBoolean();
    }

    @Override
    public byte[] readBlob(SdkSchema schema) {
        return value.asBlob();
    }

    @Override
    public byte readByte(SdkSchema schema) {
        return value.asByte();
    }

    @Override
    public short readShort(SdkSchema schema) {
        return value.asShort();
    }

    @Override
    public int readInteger(SdkSchema schema) {
        return value.asInteger();
    }

    @Override
    public long readLong(SdkSchema schema) {
        return value.asLong();
    }

    @Override
    public float readFloat(SdkSchema schema) {
        return value.asFloat();
    }

    @Override
    public double readDouble(SdkSchema schema) {
        return value.asDouble();
    }

    @Override
    public BigInteger readBigInteger(SdkSchema schema) {
        return value.asBigInteger();
    }

    @Override
    public BigDecimal readBigDecimal(SdkSchema schema) {
        return value.asBigDecimal();
    }

    @Override
    public String readString(SdkSchema schema) {
        return value.asString();
    }

    @Override
    public Any readDocument(SdkSchema schema) {
        return value;
    }

    @Override
    public Instant readTimestamp(SdkSchema schema) {
        return value.asTimestamp();
    }

    @Override
    public void readStruct(SdkSchema schema, BiConsumer<SdkSchema, ShapeDeserializer> eachEntry) {
        for (var memberSchema : schema.members()) {
            var memberValue = value.getStructMember(memberSchema.memberName());
            if (memberValue != null) {
                eachEntry.accept(memberSchema, new AnyDeserializer(memberValue));
            }
        }
    }

    @Override
    public void readList(SdkSchema schema, Consumer<ShapeDeserializer> eachElement) {
        for (var element : value.asList()) {
            eachElement.accept(new AnyDeserializer(element));
        }
    }

    @Override
    public void readStringMap(SdkSchema schema, BiConsumer<String, ShapeDeserializer> eachEntry) {
        var map = value.asMap();
        for (var entry : map.entrySet()) {
            eachEntry.accept(entry.getKey().asString(), new AnyDeserializer(entry.getValue()));
        }
    }

    @Override
    public void readIntMap(SdkSchema schema, BiConsumer<Integer, ShapeDeserializer> eachEntry) {
        var map = value.asMap();
        for (var entry : map.entrySet()) {
            eachEntry.accept(entry.getKey().asInteger(), new AnyDeserializer(entry.getValue()));
        }
    }

    @Override
    public void readLongMap(SdkSchema schema, BiConsumer<Long, ShapeDeserializer> eachEntry) {
        var map = value.asMap();
        for (var entry : map.entrySet()) {
            eachEntry.accept(entry.getKey().asLong(), new AnyDeserializer(entry.getValue()));
        }
    }
}
