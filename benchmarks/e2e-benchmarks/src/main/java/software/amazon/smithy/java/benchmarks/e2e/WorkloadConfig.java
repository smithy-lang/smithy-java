/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * In-memory representation of a workload JSON file.
 *
 * <p>Only the v1 fields used by smithy-java's runner are exposed. Unknown
 * fields are ignored so that newer workload files do not break older runners.
 */
final class WorkloadConfig {
    final String name;
    final String service; // "dynamodb" or "s3"
    final String action; // "putitem" / "getitem" / "upload" / "download"
    final ObjectNode actionConfig;
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
        this.batchActions = batch.expectNumberMember("numberOfActions").getValue().intValue();
        this.sequential = batch.expectBooleanMember("sequentialExecution").getValue();

        this.warmupBatches = root.expectObjectMember("warmup")
                .expectNumberMember("batches")
                .getValue()
                .intValue();

        var measurement = root.expectObjectMember("measurement");
        this.measurementBatches = measurement.expectNumberMember("batches").getValue().intValue();
        this.collectMetrics = measurement.getBooleanMember("collectMetrics")
                .map(b -> b.getValue())
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

    String stringConfig(String name) {
        return actionConfig.expectStringMember(name).getValue();
    }

    int intConfig(String name) {
        return actionConfig.expectNumberMember(name).getValue().intValue();
    }
}
