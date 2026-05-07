/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.List;

/**
 * A custom constraint that extends Validation API.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} and must be registered in
 * {@code META-INF/services/software.amazon.smithy.java.core.schema.CustomConstraint}.
 *
 * @see Validator
 * @see Validator.CustomConstraintProvider
 * @see ValidationError.CustomValidationFailure
 */
public interface CustomConstraint {
    /**
     * Determines whether this rule applies to the given schema.
     *
     * @param schema the schema to check
     * @return {@code true} if this rule should validate values of this schema
     */
    boolean appliesTo(Schema schema);

    /**
     * Validates the given value against this custom rule.
     *
     * @param schema the schema of the value being validated
     * @param value the value to validate
     * @param path the path to the value (e.g., "/user/address/zipCode")
     * @return a list of validation errors, or an empty list if validation passes
     */
    List<ValidationError> validate(Schema schema, Object value, String path);
}
