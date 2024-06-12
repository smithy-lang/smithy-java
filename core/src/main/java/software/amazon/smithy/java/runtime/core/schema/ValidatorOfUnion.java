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
    private final Schema schema;
    private String setMember;

    private ValidatorOfUnion(Validator.ShapeValidator validator, Schema schema) {
        this.validator = validator;
        this.schema = schema;
    }

    static void validate(Validator.ShapeValidator validator, Schema schema, SerializableStruct struct) {
        var unionValidator = new ValidatorOfUnion(validator, schema);
        struct.serializeMembers(unionValidator);
        unionValidator.checkResult();
    }

    private void checkResult() {
        if (setMember == null) {
            var err = new ValidationError.UnionValidationFailure(
                validator.createPath(),
                "No member is set in the union",
                schema
            );
            validator.addError(err);
        }
    }

    private boolean validateSetValue(Schema schema, Object value) {
        // Don't further validate the value if it's null.
        return value != null && validateSetValue(schema);
    }

    private boolean validateSetValue(Schema schema) {
        if (setMember != null) {
            String message = "Union member conflicts with '" + setMember + "'";
            validator.addError(new ValidationError.UnionValidationFailure(validator.createPath(), message, schema));
            return false;
        } else {
            setMember = schema.memberName();
            return true;
        }
    }

    @Override
    public void writeBoolean(Schema member, boolean value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeBoolean(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeByte(Schema member, byte value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeByte(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeShort(Schema member, short value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeShort(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeInteger(Schema member, int value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeInteger(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeLong(Schema member, long value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeLong(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeFloat(Schema member, float value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeFloat(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeDouble(Schema member, double value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeDouble(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeBigInteger(Schema member, BigInteger value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member, value)) {
            validator.writeBigInteger(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeBigDecimal(Schema member, BigDecimal value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member, value)) {
            validator.writeBigDecimal(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeBlob(Schema member, byte[] value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member, value)) {
            validator.writeBlob(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeString(Schema member, String value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member, value)) {
            validator.writeString(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeTimestamp(Schema member, Instant value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member, value)) {
            validator.writeTimestamp(member, value);
        }
        validator.popPath();
    }

    @Override
    public void writeDocument(Schema member, Document value) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member, value)) {
            validator.writeDocument(member, value);
        }
        validator.popPath();
    }

    @Override
    public <T> void writeList(Schema member, T state, BiConsumer<T, ShapeSerializer> consumer) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeList(member, state, consumer);
        }
        validator.popPath();
    }

    @Override
    public <T> void writeMap(Schema member, T state, BiConsumer<T, MapSerializer> consumer) {
        validator.pushPath(member.memberName());
        if (validateSetValue(member)) {
            validator.writeMap(member, state, consumer);
        }
        validator.popPath();
    }

    @Override
    public void writeStruct(Schema member, SerializableStruct struct) {
        validator.pushPath(member);
        if (validateSetValue(member)) {
            validator.writeStruct(member, struct);
        }
        validator.popPath();
    }

    @Override
    public void writeNull(Schema schema) {
        // null values in unions are ignored.
    }
}
