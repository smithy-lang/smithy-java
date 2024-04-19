/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.json;

import com.jsoniter.output.JsonStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.Consumer;
import software.amazon.smithy.java.runtime.core.schema.Schema;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.traits.JsonNameTrait;

class JsonStructSerializer implements ShapeSerializer {

    private final boolean useJsonName;
    private final ShapeSerializer parent;
    private final JsonStream stream;
    private boolean wroteValues = false;

    JsonStructSerializer(ShapeSerializer parent, JsonStream stream, boolean useJsonName) {
        this.parent = parent;
        this.stream = stream;
        this.useJsonName = useJsonName;
    }

    private String getMemberName(Schema member) {
        if (useJsonName && member.hasTrait(JsonNameTrait.class)) {
            return member.getTrait(JsonNameTrait.class).getValue();
        } else {
            return member.memberName();
        }
    }

    void startMember(Schema member) {
        try {
            // Write commas when needed.
            if (wroteValues) {
                stream.writeMore();
            } else {
                wroteValues = true;
            }
            stream.writeObjectField(getMemberName(member));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void writeStruct(Schema member, Consumer<ShapeSerializer> memberWriter) {
        startMember(member);
        memberWriter.accept(this);
    }

    @Override
    public void writeList(Schema member, Consumer<ShapeSerializer> consumer) {
        startMember(member);
        consumer.accept(parent);
    }

    @Override
    public void writeMap(Schema member, Consumer<MapSerializer> consumer) {
        startMember(member);
        parent.writeMap(member, consumer);
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
    public void writeBlob(Schema member, byte[] value) {
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
