/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

/**
 * Validates structures that have required members and fewer than 64 total members.
 */
final class ValidatorOfRequiredStruct implements ShapeSerializer {

    private final Validator.ShapeValidator validator;
    private int setBitfields = 0;

    ValidatorOfRequiredStruct(Validator.ShapeValidator validator) {
        this.validator = validator;
    }

    static <T> void validate(
        Validator.ShapeValidator validator,
        SdkSchema schema,
        T structState,
        BiConsumer<T, ShapeSerializer> consumer
    ) {
        var structValidator = new ValidatorOfRequiredStruct(validator);
        consumer.accept(structState, structValidator);
        checkResult(schema, structValidator.setBitfields, validator);
    }

    private static void checkResult(SdkSchema schema, int setBitfields, Validator.ShapeValidator validator) {
        if (schema.requiredStructureMemberBitfield != setBitfields) {
            for (var member : missingMembers(schema, setBitfields)) {
                validator.addError(
                    new ValidationError.RequiredValidationFailure(validator.createPath(), member, schema)
                );
            }
        }
    }

    private static Set<String> missingMembers(SdkSchema schema, int setBitfields) {
        Set<String> result = new TreeSet<>();
        for (var member : schema.members()) {
            if (member.isRequiredByValidation() && (setBitfields & member.requiredByValidationBitmask) == 0) {
                result.add(member.memberName());
            }
        }
        return result;
    }

    @Override
    public void writeBoolean(SdkSchema member, boolean value) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeBoolean(member, value);
        validator.popPath();
    }

    @Override
    public void writeByte(SdkSchema member, byte value) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeByte(member, value);
        validator.popPath();
    }

    @Override
    public void writeShort(SdkSchema member, short value) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeShort(member, value);
        validator.popPath();
    }

    @Override
    public void writeInteger(SdkSchema member, int value) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeInteger(member, value);
        validator.popPath();
    }

    @Override
    public void writeLong(SdkSchema member, long value) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeLong(member, value);
        validator.popPath();
    }

    @Override
    public void writeFloat(SdkSchema member, float value) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeFloat(member, value);
        validator.popPath();
    }

    @Override
    public void writeDouble(SdkSchema member, double value) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeDouble(member, value);
        validator.popPath();
    }

    @Override
    public void writeBigInteger(SdkSchema member, BigInteger value) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeBigInteger(member, value);
        validator.popPath();
    }

    @Override
    public void writeBigDecimal(SdkSchema member, BigDecimal value) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeBigDecimal(member, value);
        validator.popPath();
    }

    @Override
    public void writeBlob(SdkSchema member, byte[] value) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeBlob(member, value);
        validator.popPath();
    }

    @Override
    public void writeString(SdkSchema member, String value) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeString(member, value);
        validator.popPath();
    }

    @Override
    public void writeTimestamp(SdkSchema member, Instant value) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeTimestamp(member, value);
        validator.popPath();
    }

    @Override
    public void writeDocument(SdkSchema member, Document value) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeDocument(value);
        validator.popPath();
    }

    @Override
    public <T> void writeList(SdkSchema member, T state, BiConsumer<T, ShapeSerializer> consumer) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeList(member, state, consumer);
        validator.popPath();
    }

    @Override
    public <T> void writeMap(SdkSchema member, T state, BiConsumer<T, MapSerializer> consumer) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeMap(member, state, consumer);
        validator.popPath();
    }

    @Override
    public <T> void writeStruct(SdkSchema member, T structState, BiConsumer<T, ShapeSerializer> consumer) {
        setBitfields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
        validator.writeStruct(member, structState, consumer);
        validator.popPath();
    }

    @Override
    public void writeNull(SdkSchema member) {
        // A null member does not count as present so don't set the bitfield.
        validator.pushPath(member.memberName());
        validator.writeNull(member);
        validator.popPath();
    }
}
