/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.exceptions.InternalServerError;
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

    public abstract CompletableFuture<Void> serializeOutput(Job job, SerializableStruct output);

    public final CompletableFuture<Void> serializeError(Job job, Throwable error) {
        return serializeError(job, error instanceof ModeledApiException me ? me : new InternalServerError(error));
    }

    public final CompletableFuture<Void> serializeError(Job job, ModeledApiException error) {
        if (!job.operation().getApiOperation().errorSchemas().contains(error.schema())) {
            error = new InternalServerError(error);
        }
        return serializeOutput(job, error);
    }

}
