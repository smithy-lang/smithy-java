/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

public sealed interface ObservableOrchestrator extends Orchestrator permits SingleThreadOrchestrator,
        OrchestratorGroup, DelegatingObservableOrchestrator {

    int inflightJobs();
}
