/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public abstract sealed class DelegatingObservableOrchestrator implements ObservableOrchestrator permits
    ErrorHandlingOrchestrator {

    protected final ObservableOrchestrator delegate;

    protected DelegatingObservableOrchestrator(ObservableOrchestrator delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public int inflightJobs() {
        return delegate.inflightJobs();
    }

    @Override
    public CompletableFuture<Void> enqueue(Job job) {
        return delegate.enqueue(job);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return delegate.shutdown();
    }
}
