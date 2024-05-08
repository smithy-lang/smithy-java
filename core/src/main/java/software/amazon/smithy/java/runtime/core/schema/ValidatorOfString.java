/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.core.schema;

import java.util.List;
import java.util.regex.Pattern;

abstract sealed class ValidatorOfString permits ValidatorOfString.NoStringValidation,
    ValidatorOfString.EnumStringValidator,
    ValidatorOfString.LengthStringValidator,
    ValidatorOfString.PatternStringValidator,
    ValidatorOfString.CompositeStringValidator {

    abstract void apply(SdkSchema schema, String value, Validator.ShapeValidator validator);

    static ValidatorOfString of(List<ValidatorOfString> validators) {
        if (validators == null || validators.isEmpty()) {
            return NoStringValidation.INSTANCE;
        } else if (validators.size() == 1) {
            return validators.get(0);
        } else {
            return new CompositeStringValidator(validators);
        }
    }

    static final class NoStringValidation extends ValidatorOfString {
        static final NoStringValidation INSTANCE = new NoStringValidation();

        @Override
        public void apply(SdkSchema schema, String value, Validator.ShapeValidator validator) {}
    }

    static final class CompositeStringValidator extends ValidatorOfString {
        private final List<ValidatorOfString> validators;

        CompositeStringValidator(List<ValidatorOfString> validators) {
            this.validators = validators;
        }

        @Override
        public void apply(SdkSchema schema, String value, Validator.ShapeValidator validator) {
            for (var v : validators) {
                v.apply(schema, value, validator);
            }
        }
    }

    static final class LengthStringValidator extends ValidatorOfString {

        private final long min;
        private final long max;

        LengthStringValidator(long min, long max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public void apply(SdkSchema schema, String value, Validator.ShapeValidator validator) {
            var length = value.codePointCount(0, value.length());
            if (length < min || length > max) {
                validator.addError(new ValidationError.LengthValidationFailure(validator.createPath(), length, schema));
            }
        }
    }

    static final class PatternStringValidator extends ValidatorOfString {

        private final Pattern pattern;

        PatternStringValidator(Pattern pattern) {
            this.pattern = pattern;
        }

        @Override
        public void apply(SdkSchema schema, String value, Validator.ShapeValidator validator) {
            try {
                // Note: using Matcher#find() here and not Matcher#match() because Smithy expects patterns to be rooted
                // with ^ and $ to get the same behavior as #match().
                if (!pattern.matcher(value).find()) {
                    validator.addError(
                        new ValidationError.PatternValidationFailure(validator.createPath(), value, schema)
                    );
                }
            } catch (StackOverflowError e) {
                throw new StackOverflowError(
                    String.format(
                        "Pattern '%s' is too expensive to evaluate against given input. Please "
                            + "refactor your pattern to be more performant",
                        pattern
                    )
                );
            }
        }
    }

    static final class EnumStringValidator extends ValidatorOfString {
        static final EnumStringValidator INSTANCE = new EnumStringValidator();

        @Override
        public void apply(SdkSchema schema, String value, Validator.ShapeValidator validator) {
            if (!schema.stringEnumValues().contains(value)) {
                validator.addError(new ValidationError.EnumValidationFailure(validator.createPath(), value, schema));
            }
        }
    }
}
