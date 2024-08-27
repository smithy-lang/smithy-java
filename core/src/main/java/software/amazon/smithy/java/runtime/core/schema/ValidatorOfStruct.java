/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

/**
 * Validates structures that have required members and fewer than 64 total members.
 */
final class ValidatorOfStruct implements ShapeSerializer {

    private final Validator.ShapeValidator validator;
    private final PresenceTracker structValidator;

    ValidatorOfStruct(Validator.ShapeValidator validator, PresenceTracker structValidator) {
        this.validator = validator;
        this.structValidator = structValidator;
    }

    static void validate(Validator.ShapeValidator validator, Schema schema, SerializableStruct struct) {
        var tracker = PresenceTracker.of(schema);
        struct.serializeMembers(new ValidatorOfStruct(validator, tracker));
        if (!tracker.allSet()) {
            for (var member : tracker.getMissingMembers()) {
                validator.addError(
                    new ValidationError.RequiredValidationFailure(validator.createPath(), member, schema)
                );
            }
        }
    }

    @Override
    public void writeBoolean(Schema member, boolean value) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeBoolean(member, value);
        validator.popPath();
    }

    @Override
    public void writeByte(Schema member, byte value) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeByte(member, value);
        validator.popPath();
    }

    @Override
    public void writeShort(Schema member, short value) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeShort(member, value);
        validator.popPath();
    }

    @Override
    public void writeInteger(Schema member, int value) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeInteger(member, value);
        validator.popPath();
    }

    @Override
    public void writeLong(Schema member, long value) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeLong(member, value);
        validator.popPath();
    }

    @Override
    public void writeFloat(Schema member, float value) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeFloat(member, value);
        validator.popPath();
    }

    @Override
    public void writeDouble(Schema member, double value) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeDouble(member, value);
        validator.popPath();
    }

    @Override
    public void writeBigInteger(Schema member, BigInteger value) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeBigInteger(member, value);
        validator.popPath();
    }

    @Override
    public void writeBigDecimal(Schema member, BigDecimal value) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeBigDecimal(member, value);
        validator.popPath();
    }

    @Override
    public void writeBlob(Schema member, ByteBuffer value) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeBlob(member, value);
        validator.popPath();
    }

    @Override
    public void writeString(Schema member, String value) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeString(member, value);
        validator.popPath();
    }

    @Override
    public void writeTimestamp(Schema member, Instant value) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeTimestamp(member, value);
        validator.popPath();
    }

    @Override
    public void writeDocument(Schema member, Document value) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeDocument(member, value);
        validator.popPath();
    }

    @Override
    public <T> void writeList(Schema member, T state, int size, BiConsumer<T, ShapeSerializer> consumer) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeList(member, state, size, consumer);
        validator.popPath();
    }

    @Override
    public <T> void writeMap(Schema member, T state, int size, BiConsumer<T, MapSerializer> consumer) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeMap(member, state, size, consumer);
        validator.popPath();
    }

    @Override
    public void writeStruct(Schema member, SerializableStruct struct) {
        structValidator.setMember(member);
        validator.pushPath(member.memberName());
        validator.writeStruct(member, struct);
        validator.popPath();
    }

    @Override
    public void writeNull(Schema member) {
        // A null member does not count as present so don't set the bitfield.
        validator.pushPath(member.memberName());
        validator.writeNull(member);
        validator.popPath();
    }
}
