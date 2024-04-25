/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import software.amazon.smithy.java.runtime.core.serde.ListSerializer;
import software.amazon.smithy.java.runtime.core.serde.MapSerializer;
import software.amazon.smithy.java.runtime.core.serde.SdkSerdeException;
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
 * <p>Note that Validator is not thread safe.
 */
public final class Validator {

    private final ShapeValidator shapeValidator;

    private Validator(Builder builder) {
        shapeValidator = new ShapeValidator(builder.maxAllowedErrors, builder.maxDepth);
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
        try {
            shape.serialize(shapeValidator);
        } catch (ValidationShortCircuitException ignored) {
            // If an error occurred, reset the state of the validator.
            shapeValidator.elementCount = 0;
            shapeValidator.currentSchema = null;
            shapeValidator.depth = 0;
        }

        if (shapeValidator.errors.isEmpty()) {
            return List.of();
        } else {
            var result = new ArrayList<>(shapeValidator.errors);
            shapeValidator.errors.clear();
            return result;
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
     * Adds an error and short circuits further validation.
     */
    static final class ValidationShortCircuitException extends SdkSerdeException {
        ValidationShortCircuitException() {
            super("Stop further validation");
        }
    }

    static final class ShapeValidator implements ShapeSerializer, MapSerializer {

        private final int maxAllowedErrors;
        private final int maxDepth;
        private final ListSerializer listValidator;
        private final List<ValidationError> errors = new ArrayList<>();
        private Object[] path = new Object[8];
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
        private SdkSchema currentSchema = null;

        ShapeValidator(int maxAllowedErrors, int maxDepth) {
            this.maxAllowedErrors = maxAllowedErrors;
            this.maxDepth = maxDepth;

            // Every list is validated with this serializer. Because it's reused, the element count of the list can't
            // be used. Instead, the number of elements is tracked in the elementCount member of Validator.
            listValidator = new ListSerializer(this, ignoredPosition -> swapPath(elementCount++));
        }

        void pushPath(Object pathSegment) {
            if (depth == maxDepth) {
                addError(new ValidationError.DepthValidationFailure(createPath(), maxDepth));
                throw new Validator.ValidationShortCircuitException();
            }

            if (depth == path.length) {
                // Resize the path if needed by doubling its size.
                Object[] resized = new Object[Math.min((depth + 1) * 2, maxDepth)];
                System.arraycopy(path, 0, resized, 0, path.length);
                path = resized;
            }

            path[depth++] = pathSegment;
        }

        private void swapPath(Object pathSegment) {
            // Swap the current path segment, used in maps and lists that ensure a preliminary segment is
            // unconditionally pushed before validation and unconditionally popped after validation.
            path[depth - 1] = pathSegment;
        }

        void popPath() {
            depth--;
        }

        String createPath() {
            String errorPath;
            if (depth == 0) {
                errorPath = "/";
            } else {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < depth; i++) {
                    builder.append('/').append(path[i].toString());
                }
                errorPath = builder.toString();
            }

            return errorPath;
        }

        void addError(ValidationError error) {
            if (errors.size() == maxAllowedErrors) {
                throw new Validator.ValidationShortCircuitException();
            }
            errors.add(error);
        }

        @Override
        public <T> void writeStruct(SdkSchema schema, T structState, BiConsumer<T, ShapeSerializer> consumer) {
            // Track the current schema and count.
            var previousSchema = currentSchema;
            var previousCount = elementCount;
            currentSchema = schema;
            elementCount = 0; // note that we don't track the count of structure members.

            switch (schema.type()) {
                case STRUCTURE -> ValidatorOfStruct.validate(this, schema, structState, consumer);
                case UNION -> ValidatorOfUnion.validate(this, structState, consumer);
                default -> checkType(schema, ShapeType.STRUCTURE); // Schema / shape mis-match.
            }

            currentSchema = previousSchema;
            elementCount = previousCount;
        }

        @Override
        public <T> void writeList(SdkSchema schema, T state, BiConsumer<T, ShapeSerializer> consumer) {
            checkType(schema, ShapeType.LIST);

            // Track the current schema and count.
            var previousSchema = currentSchema;
            var previousCount = elementCount;
            currentSchema = schema;
            elementCount = 0;

            // Push a preliminary value of 0 even if there are no elements. Subsequent elements will swap this
            // path segment with the next index (e.g., 1, then 2, etc).
            pushPath(0);
            consumer.accept(state, listValidator);
            popPath();

            // Grab the count and reset the schema and count.
            var count = elementCount;
            currentSchema = previousSchema;
            elementCount = previousCount;

            // Ensure the list has an acceptable length.
            if (count < schema.minLengthConstraint) {
                addError(
                    new ValidationError.LengthValidationFailure(
                        createPath(),
                        count,
                        schema.minLengthConstraint,
                        schema.maxLengthConstraint
                    )
                );
            } else if (count > schema.maxLengthConstraint) {
                addError(
                    new ValidationError.LengthValidationFailure(
                        createPath(),
                        count,
                        schema.minLengthConstraint,
                        schema.maxLengthConstraint
                    )
                );
            }
        }

        @Override
        public <T> void writeMap(SdkSchema schema, T state, BiConsumer<T, MapSerializer> consumer) {
            checkType(schema, ShapeType.MAP);

            // Track the current schema and count.
            var previousSchema = currentSchema;
            var previousCount = elementCount;
            currentSchema = schema;
            elementCount = 0;

            // Push a preliminary map key of null. This key isn't actually used if the map is empty or if the map
            // has values. If empty, no errors are created and segment is ignored. If not empty, then this null
            // segment is swapped with the appropriate map key prior to nested validation.
            pushPath(null);
            consumer.accept(state, this);
            popPath();

            // Grab the count and reset the schema and count.
            var count = elementCount;
            currentSchema = previousSchema;
            elementCount = previousCount;

            // Ensure the map is properly sized.
            if (count < schema.minLengthConstraint) {
                addError(
                    new ValidationError.LengthValidationFailure(
                        createPath(),
                        count,
                        schema.minLengthConstraint,
                        schema.maxLengthConstraint
                    )
                );
            } else if (count > schema.maxLengthConstraint) {
                addError(
                    new ValidationError.LengthValidationFailure(
                        createPath(),
                        count,
                        schema.minLengthConstraint,
                        schema.maxLengthConstraint
                    )
                );
            }
        }

        @Override
        public void writeBoolean(SdkSchema schema, boolean value) {
            checkType(schema, ShapeType.BOOLEAN);
        }

        @Override
        public void writeByte(SdkSchema schema, byte value) {
            checkType(schema, ShapeType.BYTE);
            validateRange(schema, value, schema.minLongConstraint, schema.maxLongConstraint);
        }

        @Override
        public void writeShort(SdkSchema schema, short value) {
            checkType(schema, ShapeType.SHORT);
            validateRange(schema, value, schema.minLongConstraint, schema.maxLongConstraint);
        }

        @Override
        public void writeInteger(SdkSchema schema, int value) {
            // Validate range traits for normal integers, and validate intEnum for INT_ENUM values.
            switch (schema.type()) {
                case INTEGER -> validateRange(schema, value, schema.minLongConstraint, schema.maxLongConstraint);
                case INT_ENUM -> {
                    var values = schema.intEnumValues();
                    if (!values.isEmpty() && !values.contains(value)) {
                        addError(new ValidationError.IntEnumValidationFailure(createPath(), value, values));
                    }
                }
                default -> checkType(schema, ShapeType.INTEGER); // it's invalid.
            }
        }

        @Override
        public void writeLong(SdkSchema schema, long value) {
            checkType(schema, ShapeType.LONG);
            validateRange(schema, value, schema.minLongConstraint, schema.maxLongConstraint);
        }

        @Override
        public void writeFloat(SdkSchema schema, float value) {
            checkType(schema, ShapeType.FLOAT);
            validateRange(schema, value, schema.minDoubleConstraint, schema.maxDoubleConstraint);
        }

        @Override
        public void writeDouble(SdkSchema schema, double value) {
            checkType(schema, ShapeType.DOUBLE);
            validateRange(schema, value, schema.minDoubleConstraint, schema.maxDoubleConstraint);
        }

        @Override
        public void writeBigInteger(SdkSchema schema, BigInteger value) {
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
        public void writeBigDecimal(SdkSchema schema, BigDecimal value) {
            checkType(schema, ShapeType.BIG_DECIMAL);
            if (schema.minRangeConstraint != null && value.compareTo(schema.minRangeConstraint) < 0) {
                emitRangeError(schema, value);
            } else if (schema.maxRangeConstraint != null && value.compareTo(schema.maxRangeConstraint) > 0) {
                emitRangeError(schema, value);
            }
        }

        @Override
        public void writeString(SdkSchema schema, String value) {
            switch (schema.type()) {
                case STRING -> {
                    validateStringEnumValues(value, schema.stringEnumValues());
                    validateLength(
                        value.codePointCount(0, value.length()),
                        schema.minLengthConstraint,
                        schema.maxLengthConstraint
                    );
                    if (schema.patternConstraint != null) {
                        validatePattern(value, schema.patternConstraint);
                    }
                }
                case ENUM -> validateStringEnumValues(value, schema.stringEnumValues());
                default -> checkType(schema, ShapeType.STRING); // it's invalid, and calling this adds an error.
            }
        }

        private void validatePattern(String value, Pattern pattern) {
            try {
                // Note: using Matcher#find() here and not Matcher#match() because Smithy expects patterns to be rooted
                // with ^ and $ to get the same behavior as #match().
                if (!pattern.matcher(value).find()) {
                    addError(new ValidationError.PatternValidationFailure(createPath(), value, pattern));
                }
            } catch (StackOverflowError e) {
                throw new StackOverflowError(
                    String.format(
                        "Pattern '%s' is too expensive to evaluate against given input. Please "
                            + "refactor your pattern to be more performant",
                        pattern.pattern()
                    )
                );
            }
        }

        @Override
        public void writeBlob(SdkSchema schema, byte[] value) {
            checkType(schema, ShapeType.BLOB);
            validateLength(value.length, schema.minLengthConstraint, schema.maxLengthConstraint);
        }

        @Override
        public void writeTimestamp(SdkSchema schema, Instant value) {
            checkType(schema, ShapeType.TIMESTAMP);
        }

        @Override
        public void writeDocument(SdkSchema schema, Document document) {
            checkType(schema, ShapeType.DOCUMENT);
        }

        @Override
        public void writeNull(SdkSchema schema) {
            // This class only needs to validate null values when the current shape under validation is a list or map.
            // If it's a list or map, and it doesn't have the sparse trait, then null isn't allowed.
            // Note that union and structure member validation is handled in other classes (e.g., ValidatorOfUnion).
            if (currentSchema != null) {
                if (currentSchema.type() == ShapeType.MAP || currentSchema.type() == ShapeType.LIST) {
                    if (!currentSchema.hasTrait(SparseTrait.class)) {
                        addError(new ValidationError.SparseValidationFailure(createPath(), currentSchema.type()));
                    }
                }
            }
        }

        // MapSerializer implementation.

        @Override
        public <T> void writeEntry(
            SdkSchema keySchema,
            String key,
            T state,
            BiConsumer<T, ShapeSerializer> valueSerializer
        ) {
            elementCount++;
            swapPath(key);
            writeString(keySchema, key);
            valueSerializer.accept(state, this);
        }

        private void validateStringEnumValues(String value, Set<String> allowedValues) {
            if (!allowedValues.isEmpty() && !allowedValues.contains(value)) {
                addError(new ValidationError.EnumValidationFailure(createPath(), value, allowedValues));
            }
        }

        private void validateLength(long length, long min, long max) {
            if (length < min || length > max) {
                addError(new ValidationError.LengthValidationFailure(createPath(), length, min, max));
            }
        }

        private void validateRange(SdkSchema schema, long value, long min, long max) {
            if (value < min || value > max) {
                emitRangeError(schema, value);
            }
        }

        private void validateRange(SdkSchema schema, double value, double min, double max) {
            if (value < min || value > max) {
                emitRangeError(schema, value);
            }
        }

        private void emitRangeError(SdkSchema schema, Number value) {
            addError(
                new ValidationError.RangeValidationFailure(
                    createPath(),
                    value,
                    schema.minRangeConstraint,
                    schema.maxRangeConstraint
                )
            );
        }

        private void checkType(SdkSchema schema, ShapeType type) {
            if (schema.type() != type) {
                addError(new ValidationError.TypeValidationFailure(createPath(), schema.type(), type));
                // Stop any further validation if an incorrect type is given. This should only be encountered when data
                // is emitted from something manually and not from an actual modeled shape.
                throw new ValidationShortCircuitException();
            }
        }
    }
}
