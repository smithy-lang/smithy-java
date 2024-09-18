/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import software.amazon.smithy.java.runtime.core.schema.ApiOperation;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;

public final class Operation<I extends SerializableStruct, O extends SerializableStruct> {
    private final String name;
    private final boolean isAsync;
    private final BiFunction<I, RequestContext, O> operation;
    private final BiFunction<I, RequestContext, CompletableFuture<O>> asyncOperation;
    private final ApiOperation<I, O> sdkOperation;

    private Operation(
        String name,
        BiFunction<I, RequestContext, O> operation,
        BiFunction<I, RequestContext, CompletableFuture<O>> asyncOperation,
        ApiOperation<I, O> sdkOperation
    ) {
        if (operation != null && asyncOperation != null) {
            throw new IllegalArgumentException("At least one of operation and asyncOperation must be null");
        }
        this.name = name;
        this.isAsync = asyncOperation != null;
        if (isAsync) {
            this.operation = null;
            this.asyncOperation = Objects.requireNonNull(asyncOperation);
        } else {
            this.operation = Objects.requireNonNull(operation);
            this.asyncOperation = null;
        }
        this.sdkOperation = sdkOperation;
    }

    public static <I extends SerializableStruct, O extends SerializableStruct> Operation<I, O> of(
        String name,
        BiFunction<I, RequestContext, O> operation,
        ApiOperation<I, O> sdkOperation
    ) {
        return new Operation<>(name, operation, null, sdkOperation);
    }

    public static <I extends SerializableStruct, O extends SerializableStruct> Operation<I, O> ofAsync(
        String name,
        BiFunction<I, RequestContext, CompletableFuture<O>> operation,
        ApiOperation<I, O> sdkOperation
    ) {
        return new Operation<>(name, null, operation, sdkOperation);
    }

    public boolean isAsync() {
        return isAsync;
    }

    public BiFunction<I, RequestContext, O> function() {
        if (isAsync) {
            throw new IllegalStateException("Operation is async. asyncFunction should be invoked.");
        }
        return operation;
    }

    public BiFunction<I, RequestContext, CompletableFuture<O>> asyncFunction() {
        if (!isAsync) {
            throw new IllegalStateException("Operation is sync. function should be invoked.");
        }
        return asyncOperation;
    }

    public String name() {
        return name;
    }

    public ApiOperation<I, O> getApiOperation() {
        return sdkOperation;
    }
}
