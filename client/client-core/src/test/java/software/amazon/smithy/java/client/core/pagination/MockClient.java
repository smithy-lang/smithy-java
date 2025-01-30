/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.pagination;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.pagination.models.GetFoosInput;
import software.amazon.smithy.java.client.core.pagination.models.GetFoosOutput;
import software.amazon.smithy.java.client.core.pagination.models.ResultWrapper;

final class MockClient {
    private static final List<String> tokens = List.of("first", "second", "third", "final");
    private final Executor executor = CompletableFuture.delayedExecutor(3, TimeUnit.MILLISECONDS);
    private final Iterator<String> tokenIterator = tokens.iterator();
    private String nextToken = null;
    private boolean completed = false;

    public GetFoosOutput getFoosSync(GetFoosInput in, RequestOverrideConfig override) {
        return getFoosAsync(in, override).join();
    }

    public CompletableFuture<GetFoosOutput> getFoosAsync(GetFoosInput in, RequestOverrideConfig override) {
        if (!Objects.equals(nextToken, in.nextToken())) {
            throw new IllegalArgumentException(
                    "Next token " + nextToken + " does not match expected " + in.nextToken());
        } else if (completed) {
            throw new IllegalArgumentException("No more requests expected but got: " + in);
        }

        List<String> foos = new ArrayList<>();
        for (int idx = 0; idx < in.maxResults(); idx++) {
            foos.add("foo" + idx);
        }
        completed = !tokenIterator.hasNext();
        nextToken = tokenIterator.hasNext() ? tokenIterator.next() : null;
        var output = new GetFoosOutput(new ResultWrapper(nextToken, foos));
        return CompletableFuture.supplyAsync(() -> output, executor);
    }
}
