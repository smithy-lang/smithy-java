/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;
import software.amazon.smithy.model.shapes.ShapeType;

public final class TestCustomConstraints {

    private TestCustomConstraints() {}

    public static class AlwaysFailsConstraint implements CustomConstraint {
        @Override
        public EnumSet<ShapeType> appliesTo() {
            // Empty set means wildcard - applies to all types
            return EnumSet.noneOf(ShapeType.class);
        }

        @Override
        public List<ValidationError> validate(Schema schema, Object value, Supplier<String> pathSupplier) {
            // Filter by namespace in the validate method
            if (!schema.id().getNamespace().contains("CustomTest")) {
                return List.of();
            }
            return List.of(new ValidationError.CustomValidationFailure(
                    pathSupplier.get(),
                    schema,
                    "Custom constraint failed"));
        }
    }

    public static class StringOnlyConstraint implements CustomConstraint {
        @Override
        public EnumSet<ShapeType> appliesTo() {
            return EnumSet.of(ShapeType.STRING);
        }

        @Override
        public List<ValidationError> validate(Schema schema, Object value, Supplier<String> pathSupplier) {
            // Filter by namespace in the validate method
            if (!schema.id().getNamespace().contains("CustomTest")) {
                return List.of();
            }
            return List.of(new ValidationError.CustomValidationFailure(
                    pathSupplier.get(),
                    schema,
                    "String-only constraint violated"));
        }
    }

    public static class ListOnlyConstraint implements CustomConstraint {
        @Override
        public EnumSet<ShapeType> appliesTo() {
            return EnumSet.of(ShapeType.LIST);
        }

        @Override
        public List<ValidationError> validate(Schema schema, Object value, Supplier<String> pathSupplier) {
            // Filter by namespace in the validate method
            if (!schema.id().getNamespace().contains("CustomTest")) {
                return List.of();
            }
            return List.of(new ValidationError.CustomValidationFailure(
                    pathSupplier.get(),
                    schema,
                    "List-only constraint violated"));
        }
    }

    public static class StructOnlyConstraint implements CustomConstraint {

        @Override
        public EnumSet<ShapeType> appliesTo() {
            return EnumSet.of(ShapeType.STRUCTURE);
        }

        @Override
        public List<ValidationError> validate(Schema schema, Object value, Supplier<String> pathSupplier) {
            // Filter by namespace in the validate method
            if (!schema.id().getNamespace().contains("CustomTest")) {
                return List.of();
            }
            return List.of(new ValidationError.CustomValidationFailure(
                    pathSupplier.get(),
                    schema,
                    "Struct-only constraint violated"));
        }
    }

    public static class UnionOnlyConstraint implements CustomConstraint {

        @Override
        public EnumSet<ShapeType> appliesTo() {
            return EnumSet.of(ShapeType.UNION);
        }

        @Override
        public List<ValidationError> validate(Schema schema, Object value, Supplier<String> pathSupplier) {
            // Filter by namespace in the validate method
            if (!schema.id().getNamespace().contains("CustomTest")) {
                return List.of();
            }
            return List.of(new ValidationError.CustomValidationFailure(
                    pathSupplier.get(),
                    schema,
                    "Union-only constraint violated"));
        }
    }
}
