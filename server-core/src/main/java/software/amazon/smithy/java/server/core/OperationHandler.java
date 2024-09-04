/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.core.schema.ModeledApiException;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.java.server.exceptions.InternalServerError;

public class OperationHandler implements Handler {

    @Override
    public CompletableFuture<Void> before(Job job) {
        Operation operation = job.operation();
        SerializableStruct inputShape = job.request().getDeserializedValue();
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (operation.isAsync()) {
            CompletableFuture<? extends SerializableStruct> response = (CompletableFuture<? extends SerializableStruct>) operation
                .asyncFunction()
                .apply(inputShape, null); //TODO add request context.
            response.whenComplete((result, error) -> {
                SerializableStruct output = result;
                if (error != null) {
                    if (error instanceof ModeledApiException e) {
                        output = e;
                    } else {
                        output = new InternalServerError(error);
                    }
                }
                job.response().setValue(output);
                job.complete();
                future.complete(null);
            });
        } else {
            SerializableStruct response = (SerializableStruct) operation.function().apply(inputShape, null);
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
