/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.model.traits.PatternTrait;

/**
 * A validation error.
 */
public interface ValidationError {

    /**
     * Path pointing to where the error occurred.
     *
     * @return path to the error.
     */
    String path();

    /**
     * Default error message for the error.
     *
     * @return error message.
     */
    String message();

    record DepthValidationFailure(String path, String message, int nestingLevel) implements ValidationError {
        public DepthValidationFailure(String path, int nestingLevel) {
            this(path, "Value is too deeply nested", nestingLevel);
        }
    }

    record UnionValidationFailure(String path, String message, SdkSchema schema) implements ValidationError {}

    record TypeValidationFailure(String path, String message, ShapeType actual, SdkSchema schema) implements
        ValidationError {
        public TypeValidationFailure(String path, ShapeType actual, SdkSchema schema) {
            this(path, "Value must be " + schema.type() + ", but found " + actual, actual, schema);
        }
    }

    record RequiredValidationFailure(String path, String message, String missingMember, SdkSchema schema) implements
        ValidationError {
        public RequiredValidationFailure(String path, String missingMember, SdkSchema schema) {
            this(path, "Value missing required member: " + missingMember, missingMember, schema);
        }
    }

    record PatternValidationFailure(String path, String message, String value, SdkSchema schema) implements
        ValidationError {
        public PatternValidationFailure(String path, String value, SdkSchema schema) {
            this(
                path,
                "Value must satisfy regular expression pattern: "
                    + schema.expectTrait(PatternTrait.class).getPattern(),
                value,
                schema
            );
        }
    }

    record EnumValidationFailure(String path, String message, String value, SdkSchema schema) implements
        ValidationError {
        public EnumValidationFailure(String path, String value, SdkSchema schema) {
            this(path, "Value is not an allowed enum string", value, schema);
        }
    }

    record IntEnumValidationFailure(String path, String message, int value, SdkSchema schema) implements
        ValidationError {
        public IntEnumValidationFailure(String path, int value, SdkSchema schema) {
            this(path, "Value is not an allowed integer enum number", value, schema);
        }
    }

    record SparseValidationFailure(String path, String message, SdkSchema schema) implements ValidationError {
        public SparseValidationFailure(String path, SdkSchema schema) {
            this(path, "Value is in a " + schema.type() + " that does not allow null values", schema);
        }
    }

    record RangeValidationFailure(String path, String message, Number value, SdkSchema schema) implements
        ValidationError {
        public RangeValidationFailure(String path, Number value, SdkSchema schema) {
            this(path, createMessage(schema), value, schema);
        }

        private static String createMessage(SdkSchema schema) {
            if (schema.minRangeConstraint == null) {
                return "Value must be less than or equal to " + formatDecimal(schema.maxRangeConstraint);
            } else if (schema.maxRangeConstraint == null) {
                return "Value must be greater than or equal to " + formatDecimal(schema.minRangeConstraint);
            } else {
                return "Value must be between " + formatDecimal(schema.minRangeConstraint) + " and " + formatDecimal(
                    schema.maxRangeConstraint
                ) + ", inclusive";
            }
        }

        private static String formatDecimal(Number value) {
            String result = value.toString();
            if (result.endsWith(".0")) {
                return result.substring(0, result.length() - 2);
            } else {
                return result;
            }
        }
    }

    record LengthValidationFailure(String path, String message, long length, SdkSchema schema) implements
        ValidationError {
        public LengthValidationFailure(String path, long length, SdkSchema schema) {
            this(path, createMessage(length, schema), length, schema);
        }

        private static String createMessage(long length, SdkSchema schema) {
            var prefix = "Value with length " + length;
            if (schema.minLengthConstraint == Long.MIN_VALUE) {
                return prefix + " must have length less than or equal to " + schema.maxLengthConstraint;
            } else if (schema.maxLengthConstraint == Long.MAX_VALUE) {
                return prefix + " must have length greater than or equal to " + schema.minLengthConstraint;
            } else {
                return prefix + " must have length between " + schema.minLengthConstraint
                    + " and " + schema.maxLengthConstraint + ", inclusive";
            }
        }
    }
}
