/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;

final class ShapeValidator {

    static final ValidationShortCircuitException SHORT_CIRCUIT_EXCEPTION =
            new ValidationShortCircuitException();

    private static final int STARTING_PATH_SIZE = 4;

    private final int maxAllowedErrors;
    private final int maxDepth;
    private final boolean failFast;
    private Object[] path;
    private int depth;
    private final List<ValidationError> errors = new ArrayList<>();

    private Schema currentSchema;
    private int elementCount = -1;
    private PresenceTracker currentPresenceTracker;

    ShapeValidator(int maxDepth, int maxAllowedErrors) {
        this(maxDepth, maxAllowedErrors, false);
    }

    ShapeValidator(int maxDepth, int maxAllowedErrors, boolean failFast) {
        this.maxAllowedErrors = maxAllowedErrors;
        this.maxDepth = maxDepth;

        // The length of the path will never exceed the current depth + the maxDepth, removing a conditional in
        // pushPath and ensuring we don't over-allocate. Default to 6 initially, but go lower if maxDepth is lower.
        this.path = new Object[Math.min(STARTING_PATH_SIZE, maxDepth)];

        // Every list is validated with this serializer. Because it's reused, the element count of the list can't
        // be used. Instead, the number of elements is tracked in the elementCount member of Validator.
        this.failFast = failFast;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }

    public void betweenListElements(int ignored) {
        swapPath(elementCount);
        elementCount++;
    }

    void swapPath(Object pathSegment) {
        path[depth - 1] = pathSegment;
    }

    void popPath() {
        depth--;
    }

    void pushPath(Object pathSegment) {
        // Rather than check if the depth exceeds maxDepth _and_ if depth == path.length, we instead always
        // ensure that the path length never exceeds maxDepth.
        if (depth == path.length) {
            // Resize the path if needed by multiplying the size by 1.5.
            if (path.length == maxDepth) {
                addError(new ValidationError.DepthValidationFailure(createPath(), maxDepth));
                throw SHORT_CIRCUIT_EXCEPTION;
            }
            int oldSize = path.length;
            int newSize = oldSize + (oldSize >> 1);
            if (newSize > maxDepth) {
                newSize = maxDepth;
            }
            Object[] resized = new Object[newSize];
            System.arraycopy(path, 0, resized, 0, path.length);
            path = resized;
        }

        path[depth++] = pathSegment;
    }

    public String createPath() {
        if (depth == 0) {
            return "/";
        } else {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < depth; i++) {
                var curSegment = path[i];
                if (curSegment != null) {
                    if (i > 0 && path[i - 1] instanceof Integer && curSegment.equals("member")) {
                        continue;
                    }
                    builder.append('/').append(curSegment);
                }
            }
            return builder.toString();
        }
    }

    public void addError(ValidationError error) {
        errors.add(error);
        if (errors.size() == maxAllowedErrors) {
            throw SHORT_CIRCUIT_EXCEPTION;
        }
        if (failFast) {
            throw SHORT_CIRCUIT_EXCEPTION;
        }
    }

    public State startList(Schema schema, int size) {
        if (schema.isMember()) {
            pushPath(schema.memberName());
            currentPresenceTracker.setMember(schema);
        }
        checkType(schema, ShapeType.LIST);
        checkListLength(schema, size);
        var state = saveState();
        elementCount = 0;
        if (size > 0) {
            currentSchema = schema;
            pushPath(null);
        }
        return state;
    }

    public State startList(Schema schema) {
        checkType(schema, ShapeType.LIST);
        if (schema.isMember()) {
            pushPath(schema.memberName());
            currentPresenceTracker.setMember(schema);
        }
        var state = saveState();
        elementCount = 0;
        currentSchema = schema;
        pushPath(null);
        return state;
    }

    public void endList(List<?> list, State state, Schema schema) {
        if (schema.uniqueItemsConstraint && list.size() > 1) {
            Set<Object> set = new HashSet<>(list.size());
            for (int i = 0; i < list.size(); i++) {
                if (!set.add(list.get(i))) {
                    swapPath(i);
                    addError(new ValidationError.UniqueItemConflict(createPath(), i, currentSchema));
                }
            }
        }
        popPath();
        restoreState(state);

    }

    public void endListWithSizeCheck(List<?> list, State state, Schema schema) {
        checkListLength(schema, list.size());
        endList(list, state, schema);
    }

    private void checkListLength(Schema schema, int count) {
        // Ensure the list has an acceptable length.
        if (count < schema.minLengthConstraint) {
            addError(new ValidationError.LengthValidationFailure(createPath(), count, schema));
        } else if (count > schema.maxLengthConstraint) {
            addError(new ValidationError.LengthValidationFailure(createPath(), count, schema));
        }
    }

    public State startMap(Schema schema, int size) {
        if (schema.isMember()) {
            pushPath(schema.memberName());
            currentPresenceTracker.setMember(schema);
        }
        checkType(schema, ShapeType.MAP);
        checkMapLength(schema, size);
        var state = saveState();
        if (size > 0) {
            // Track the current schema and count.
            currentSchema = schema;
            elementCount = 0;
        }
        return state;
    }

    public State startMap(Schema schema) {
        checkType(schema, ShapeType.MAP);
        if (schema.isMember()) {
            pushPath(schema.memberName());
            currentPresenceTracker.setMember(schema);
        }
        var state = saveState();
        // Track the current schema and count.
        currentSchema = schema;
        elementCount = 0;
        return state;
    }

    private State saveState() {
        return new State(currentSchema, elementCount, currentPresenceTracker);
    }

    private void checkMapLength(Schema schema, int count) {
        // Ensure the map is properly sized.
        if (count < schema.minLengthConstraint) {
            addError(new ValidationError.LengthValidationFailure(createPath(), count, schema));
        } else if (count > schema.maxLengthConstraint) {
            addError(new ValidationError.LengthValidationFailure(createPath(), count, schema));
        }
    }

    public void endMap(State state) {
        restoreState(state);
        popPath();
    }

    public void endMap(Schema schema, int size, State state) {
        checkMapLength(schema, size);
        endMap(state);
    }

    static final class ValidationShortCircuitException extends RuntimeException {

        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    public State startStruct(Schema schema) {
        boolean isUnion = false;
        if (currentPresenceTracker != null) {
            pushPath(schema.memberName());
            currentPresenceTracker.setMember(schema);
        }
        if (schema.type() != ShapeType.STRUCTURE && !(isUnion = schema.type() == ShapeType.UNION)) {
            checkType(schema, ShapeType.STRUCTURE); //this is guaranteed to fail type checking.
        }
        var curState = saveState();
        currentSchema = schema;
        elementCount = 0;
        currentPresenceTracker = isUnion ? new UnionPresenceTracker(this, schema) : PresenceTracker.of(schema);
        return curState;
    }

    public void endStruct(Schema schema, State prevState) {
        if (!currentPresenceTracker.allSet()) {
            if (schema.type() == ShapeType.STRUCTURE) {
                for (var member : currentPresenceTracker.getMissingMembers()) {
                    addError(new ValidationError.RequiredValidationFailure(createPath(), member, currentSchema));
                }
            } else {
                addError(new ValidationError.UnionValidationFailure(createPath(),
                        "No member is set in the union",
                        schema));
            }
        }
        if (schema.isMember()) {
            popPath();
        }
        restoreState(prevState);
    }

    private void restoreState(State state) {
        currentSchema = state.schema;
        elementCount = state.elementCount;
        currentPresenceTracker = state.presenceTracker;
    }

    public void validateBoolean(Schema schema) {
        pushPath(schema.memberName());
        currentPresenceTracker.setMember(schema);
        checkType(schema, ShapeType.BOOLEAN);
        popPath();
    }

    public void validateByte(Schema schema, byte value) {
        currentPresenceTracker.setMember(schema);
        pushPath(schema.memberName());
        checkType(schema, ShapeType.BYTE);
        validateRange(schema, value);
        popPath();
    }

    public void validateShort(Schema schema, short value) {
        pushPath(schema.memberName());
        currentPresenceTracker.setMember(schema);
        checkType(schema, ShapeType.SHORT);
        validateRange(schema, value);
        popPath();
    }

    public void validateInteger(Schema schema, int value) {
        pushPath(schema.memberName());
        currentPresenceTracker.setMember(schema);
        switch (schema.type()) {
            case INTEGER -> validateRange(schema, value);
            case INT_ENUM -> {
                if (!schema.intEnumValues().isEmpty() && !schema.intEnumValues().contains(value)) {
                    addError(new ValidationError.IntEnumValidationFailure(createPath(), value, schema));
                }
            }
            default -> checkType(schema, ShapeType.INTEGER); // it's invalid.
        }
        popPath();
    }

    public void validateFloat(Schema schema, float value) {
        pushPath(schema.memberName());
        currentPresenceTracker.setMember(schema);
        checkType(schema, ShapeType.FLOAT);
        validateRange(schema, value, schema.minDoubleConstraint, schema.maxDoubleConstraint);
        popPath();
    }

    public void validateDouble(Schema schema, double value) {
        pushPath(schema.memberName());
        currentPresenceTracker.setMember(schema);
        checkType(schema, ShapeType.DOUBLE);
        validateRange(schema, value, schema.minDoubleConstraint, schema.maxDoubleConstraint);
        popPath();
    }

    public void validateBigInteger(Schema schema, BigInteger value) {
        if (value != null) {
            pushPath(schema.memberName());
            currentPresenceTracker.setMember(schema);
            checkType(schema, ShapeType.BIG_INTEGER);
            if (schema.minRangeConstraint != null && value.compareTo(schema.minRangeConstraint.toBigInteger()) < 0) {
                emitRangeError(schema, value);
            } else if (schema.maxRangeConstraint != null && value.compareTo(
                    schema.maxRangeConstraint.toBigInteger()) > 0) {
                emitRangeError(schema, value);
            }
            popPath();
        }
    }

    public void validateBigDecimal(Schema schema, BigDecimal value) {
        if (value != null) {
            pushPath(schema.memberName());
            currentPresenceTracker.setMember(schema);
            checkType(schema, ShapeType.BIG_DECIMAL);
            if (schema.minRangeConstraint != null && value.compareTo(schema.minRangeConstraint) < 0) {
                emitRangeError(schema, value);
            } else if (schema.maxRangeConstraint != null && value.compareTo(schema.maxRangeConstraint) > 0) {
                emitRangeError(schema, value);
            }
            popPath();
        }
    }

    public void validateString(Schema schema, String value) {
        if (value != null) {
            pushPath(schema.memberName());
            currentPresenceTracker.setMember(schema);
            switch (schema.type()) {
                case STRING, ENUM -> schema.stringValidation.apply(schema, value, this);
                default -> checkType(schema, ShapeType.STRING); // it's invalid, and calling this adds an error.
            }
            popPath();
        }
    }

    public void validateBlob(Schema schema, ByteBuffer value) {
        if (value != null) {
            pushPath(schema.memberName());
            currentPresenceTracker.setMember(schema);
            checkType(schema, ShapeType.BLOB);
            int length = value.remaining();
            if (length < schema.minLengthConstraint || length > schema.maxLengthConstraint) {
                addError(new ValidationError.LengthValidationFailure(createPath(), length, schema));
            }
            popPath();
        }
    }

    public void validateTimestamp(Schema schema, Instant value) {
        if (value != null) {
            pushPath(schema.memberName());
            currentPresenceTracker.setMember(schema);
            checkType(schema, ShapeType.TIMESTAMP);
            popPath();
        }
    }

    public void validateDocument(Schema schema, Document document) {
        if (document != null) {
            pushPath(schema.memberName());
            currentPresenceTracker.setMember(schema);
            checkType(schema, ShapeType.DOCUMENT);
            popPath();
        }
    }

    public void validateLong(Schema schema, long value) {
        pushPath(schema.memberName());
        currentPresenceTracker.setMember(schema);
        checkType(schema, ShapeType.LONG);
        validateRange(schema, value);
        popPath();
    }

    public void startEntry(Schema schema, String key) {
        elementCount++;
        int keyDepth = depth;
        pushPath("key");
        pushPath(key);
        switch (schema.type()) {
            case STRING, ENUM -> schema.stringValidation.apply(schema, key, this);
            default -> checkType(schema, ShapeType.STRING); // it's invalid, and calling this adds an error.
        }
        //remove the key now
        path[keyDepth] = null;
    }

    public void endEntry() {
        popPath();
        popPath();
    }

    public void validateNull(Schema schema) {
        if (currentSchema != null &&
                (currentSchema.type() == ShapeType.MAP || currentSchema.type() == ShapeType.LIST)
                &&
                !currentSchema.hasTrait(TraitKey.SPARSE_TRAIT)) {
            addError(new ValidationError.SparseValidationFailure(createPath(), currentSchema));
        }

    }

    private void validateRange(Schema schema, long value) {
        if (schema.hasRangeConstraint && (value < schema.minLongConstraint || value > schema.maxLongConstraint)) {
            emitRangeError(schema, value);
        }
    }

    private void validateRange(Schema schema, double value, double min, double max) {
        if (schema.hasRangeConstraint && (value < min || value > max)) {
            emitRangeError(schema, value);
        }
    }

    private void emitRangeError(Schema schema, Number value) {
        addError(new ValidationError.RangeValidationFailure(createPath(),
                schema.rangeValidationFailureMessage,
                value,
                schema));
    }

    private void checkType(Schema schema, ShapeType type) {
        if (schema.type() != type) {
            addError(new ValidationError.TypeValidationFailure(createPath(), type, schema));
            // Stop any further validation if an incorrect type is given. This should only be encountered when data
            // is emitted from something manually and not from an actual modeled shape.
            throw SHORT_CIRCUIT_EXCEPTION;
        }
    }

    record State(Schema schema, int elementCount, PresenceTracker presenceTracker) {}

    static final class UnionPresenceTracker extends PresenceTracker {

        private Schema setMember;
        private final ShapeValidator shapeValidator;
        private final Schema schema;

        private UnionPresenceTracker(ShapeValidator shapeValidator, Schema schema) {
            this.shapeValidator = shapeValidator;
            this.schema = schema;
        }

        @Override
        public void setMember(Schema memberSchema) {
            if (setMember == null) {
                setMember = memberSchema;
                return;
            }
            shapeValidator.addError(new ValidationError.UnionValidationFailure(shapeValidator.createPath(),
                    "Union member conflicts with '" + setMember.memberName() + "'",
                    schema));
        }

        @Override
        public boolean checkMember(Schema memberSchema) {
            return memberSchema.equals(setMember);
        }

        @Override
        public boolean allSet() {
            return setMember != null;
        }

        @Override
        public Set<String> getMissingMembers() {
            return Set.of();
        }
    }
}
