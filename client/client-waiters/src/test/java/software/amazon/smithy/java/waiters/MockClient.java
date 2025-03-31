/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.waiters;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.core.error.ModeledException;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.waiters.models.GetFoosInput;
import software.amazon.smithy.java.waiters.models.GetFoosOutput;

final class MockClient {
    private final Executor executor = CompletableFuture.delayedExecutor(3, TimeUnit.MILLISECONDS);
    private final String expectedId;
    private final Iterator<? extends SerializableStruct> iterator;

    MockClient(String expectedId, List<? extends SerializableStruct> outputs) {
        this.expectedId = expectedId;
        this.iterator = outputs.iterator();
    }

    public GetFoosOutput getFoosSync(GetFoosInput in, RequestOverrideConfig override) {
        return getFoosAsync(in, override).join();
    }

    public CompletableFuture<GetFoosOutput> getFoosAsync(GetFoosInput in, RequestOverrideConfig override) {
        if (!Objects.equals(expectedId, in.id())) {
            throw new IllegalArgumentException("ID: " + in.id() + " does not match expected " + expectedId);
        } else if (!iterator.hasNext()) {
            throw new IllegalArgumentException("No more requests expected but got: " + in);
        }
        var next = iterator.next();
        if (next instanceof GetFoosOutput output) {
            return CompletableFuture.supplyAsync(() -> output, executor);
        } else if (next instanceof ModeledException exc) {
            // Throw exception
            throw exc;
        }

        throw new IllegalArgumentException("Expected an output shape or modeled exception. Found: " + next);
    }
}
