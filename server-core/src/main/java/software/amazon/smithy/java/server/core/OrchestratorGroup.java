/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.server.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class OrchestratorGroup implements ObservableOrchestrator {

    private final List<ObservableOrchestrator> orchestrators;
    private final Strategy strategy;

    public OrchestratorGroup(
        int numberOfOrchestrators,
        Supplier<ObservableOrchestrator> orchestratorSupplier,
        Strategy strategy
    ) {
        this.strategy = strategy;
        List<ObservableOrchestrator> orchestrators = new ArrayList<>(numberOfOrchestrators);
        for (int i = 0; i < numberOfOrchestrators; i++) {
            orchestrators.add(orchestratorSupplier.get());
        }
        this.orchestrators = Collections.unmodifiableList(orchestrators);
    }

    public Orchestrator next() {
        return strategy.select(orchestrators);
    }

    @Override
    public CompletableFuture<Void> enqueue(Job job) {
        return strategy.select(orchestrators).enqueue(job);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.allOf(
            orchestrators.stream().map(Orchestrator::shutdown).toArray(CompletableFuture[]::new)
        );
    }

    @Override
    public int inflightJobs() {
        return orchestrators.stream().map(ObservableOrchestrator::inflightJobs).reduce(0, Integer::sum);
    }

    public sealed interface Strategy {

        static Strategy roundRobin() {
            return new RoundRobinStrategy();
        }

        static Strategy leastLoaded() {
            return new LeastLoadedStrategy();
        }

        ObservableOrchestrator select(List<ObservableOrchestrator> orchestrators);
    }

    private static final class RoundRobinStrategy implements Strategy {
        private final AtomicInteger idx = new AtomicInteger();

        @Override
        public ObservableOrchestrator select(List<ObservableOrchestrator> orchestrators) {
            return orchestrators.get(idx.getAndIncrement() % orchestrators.size());
        }
    }

    private static final class LeastLoadedStrategy implements Strategy {

        @Override
        public ObservableOrchestrator select(List<ObservableOrchestrator> orchestrators) {
            int minLoad = Integer.MAX_VALUE;
            ObservableOrchestrator selected = null;
            for (ObservableOrchestrator orchestrator : orchestrators) {
                int load = orchestrator.inflightJobs();
                if (load < minLoad) {
                    selected = orchestrator;
                    minLoad = load;
                }
            }
            return selected;
        }
    }
}
