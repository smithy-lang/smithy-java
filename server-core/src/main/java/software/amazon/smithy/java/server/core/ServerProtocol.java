/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.core.schema.ModeledApiException;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.core.serde.SerializationException;
import software.amazon.smithy.java.framework.model.InternalFailureException;
import software.amazon.smithy.java.framework.model.MalformedRequestException;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.model.shapes.ShapeId;

public abstract class ServerProtocol {

    private final List<Service> services;

    protected ServerProtocol(List<Service> services) {
        this.services = services;
    }

    public abstract ShapeId getProtocolId();

    public abstract ServiceProtocolResolutionResult resolveOperation(
            ServiceProtocolResolutionRequest request,
            List<Service> candidates
    );

    public abstract CompletableFuture<Void> deserializeInput(Job job);

    public final CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output) {
        return serializeOutput(job, output, false);
    }

    public final CompletableFuture<Void> serializeError(Job job, Throwable error) {
        return serializeError(
                job,
                error instanceof ModeledApiException me ? me
                        : translate(error));
    }

    private static ModeledApiException translate(Throwable error) {
        if (error instanceof SerializationException se) {
            return MalformedRequestException.builder()
                    .withoutStackTrace()
                    .withCause(se)
                    .message(se.getMessage())
                    .build();
        }
        return InternalFailureException.builder().withCause(error).build();
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
