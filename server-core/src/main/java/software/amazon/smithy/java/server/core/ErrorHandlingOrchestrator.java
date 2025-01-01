/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import software.amazon.smithy.java.logging.InternalLogger;

public final class ErrorHandlingOrchestrator extends DelegatingObservableOrchestrator {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(ErrorHandlingOrchestrator.class);

    public ErrorHandlingOrchestrator(ObservableOrchestrator delegate) {
        super(delegate);
    }

    @Override
    public CompletableFuture<Void> enqueue(Job job) {
        return delegate.enqueue(job).exceptionallyCompose(t -> {
            var failure = unwrap(t);
            LOGGER.error("Failure while orchestration", failure);
            job.setFailure(failure);
            return job.chosenProtocol().serializeError(job, failure);
        });
    }

    private static Throwable unwrap(Throwable throwable) {
        while (throwable instanceof CompletionException) {
            throwable = throwable.getCause();
        }
        return throwable;
    }
}
