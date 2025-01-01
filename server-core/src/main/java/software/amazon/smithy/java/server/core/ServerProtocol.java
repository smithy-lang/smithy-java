/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.core.schema.ModeledApiException;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.schema.ValidationError;
import software.amazon.smithy.java.core.schema.Validator;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.core.serde.ShapeDeserializer;
import software.amazon.smithy.java.framework.model.InternalFailureException;
import software.amazon.smithy.java.framework.model.MalformedRequestException;
import software.amazon.smithy.java.framework.model.ValidationException;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;

public abstract class ServerProtocol {

    private final List<Service> services;
    private final Validator validator = Validator.builder().build();

    protected ServerProtocol(List<Service> services) {
        this.services = services;
    }

    public abstract ShapeId getProtocolId();

    public abstract ServiceProtocolResolutionResult resolveOperation(
            ServiceProtocolResolutionRequest request,
            List<Service> candidates
    );

    public CompletableFuture<Void> deserializeInput(Job job) {
        var inputBuilder = job.operation().getApiOperation().inputBuilder();

        return getDeserializer(job)
                .thenApply(shapeDeserializer -> {
                    try {
                        var errors = validator.deserializeAndValidate(inputBuilder, shapeDeserializer);
                        if (errors.isEmpty()) {
                            job.request().setDeserializedValue(inputBuilder.build());
                        } else {
                            job.setFailure(
                                    ValidationException.builder()
                                            .withoutStackTrace()
                                            .message(createValidationErrorMessage(errors))
                                            .build());
                        }
                        return null;
                    } catch (SerializationException e) {
                        job.setFailure(
                                MalformedRequestException.builder()
                                        .message("Malformed Request")
                                        .withCause(e)
                                        .build());
                        return null;
                    }
                });
    }

    private String createValidationErrorMessage(List<ValidationError> errors) {
        StringBuilder builder = new StringBuilder();
        builder.append(errors.size())
                .append(" validation error(s) detected.");
        for (var error : errors) {
            builder.append(error.message())
                    .append(" at ")
                    .append(error.path())
                    .append(";");
        }
        return builder.toString();
    }

    protected abstract CompletableFuture<ShapeDeserializer> getDeserializer(
            Job job
    );

    public final CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output) {
        return serializeOutput(job, output, false);
    }

    public final CompletableFuture<Void> serializeError(Job job, Throwable error) {
        return serializeError(
                job,
                error instanceof ModeledApiException me ? me
                        : InternalFailureException.builder().withCause(error).build());
    }

    protected abstract CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output, boolean isError);

    public final CompletableFuture<Void> serializeError(Job job, ModeledApiException error) {
        // Check both implicit errors and operation errors to see if modeled API exception is
        // defined as part of service interface. Otherwise, throw generic exception.
        if (!job.operation().getOwningService().typeRegistry().contains(error.schema().id())
                && !job.operation().getApiOperation().errorRegistry().contains(error.schema().id())) {
            error = InternalFailureException.builder().withCause(error).build();
        }
        return serializeOutput(job, error, true);
    }
}
