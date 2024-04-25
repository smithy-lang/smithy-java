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
 * Evaluates structures and find missing required members.
 */
final class ValidatorOfStruct implements ShapeSerializer {

    private final Validator.ShapeValidator validator;
    private final SdkSchema schema;
    private int setFields = 0;

    private ValidatorOfStruct(Validator.ShapeValidator validator, SdkSchema schema) {
        this.validator = validator;
        this.schema = schema;
    }

    static <T> void validate(
        Validator.ShapeValidator validator,
        SdkSchema schema,
        T structState,
        BiConsumer<T, ShapeSerializer> consumer
    ) {
        var statefulStructSerializer = new ValidatorOfStruct(validator, schema);
        consumer.accept(structState, statefulStructSerializer);
        if (schema.requiredStructureMemberBitfield != statefulStructSerializer.setFields) {
            for (var member : statefulStructSerializer.getMissingMembers()) {
                validator.addError(new ValidationError.RequiredValidationFailure(validator.createPath(), member));
            }
        }
    }

    private Set<String> getMissingMembers() {
        Set<String> result = new TreeSet<>();
        for (var member : schema.members()) {
            if (member.isRequiredByValidation() && !isPresent(member)) {
                result.add(member.memberName());
            }
        }
        return result;
    }

    private boolean isPresent(SdkSchema member) {
        return (setFields & member.requiredByValidationBitmask) != 0;
    }

    private void before(SdkSchema member) {
        setFields |= member.requiredByValidationBitmask;
        validator.pushPath(member.memberName());
    }

    @Override
    public void writeBoolean(SdkSchema member, boolean value) {
        before(member);
        validator.writeBoolean(member, value);
        validator.popPath();
    }

    @Override
    public void writeByte(SdkSchema member, byte value) {
        before(member);
        validator.writeByte(member, value);
        validator.popPath();
    }

    @Override
    public void writeShort(SdkSchema member, short value) {
        before(member);
        validator.writeShort(member, value);
        validator.popPath();
    }

    @Override
    public void writeInteger(SdkSchema member, int value) {
        before(member);
        validator.writeInteger(member, value);
        validator.popPath();
    }

    @Override
    public void writeLong(SdkSchema member, long value) {
        before(member);
        validator.writeLong(member, value);
        validator.popPath();
    }

    @Override
    public void writeFloat(SdkSchema member, float value) {
        before(member);
        validator.writeFloat(member, value);
        validator.popPath();
    }

    @Override
    public void writeDouble(SdkSchema member, double value) {
        before(member);
        validator.writeDouble(member, value);
        validator.popPath();
    }

    @Override
    public void writeBigInteger(SdkSchema member, BigInteger value) {
        before(member);
        validator.writeBigInteger(member, value);
        validator.popPath();
    }

    @Override
    public void writeBigDecimal(SdkSchema member, BigDecimal value) {
        before(member);
        validator.writeBigDecimal(member, value);
        validator.popPath();
    }

    @Override
    public void writeBlob(SdkSchema member, byte[] value) {
        before(member);
        validator.writeBlob(member, value);
        validator.popPath();
    }

    @Override
    public void writeString(SdkSchema member, String value) {
        before(member);
        validator.writeString(member, value);
        validator.popPath();
    }

    @Override
    public void writeTimestamp(SdkSchema member, Instant value) {
        before(member);
        validator.writeTimestamp(member, value);
        validator.popPath();
    }

    @Override
    public void writeDocument(SdkSchema member, Document value) {
        before(member);
        validator.writeDocument(value);
        validator.popPath();
    }

    @Override
    public <T> void writeList(SdkSchema member, T state, BiConsumer<T, ShapeSerializer> consumer) {
        before(member);
        validator.writeList(member, state, consumer);
        validator.popPath();
    }

    @Override
    public <T> void writeMap(SdkSchema member, T state, BiConsumer<T, MapSerializer> consumer) {
        before(member);
        validator.writeMap(member, state, consumer);
        validator.popPath();
    }

    @Override
    public <T> void writeStruct(SdkSchema member, T structState, BiConsumer<T, ShapeSerializer> consumer) {
        before(member);
        validator.writeStruct(member, structState, consumer);
        validator.popPath();
    }

    @Override
    public void writeNull(SdkSchema member) {
        // A null member does not count as present so don't call before() here.
        validator.pushPath(member.memberName());
        validator.writeNull(member);
        validator.popPath();
    }
}
