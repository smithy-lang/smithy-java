/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.java.server.ServiceMatcher;

public final class DefaultOrchestrator implements Orchestrator {

    public DefaultOrchestrator(
        List<Service> services,
        ServiceMatcher serviceMatcher
    ) {

    }

    @Override
    public CompletableFuture<Void> enqueue(Job job) {
        return null;
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return null;
    }
}
