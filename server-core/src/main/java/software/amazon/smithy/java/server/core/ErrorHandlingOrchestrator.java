/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.concurrent.CompletableFuture;

public final class ErrorHandlingOrchestrator extends DelegatingObservableOrchestrator {

    public ErrorHandlingOrchestrator(ObservableOrchestrator delegate) {
        super(delegate);
    }

    @Override
    public CompletableFuture<Void> enqueue(Job job) {
        return delegate.enqueue(job).exceptionallyCompose(t -> {
            job.setFailure(t);
            return job.chosenProtocol().serializeError(job, t);
        });
    }
}
