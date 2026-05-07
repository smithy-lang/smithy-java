/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.core.schema;

import java.util.List;
import software.amazon.smithy.model.shapes.ShapeType;

public final class TestCustomConstraints {

    private TestCustomConstraints() {}

    public static class AlwaysFailsConstraint implements CustomConstraint {
        @Override
        public boolean appliesTo(Schema schema) {
            return schema.id().getNamespace().contains("CustomTest");
        }

        @Override
        public List<ValidationError> validate(Schema schema, Object value, String path) {
            return List.of(new ValidationError.CustomValidationFailure(
                    path,
                    schema,
                    "Custom constraint failed"));
        }
    }

    public static class StringOnlyConstraint implements CustomConstraint {
        @Override
        public boolean appliesTo(Schema schema) {
            return schema.type() == ShapeType.STRING;
        }

        @Override
        public List<ValidationError> validate(Schema schema, Object value, String path) {
            return List.of(new ValidationError.CustomValidationFailure(
                    path,
                    schema,
                    "String-only constraint violated"));
        }
    }
}
