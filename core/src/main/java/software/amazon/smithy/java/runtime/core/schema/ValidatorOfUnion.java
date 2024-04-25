/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;

/**
 * Ensures that exactly one member is set in a union, and validates the set member.
 */
final class ValidatorOfUnion implements ShapeSerializer {

    private final Validator.ShapeValidator validator;
    private String setMember;

    private ValidatorOfUnion(Validator.ShapeValidator validator) {
        this.validator = validator;
    }

    static <T> void validate(
        Validator.ShapeValidator validator,
        T unionState,
        BiConsumer<T, ShapeSerializer> consumer
    ) {
        var unionValidator = new ValidatorOfUnion(validator);
        consumer.accept(unionState, unionValidator);
        if (unionValidator.setMember == null) {
            validator.addError(
                new ValidationError.UnionValidationFailure(validator.createPath(), "No member is set in the union")
            );
        }
    }

    private boolean validateSetValue(SdkSchema schema, Object value) {
        // Don't further validate the value if it's null.
        return value != null && validateSetValue(schema);
    }

    private boolean validateSetValue(SdkSchema schema) {
        if (setMember != null) {
            validator.addError(
                new ValidationError.UnionValidationFailure(
                    validator.createPath(),
                    "Union member conflicts with '" + setMember + "'"
                )
            );
            return false;
        } else {
            setMember = schema.memberName();
            return true;
        }
    }

    @Override
    public void writeBoolean(SdkSchema member, boolean value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeBoolean(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeByte(SdkSchema member, byte value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeByte(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeShort(SdkSchema member, short value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeShort(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeInteger(SdkSchema member, int value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeInteger(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeLong(SdkSchema member, long value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeLong(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeFloat(SdkSchema member, float value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeFloat(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeDouble(SdkSchema member, double value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeDouble(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeBigInteger(SdkSchema member, BigInteger value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member, value)) {
            validator.writeBigInteger(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeBigDecimal(SdkSchema member, BigDecimal value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member, value)) {
            validator.writeBigDecimal(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeBlob(SdkSchema member, byte[] value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member, value)) {
            validator.writeBlob(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeString(SdkSchema member, String value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member, value)) {
            validator.writeString(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeTimestamp(SdkSchema member, Instant value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member, value)) {
            validator.writeTimestamp(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeDocument(SdkSchema member, Document value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member, value)) {
            validator.writeDocument(value);
        }
        validator.popPath();
    }

    @Override
    public <T> void writeList(SdkSchema member, T state, BiConsumer<T, ShapeSerializer> consumer) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeList(member, state, consumer);
        }
        validator.popPath();
    }

    @Override
    public <T> void writeMap(SdkSchema member, T state, BiConsumer<T, MapSerializer> consumer) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeMap(member, state, consumer);
        }
        validator.popPath();
    }

    @Override
    public <T> void writeStruct(SdkSchema member, T structState, BiConsumer<T, ShapeSerializer> consumer) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeStruct(member, structState, consumer);
        }
        validator.popPath();
    }

    @Override
    public void writeNull(SdkSchema schema) {
        // null values in unions are ignored.
    }
}
