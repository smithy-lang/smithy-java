/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.core.schema.ValidationError;
import software.amazon.smithy.java.core.schema.Validator;
import software.amazon.smithy.java.framework.model.ValidationException;

final class ValidationHandler implements Handler {

    private final Validator validator = Validator.builder().build();

    @Override
    public CompletableFuture<Void> before(Job job) {
        var input = job.request().getDeserializedValue();
        var errors = validator.validate(input);
        if (errors.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.failedFuture(ValidationException.builder()
                .withoutStackTrace()
                .message(createValidationErrorMessage(errors))
                .build());
    }

    @Override
    public CompletableFuture<Void> after(Job job) {
        return CompletableFuture.completedFuture(null);
    }

    private String createValidationErrorMessage(List<ValidationError> errors) {
        StringBuilder builder = new StringBuilder();
        builder.append(errors.size())
                .append(" validation error(s) detected. ");
        for (var error : errors) {
            builder.append(error.message())
                    .append(" at ")
                    .append(error.path())
                    .append(";");
        }
        return builder.toString();
    }
}
