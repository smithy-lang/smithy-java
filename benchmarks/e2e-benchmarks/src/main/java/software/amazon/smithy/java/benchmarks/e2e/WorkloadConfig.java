/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import software.amazon.smithy.model.node.BooleanNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * In-memory representation of a workload JSON file.
 */
final class WorkloadConfig {
    final String name;
    final String service; // "dynamodb" or "s3"
    final String action; // "putitem" / "getitem" / "upload" / "download"
    ObjectNode actionConfig;
    final int batchActions;
    final boolean sequential;
    final int warmupBatches;
    final int measurementBatches;
    final boolean collectMetrics;
    final int metricsIntervalMs;

    private WorkloadConfig(ObjectNode root) {
        this.name = root.expectStringMember("name").getValue();
        this.service = root.expectStringMember("service").getValue();
        this.action = root.expectStringMember("action").getValue();
        this.actionConfig = root.expectObjectMember("actionConfig");

        var batch = root.expectObjectMember("batch");
        // Run length is configurable via system properties so a benchmark harness can extend
        // warmup/measurement (e.g. to let the JIT fully warm up and dilute compiler noise in a
        // profile) without editing the committed workload JSON. Each falls back to the JSON value.
        this.batchActions = Integer.getInteger("e2e.batch.actions",
                batch.expectNumberMember("numberOfActions").getValue().intValue());
        this.sequential = batch.expectBooleanMember("sequentialExecution").getValue();

        this.warmupBatches = Integer.getInteger("e2e.warmup.batches",
                root.expectObjectMember("warmup")
                        .expectNumberMember("batches")
                        .getValue()
                        .intValue());

        var measurement = root.expectObjectMember("measurement");
        this.measurementBatches = Integer.getInteger("e2e.measurement.batches",
                measurement.expectNumberMember("batches").getValue().intValue());
        this.collectMetrics = measurement.getBooleanMember("collectMetrics")
                .map(BooleanNode::getValue)
                .orElse(false);
        this.metricsIntervalMs = measurement.getNumberMember("metricsInterval")
                .map(n -> n.getValue().intValue())
                .orElse(100);
    }

    static WorkloadConfig load(String path) throws IOException {
        var bytes = Files.readAllBytes(Paths.get(path));
        var node = Node.parse(new String(bytes)).expectObjectNode();
        return new WorkloadConfig(node);
    }

    /**
     * Replace string members on {@code actionConfig} with the given values. Used to wire CLI
     * overrides like {@code --bucket}, {@code --table}, {@code --region} into the workload at
     * runtime so the JSON files don't have to be edited per environment.
     */
    void overrideActionConfig(Map<String, String> overrides) {
        var builder = actionConfig.toBuilder();
        for (var entry : overrides.entrySet()) {
            builder.withMember(entry.getKey(), entry.getValue());
        }
        this.actionConfig = builder.build();
    }

    String stringConfig(String name) {
        return actionConfig.expectStringMember(name).getValue();
    }

    int intConfig(String name) {
        return actionConfig.expectNumberMember(name).getValue().intValue();
    }
}
