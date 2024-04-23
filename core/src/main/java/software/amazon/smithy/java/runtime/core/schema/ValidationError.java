/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.Set;
import java.util.regex.Pattern;
import software.amazon.smithy.model.shapes.ShapeType;

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

    record UnionValidationFailure(String path, String message) implements ValidationError {}

    record TypeValidationFailure(String path, String message, ShapeType expected, ShapeType actual) implements
        ValidationError {
        public TypeValidationFailure(String path, ShapeType expected, ShapeType actual) {
            this(path, "Value must be " + expected + ", but found " + actual, expected, actual);
        }
    }

    record DepthValidationFailure(String path, String message, int nestingLevel) implements ValidationError {
        public DepthValidationFailure(String path, int nestingLevel) {
            this(path, "Value is too deeply nested", nestingLevel);
        }
    }

    record RequiredValidationFailure(String path, String message, String missingMember) implements ValidationError {
        public RequiredValidationFailure(String path, String missingMember) {
            this(path, "Value missing required member: " + missingMember, missingMember);
        }
    }

    record PatternValidationFailure(String path, String message, String value, Pattern pattern) implements
        ValidationError {
        public PatternValidationFailure(String path, String value, Pattern pattern) {
            this(path, "Value must satisfy regular expression pattern: " + pattern.pattern(), value, pattern);
        }
    }

    record EnumValidationFailure(String path, String message, String value, Set<String> allowed) implements
        ValidationError {
        public EnumValidationFailure(String path, String value, Set<String> allowed) {
            this(path, "Value must satisfy enum value set: " + allowed, value, allowed);
        }
    }

    record IntEnumValidationFailure(String path, String message, int value, Set<Integer> allowed) implements
        ValidationError {
        public IntEnumValidationFailure(String path, int value, Set<Integer> allowed) {
            this(path, "Value must satisfy intEnum value set: " + allowed, value, allowed);
        }
    }

    record SparseValidationFailure(String path, String message, ShapeType containerType) implements ValidationError {
        public SparseValidationFailure(String path, ShapeType containerType) {
            this(path, "Value is a " + containerType + " that does not allow null values", containerType);
        }
    }

    record RangeValidationFailure(String path, String message, Number value, Number min, Number max) implements
        ValidationError {
        public RangeValidationFailure(String path, Number value, Number min, Number max) {
            this(path, createMessage(min, max), value, min, max);
        }

        private static String createMessage(Number min, Number max) {
            if (min == null) {
                return "Value must be less than or equal to " + formatDecimal(max);
            } else if (max == null) {
                return "Value must be greater than or equal to " + formatDecimal(min);
            } else {
                return "Value must be between " + formatDecimal(min) + " and " + formatDecimal(max) + ", inclusive";
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

    record LengthValidationFailure(String path, String message, long length, long min, long max) implements
        ValidationError {
        public LengthValidationFailure(String path, long length, long min, long max) {
            this(path, createMessage(length, min, max), length, min, max);
        }

        private static String createMessage(long length, long min, long max) {
            var prefix = "Value with length " + length;
            if (min == Long.MIN_VALUE) {
                return prefix + " must have length less than or equal to " + max;
            } else if (max == Long.MAX_VALUE) {
                return prefix + " must have length greater than or equal to " + min;
            } else {
                return prefix + " must have length between " + min + " and " + max + ", inclusive";
            }
        }
    }
}
