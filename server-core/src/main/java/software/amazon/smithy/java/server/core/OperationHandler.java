/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.core.schema.ModeledApiException;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.framework.model.InternalFailureException;
import software.amazon.smithy.java.server.Operation;

public class OperationHandler implements Handler {

    @Override
    public CompletableFuture<Void> before(Job job) {
        Operation operation = job.operation();
        SerializableStruct inputShape = job.request().getDeserializedValue();
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (operation.isAsync()) {
            CompletableFuture<? extends SerializableStruct> response =
                    (CompletableFuture<? extends SerializableStruct>) operation
                            .asyncFunction()
                            .apply(inputShape, null); //TODO add request context.
            response.whenComplete((result, error) -> {
                SerializableStruct output = result;
                if (error != null) {
                    ModeledApiException modeledError;
                    if (error instanceof ModeledApiException e) {
                        modeledError = e;
                    } else {
                        modeledError = InternalFailureException.builder().withCause(error).build();
                    }
                    output = modeledError;
                    job.setFailure(modeledError);
                }
                job.response().setValue(output);
                job.complete();
                future.complete(null);
            });
        } else {
            SerializableStruct response;
            try {
                response = (SerializableStruct) operation.function().apply(inputShape, null);
            } catch (ModeledApiException e) {
                job.setFailure(e);
                response = e;
            } catch (Exception e) {
                var modeledError = InternalFailureException.builder().withCause(e).build();
                job.setFailure(e);
                response = modeledError;
            }
            job.response().setValue(response);
            job.complete();
            future.complete(null);
        }
        return future;
    }

    @Override
    public CompletableFuture<Void> after(Job job) {
        return CompletableFuture.completedFuture(null);
    }
}
