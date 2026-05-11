/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * A custom constraint that extends Validation API.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} and must be registered in
 * {@code META-INF/services/software.amazon.smithy.java.core.schema.CustomConstraint}.
 *
 * @see Validator
 * @see ValidationError.CustomValidationFailure
 */
public interface CustomConstraint {
    /**
     * Returns the set of shape types this constraint applies to.
     *
     * <p>Return an empty set to match all shape types (wildcard).
     *
     * @return the set of shape types this constraint validates, or an empty set for all types
     */
    EnumSet<ShapeType> appliesTo();

    /**
     * Validates the given value against this custom rule.
     *
     * @param schema the schema of the value being validated
     * @param value the value to validate
     * @param pathSupplier a supplier that lazily computes the path to the value (e.g., "/user/address/zipCode")
     * @return a list of validation errors, or an empty list if validation passes
     */
    List<ValidationError> validate(Schema schema, Object value, Supplier<String> pathSupplier);
}
