/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import software.amazon.smithy.java.runtime.core.serde.ListSerializer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SerializationException;
import software.amazon.smithy.java.runtime.core.serde.ShapeSerializer;
import software.amazon.smithy.java.runtime.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.SparseTrait;

/**
 * Validates shapes.
 *
 * <p>Validation can be applied to any {@link SerializableShape}.
 *
 * <pre>{@code
 * Validator validator = Validator.builder().build();
 * List<ValidationError> errors = validator.validate(someShape);
 * }</pre>
 *
 * <p>Validator is thread safe.
 */
public final class Validator {

    private static final TraitKey<SparseTrait> SPARSE_TRAIT = TraitKey.get(SparseTrait.class);

    private final int maxDepth;
    private final int maxAllowedErrors;

    private Validator(Builder builder) {
        this.maxAllowedErrors = builder.maxAllowedErrors;
        this.maxDepth = builder.maxDepth;
    }

    /**
     * Create a builder responsible for building a {@link Validator}.
     *
     * @return the created builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Validate a shape and return any encountered errors.
     *
     * @param shape Shape to validate.
     * @return the validation errors produced by the shape.
     */
    public List<ValidationError> validate(SerializableShape shape) {
        var shapeValidator = new ShapeValidator(maxAllowedErrors, maxDepth);
        try {
            shape.serialize(shapeValidator);
            return shapeValidator.errors;
        } catch (ValidationShortCircuitException ignored) {
            return shapeValidator.errors;
        }
    }

    /**
     * Builds a {@link  Validator}.
     */
    public static final class Builder {

        private int maxDepth = 100;
        private int maxAllowedErrors = 100;

        private Builder() {}

        /**
         * Build the {@link Validator}.
         *
         * @return the created Validator.
         */
        public Validator build() {
            return new Validator(this);
        }

        /**
         * Set the maximum allowed depth of the evaluated value.
         *
         * @param maxDepth Max allowed depth (default is 100).
         * @return the builder.
         */
        public Builder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        /**
         * Set the maximum number of errors to find before giving up and returning.
         *
         * @param maxAllowedErrors Maximum number of errors to find before giving up (default is 100).
         * @return the builder.
         */
        public Builder maxAllowedErrors(int maxAllowedErrors) {
            this.maxAllowedErrors = maxAllowedErrors;
            return this;
        }
    }

    /**
     * An error that short circuits further validation.
     */
    static final class ValidationShortCircuitException extends SerializationException {
        ValidationShortCircuitException() {
            super("Stop further validation");
        }
    }

    static final class ShapeValidator implements ShapeSerializer, MapSerializer {

        private static final int STARTING_PATH_SIZE = 4;
        private final int maxAllowedErrors;
        private final int maxDepth;
        private final ListSerializer listValidator;
        private final List<ValidationError> errors = new ArrayList<>();
        private Object[] path;
        private int depth = 0;

        /**
         * Tracks the number of elements in a list or map.
         *
         * @see #currentSchema
         */
        private int elementCount = 0;

        /**
         * Tracks the current shape being validated, used specifically to test that null values are permitted only in
         * sparse collections. Each time the currentSchema is changed, the previous {@link #elementCount} and schema must
         * be stored in a variable, the next shape is validated, and then the schema and count are restored.
         */
        private Schema currentSchema = null;

        private ShapeValidator(int maxAllowedErrors, int maxDepth) {
            this.maxAllowedErrors = maxAllowedErrors;
            this.maxDepth = maxDepth;

            // The length of the path will never exceed the current depth + the maxDepth, removing a conditional in
            // pushPath and ensuring we don't over-allocate. Default to 6 initially, but go lower if maxDepth is lower.
            this.path = new Object[Math.min(STARTING_PATH_SIZE, maxDepth)];

            // Every list is validated with this serializer. Because it's reused, the element count of the list can't
            // be used. Instead, the number of elements is tracked in the elementCount member of Validator.
            listValidator = new ListSerializer(this, this::betweenListElements);
        }

        private void betweenListElements(int ignoredPosition) {
            swapPath(elementCount);
            elementCount++;
        }

        private void resetValidatorState() {
            elementCount = 0;
            currentSchema = null;
            depth = 0;
        }

        void pushPath(Object pathSegment) {
            // Rather than check if the depth exceeds maxDepth _and_ if depth == path.length, we instead always
            // ensure that the path length never exceeds maxDepth.
            if (depth == path.length) {
                // Resize the path if needed by multiplying the size by 1.5.
                int remainingDepth = maxDepth - depth;
                if (remainingDepth == 0) {
                    addError(new ValidationError.DepthValidationFailure(createPath(), maxDepth));
                    throw new Validator.ValidationShortCircuitException();
                } else {
                    int newSize = Math.min(remainingDepth, depth + (depth >> 1));
                    Object[] resized = new Object[newSize];
                    System.arraycopy(path, 0, resized, 0, path.length);
                    path = resized;
                }
            }

            path[depth++] = pathSegment;
        }

        void swapPath(Object pathSegment) {
            path[depth - 1] = pathSegment;
        }

        void popPath() {
            depth--;
        }

        String createPath() {
            if (depth == 0) {
                return "/";
            } else {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < depth; i++) {
                    builder.append('/').append(path[i].toString());
                }
                return builder.toString();
            }
        }

        void addError(ValidationError error) {
            if (errors.size() == maxAllowedErrors) {
                throw new Validator.ValidationShortCircuitException();
            }
            errors.add(error);
        }

        @Override
        public void writeStruct(Schema schema, SerializableStruct struct) {
            // Track the current schema and count.
            var previousSchema = currentSchema;
            var previousCount = elementCount;
            currentSchema = schema;
            elementCount = 0; // note that we don't track the count of structure members.
            switch (schema.type()) {
                case STRUCTURE -> ValidatorOfStruct.validate(this, schema, struct);
                case UNION -> ValidatorOfUnion.validate(this, schema, struct);
                default -> checkType(schema, ShapeType.STRUCTURE); // this is guaranteed to fail type checking.
            }
            currentSchema = previousSchema;
            elementCount = previousCount;
        }

        @Override
        public <T> void writeList(Schema schema, T state, int size, BiConsumer<T, ShapeSerializer> consumer) {
            checkType(schema, ShapeType.LIST);

            if (size == 0) {
                checkListLength(schema, 0);
            } else {
                // Track the current schema and count.
                var previousSchema = currentSchema;
                var previousCount = elementCount;
                currentSchema = schema;
                elementCount = 0;

                // Push a preliminary value of null. Each list element will swap this path position with its index.
                pushPath(null);
                consumer.accept(state, listValidator);
                popPath();

                // Grab the count and reset the schema and count.
                var count = elementCount;
                currentSchema = previousSchema;
                elementCount = previousCount;

                checkListLength(schema, count);
            }
        }

        private void checkListLength(Schema schema, int count) {
            // Ensure the list has an acceptable length.
            if (count < schema.minLengthConstraint) {
                addError(new ValidationError.LengthValidationFailure(createPath(), count, schema));
            } else if (count > schema.maxLengthConstraint) {
                addError(new ValidationError.LengthValidationFailure(createPath(), count, schema));
            }
        }

        @Override
        public <T> void writeMap(Schema schema, T state, int size, BiConsumer<T, MapSerializer> consumer) {
            checkType(schema, ShapeType.MAP);
            if (size == 0) {
                checkMapLength(schema, 0);
            } else {
                // Track the current schema and count.
                var previousSchema = currentSchema;
                var previousCount = elementCount;
                currentSchema = schema;
                elementCount = 0;

                // Push a preliminary map key and key/value holder of null. These values are replaced as map keys and
                // values are validated.
                pushPath(null);
                pushPath(null);
                consumer.accept(state, this);
                popPath();
                popPath();

                // Grab the count and reset the schema and count.
                var count = elementCount;
                currentSchema = previousSchema;
                elementCount = previousCount;
                checkMapLength(schema, count);
            }
        }

        private void checkMapLength(Schema schema, int count) {
            // Ensure the map is properly sized.
            if (count < schema.minLengthConstraint) {
                addError(new ValidationError.LengthValidationFailure(createPath(), count, schema));
            } else if (count > schema.maxLengthConstraint) {
                addError(new ValidationError.LengthValidationFailure(createPath(), count, schema));
            }
        }

        // MapSerializer implementation to write a map key.

        @Override
        public <T> void writeEntry(
            Schema keySchema,
            String key,
            T state,
            BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            elementCount++;
            path[depth - 2] = key; // set /map/<key>
            path[depth - 1] = "key"; // set /map/<key>/key
            writeString(keySchema, key);
            path[depth - 1] = "value"; // set /map/<key>/value
            valueSerializer.accept(state, this);
        }

        @Override
        public void writeBoolean(Schema schema, boolean value) {
            checkType(schema, ShapeType.BOOLEAN);
        }

        @Override
        public void writeByte(Schema schema, byte value) {
            checkType(schema, ShapeType.BYTE);
            validateRange(schema, value, schema.minLongConstraint, schema.maxLongConstraint);
        }

        @Override
        public void writeShort(Schema schema, short value) {
            checkType(schema, ShapeType.SHORT);
            validateRange(schema, value, schema.minLongConstraint, schema.maxLongConstraint);
        }

        @Override
        public void writeInteger(Schema schema, int value) {
            // Validate range traits for normal integers, and validate intEnum for INT_ENUM values.
            switch (schema.type()) {
                case INTEGER -> validateRange(schema, value, schema.minLongConstraint, schema.maxLongConstraint);
                case INT_ENUM -> {
                    if (!schema.intEnumValues().isEmpty() && !schema.intEnumValues().contains(value)) {
                        addError(new ValidationError.IntEnumValidationFailure(createPath(), value, schema));
                    }
                }
                default -> checkType(schema, ShapeType.INTEGER); // it's invalid.
            }
        }

        @Override
        public void writeLong(Schema schema, long value) {
            checkType(schema, ShapeType.LONG);
            validateRange(schema, value, schema.minLongConstraint, schema.maxLongConstraint);
        }

        @Override
        public void writeFloat(Schema schema, float value) {
            checkType(schema, ShapeType.FLOAT);
            validateRange(schema, value, schema.minDoubleConstraint, schema.maxDoubleConstraint);
        }

        @Override
        public void writeDouble(Schema schema, double value) {
            checkType(schema, ShapeType.DOUBLE);
            validateRange(schema, value, schema.minDoubleConstraint, schema.maxDoubleConstraint);
        }

        @Override
        public void writeBigInteger(Schema schema, BigInteger value) {
            checkType(schema, ShapeType.BIG_INTEGER);
            if (schema.minRangeConstraint != null && value.compareTo(schema.minRangeConstraint.toBigInteger()) < 0) {
                emitRangeError(schema, value);
            } else if (schema.maxRangeConstraint != null && value.compareTo(
                schema.maxRangeConstraint.toBigInteger()
            ) > 0) {
                emitRangeError(schema, value);
            }
        }

        @Override
        public void writeBigDecimal(Schema schema, BigDecimal value) {
            checkType(schema, ShapeType.BIG_DECIMAL);
            if (schema.minRangeConstraint != null && value.compareTo(schema.minRangeConstraint) < 0) {
                emitRangeError(schema, value);
            } else if (schema.maxRangeConstraint != null && value.compareTo(schema.maxRangeConstraint) > 0) {
                emitRangeError(schema, value);
            }
        }

        @Override
        public void writeString(Schema schema, String value) {
            switch (schema.type()) {
                case STRING, ENUM -> schema.stringValidation.apply(schema, value, this);
                default -> checkType(schema, ShapeType.STRING); // it's invalid, and calling this adds an error.
            }
        }

        @Override
        public void writeBlob(Schema schema, ByteBuffer value) {
            checkType(schema, ShapeType.BLOB);
            int length = value.remaining();
            if (length < schema.minLengthConstraint || length > schema.maxLengthConstraint) {
                addError(new ValidationError.LengthValidationFailure(createPath(), length, schema));
            }
        }

        @Override
        public void writeTimestamp(Schema schema, Instant value) {
            checkType(schema, ShapeType.TIMESTAMP);
        }

        @Override
        public void writeDocument(Schema schema, Document document) {
            checkType(schema, ShapeType.DOCUMENT);
        }

        @Override
        public void writeNull(Schema schema) {
            // This class only needs to validate null values when the current shape under validation is a list or map.
            // If it's a list or map, and it doesn't have the sparse trait, then null isn't allowed.
            // Note that union and structure member validation is handled in other classes (e.g., ValidatorOfUnion).
            if (currentSchema != null) {
                if (currentSchema.type() == ShapeType.MAP || currentSchema.type() == ShapeType.LIST) {
                    if (!currentSchema.hasTrait(SPARSE_TRAIT)) {
                        addError(new ValidationError.SparseValidationFailure(createPath(), currentSchema));
                    }
                }
            }
        }

        private void validateRange(Schema schema, long value, long min, long max) {
            if (value < min || value > max) {
                emitRangeError(schema, value);
            }
        }

        private void validateRange(Schema schema, double value, double min, double max) {
            if (value < min || value > max) {
                emitRangeError(schema, value);
            }
        }

        private void emitRangeError(Schema schema, Number value) {
            addError(new ValidationError.RangeValidationFailure(createPath(), value, schema));
        }

        private void checkType(Schema schema, ShapeType type) {
            if (schema.type() != type) {
                addError(new ValidationError.TypeValidationFailure(createPath(), type, schema));
                // Stop any further validation if an incorrect type is given. This should only be encountered when data
                // is emitted from something manually and not from an actual modeled shape.
                throw new ValidationShortCircuitException();
            }
        }
    }
}
