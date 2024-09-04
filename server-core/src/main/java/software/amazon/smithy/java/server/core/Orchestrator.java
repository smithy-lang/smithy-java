/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.concurrent.CompletableFuture;

public sealed interface Orchestrator permits ObservableOrchestrator {

    CompletableFuture<Void> enqueue(Job job);

    CompletableFuture<Void> shutdown();
}
