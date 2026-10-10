/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import software.amazon.smithy.java.benchmarks.e2e.CpuTimeRunner.Measurement;
import software.amazon.smithy.java.benchmarks.e2e.CpuTimeRunner.Settings;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/** Builds and writes per-run benchmark results. */
final class RunReport {

    static final String SCHEMA = "smithy-java/e2e-ops-cpusec/2";
    static final String METRIC = "ops_per_cpu_second";
    static final String MEASUREMENT = "com.sun.management.OperatingSystemMXBean.getProcessCpuTime";

    private RunReport() {}

    static ObjectNode benchmark(BenchmarkCase benchmarkCase, Measurement m) {
        var warmup = Node.objectNodeBuilder()
                .withMember("iterations", m.warmup().iterations())
                .withMember("hit_cap", m.warmup().hitCap())
                .withMember("final_chunk_jit_share", round(m.warmup().finalChunkJitShare(), 5))
                .build();
        return Node.objectNodeBuilder()
                .withMember("id", benchmarkCase.id())
                .withMember("protocol", benchmarkCase.protocol().summaryName())
                .withMember("operation", benchmarkCase.operationName())
                .withMember("source", benchmarkCase.source().label())
                .withMember("workload_fingerprint", benchmarkCase.fingerprint())
                .withMember("iterations", m.iterations())
                .withMember("ops_per_cpu_sec", round(m.opsPerCpuSecond(), 2))
                .withMember("ops_per_wall_sec", round(m.opsPerWallSecond(), 2))
                .withMember("cpu_seconds", round(m.processCpuNanos() / 1e9, 6))
                .withMember("wall_seconds", round(m.wallNanos() / 1e9, 6))
                .withMember("cpu_wall_ratio", round(m.cpuWallRatio(), 4))
                .withMember("warmup", warmup)
                .withMember("jit_millis", m.jitMillis())
                .withMember("jit_share_of_wall", round(m.jitShareOfWall(), 5))
                .withMember("under_warmed", m.underWarmed())
                .withMember("gc_count", m.gcCount())
                .withMember("gc_millis", m.gcMillis())
                .withMember("response_body_bytes", benchmarkCase.response().bodyLength())
                .build();
    }

    static ObjectNode metadata(
            BenchmarkOptions options,
            Settings settings,
            ObjectNode environment,
            Instant started,
            Instant finished,
            boolean backgroundJitDisabled,
            Target target,
            int benchmarkCount
    ) {
        var builder = Node.objectNodeBuilder()
                .withMember("lang", "Java")
                .withMember("sdk", "smithy-java")
                .withMember("sdk_version", environment.expectStringMember("sdk_version").getValue())
                .withMember("client", "sync")
                .withMember("metric", METRIC)
                .withMember("measurement", MEASUREMENT)
                .withMember("stop_condition", settings.stopCondition())
                .withMember("min_iterations", settings.minIterations())
                .withMember("min_cpu_seconds", settings.minCpuSeconds())
                .withMember("min_measure_cpu_seconds", settings.minMeasureCpuSeconds())
                .withMember("check_interval", settings.checkInterval())
                .withMember("warmup_policy", settings.warmup().policy())
                .withMember("warmup_policy_detail", settings.warmup().detail())
                .withMember("background_jit_compilation_disabled", backgroundJitDisabled)
                .withMember("mode", options.mode().label())
                .withMember("transport", target.description())
                .withMember("http_mock",
                        options.mode() == Mode.STUB
                                ? MockHttpTransport.DESCRIPTION
                                : "none: real HTTP client over TLS to a loopback fixture server in another process")
                .withMember("endpoint", target.endpoint())
                .withMember("region", BenchmarkProtocol.REGION)
                .withMember("instance", environment.expectStringMember("instance").getValue())
                .withMember("commit", environment.expectStringMember("commit").getValue())
                .withMember("branch", environment.expectStringMember("branch").getValue());
        if (options.notes() != null) {
            builder.withMember("notes", options.notes());
        }
        return builder.withMember("run_started_utc", started.toString())
                .withMember("run_finished_utc", finished.toString())
                .withMember("run_duration_seconds", round(Duration.between(started, finished).toMillis() / 1000.0, 3))
                .withMember("benchmark_count", benchmarkCount)
                .withMember("environment", environment)
                .build();
    }

    static ObjectNode report(ObjectNode metadata, List<ObjectNode> benchmarks) {
        return Node.objectNodeBuilder()
                .withMember("schema", SCHEMA)
                .withMember("metadata", metadata)
                .withMember("benchmarks", ArrayNode.fromNodes(benchmarks.toArray(new Node[0])))
                .withMember("summary", summary(benchmarks))
                .build();
    }

    static ObjectNode summary(List<ObjectNode> benchmarks) {
        var protocols = Node.objectNodeBuilder();
        List<Double> all = new ArrayList<>();
        for (var protocol : BenchmarkProtocol.values()) {
            List<Double> values = new ArrayList<>();
            for (var benchmark : benchmarks) {
                if (protocol.summaryName().equals(benchmark.expectStringMember("protocol").getValue())) {
                    values.add(number(benchmark, "ops_per_cpu_sec"));
                }
            }
            if (!values.isEmpty()) {
                all.addAll(values);
                protocols.withMember(protocol.summaryName(),
                        Node.objectNodeBuilder()
                                .withMember("ops_per_cpu_sec", round(GeometricMean.of(values), 2))
                                .withMember("benchmark_count", values.size())
                                .build());
            }
        }

        double ratioMin = Double.MAX_VALUE;
        double ratioMax = 0;
        double ratioSum = 0;
        long warmupMin = Long.MAX_VALUE;
        long warmupMax = 0;
        int hitCap = 0;
        int underWarmed = 0;
        for (var benchmark : benchmarks) {
            double ratio = number(benchmark, "cpu_wall_ratio");
            ratioMin = Math.min(ratioMin, ratio);
            ratioMax = Math.max(ratioMax, ratio);
            ratioSum += ratio;
            var warmup = benchmark.expectObjectMember("warmup");
            long warmupIterations = warmup.expectNumberMember("iterations").getValue().longValue();
            warmupMin = Math.min(warmupMin, warmupIterations);
            warmupMax = Math.max(warmupMax, warmupIterations);
            if (warmup.expectBooleanMember("hit_cap").getValue()) {
                hitCap++;
            }
            if (benchmark.expectBooleanMember("under_warmed").getValue()) {
                underWarmed++;
            }
        }
        int count = benchmarks.size();
        var overall = Node.objectNodeBuilder()
                .withMember("ops_per_cpu_sec", count == 0 ? 0 : round(GeometricMean.of(all), 2))
                .withMember("benchmark_count", count)
                .withMember("aggregation", "geometric_mean")
                .build();
        var cpuWall = Node.objectNodeBuilder()
                .withMember("min", count == 0 ? 0 : round(ratioMin, 4))
                .withMember("max", round(ratioMax, 4))
                .withMember("mean", count == 0 ? 0 : round(ratioSum / count, 4))
                .build();
        var warmupSummary = Node.objectNodeBuilder()
                .withMember("min_iterations", count == 0 ? 0 : warmupMin)
                .withMember("max_iterations", warmupMax)
                .withMember("benchmarks_that_hit_the_cap", hitCap)
                .build();
        return Node.objectNodeBuilder()
                .withMember("protocols", protocols.build())
                .withMember("overall", overall)
                .withMember("cpu_wall_ratio", cpuWall)
                .withMember("warmup", warmupSummary)
                .withMember("under_warmed_benchmarks", underWarmed)
                .build();
    }

    static void write(ObjectNode report, Path path) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, Node.prettyPrintJson(report) + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + path, e);
        }
    }

    static void printSummary(ObjectNode report, Path output, PrintStream out) {
        var metadata = report.expectObjectMember("metadata");
        var summary = report.expectObjectMember("summary");
        var environment = metadata.expectObjectMember("environment");
        NumberFormat integers = NumberFormat.getIntegerInstance(Locale.US);

        out.println();
        out.printf(Locale.ROOT,
                "smithy-java e2e ops/CPU-sec: %d benchmarks, mode %s, on %s, %s %s%s%n",
                metadata.expectNumberMember("benchmark_count").getValue().intValue(),
                metadata.expectStringMember("mode").getValue(),
                metadata.expectStringMember("instance").getValue(),
                environment.expectObjectMember("java").expectStringMember("vm_name").getValue(),
                environment.expectObjectMember("java").expectStringMember("version").getValue(),
                metadata.expectBooleanMember("background_jit_compilation_disabled").getValue()
                        ? " (-Xbatch)"
                        : " (WITHOUT -Xbatch)");
        out.printf(Locale.ROOT,
                "smithy-java %s, commit %s, stop: %s%n",
                metadata.expectStringMember("sdk_version").getValue(),
                metadata.expectStringMember("commit").getValue(),
                metadata.expectStringMember("stop_condition").getValue());
        out.println();
        out.printf(Locale.ROOT, "%-20s %14s %5s%n", "Protocol", "ops/CPU-sec", "n");
        var protocols = summary.expectObjectMember("protocols");
        for (var protocol : BenchmarkProtocol.values()) {
            protocols.getObjectMember(protocol.summaryName())
                    .ifPresent(p -> out.printf(Locale.ROOT,
                            "%-20s %14s %5d%n",
                            protocol.summaryName(),
                            integers.format(Math.round(number(p, "ops_per_cpu_sec"))),
                            p.expectNumberMember("benchmark_count").getValue().intValue()));
        }
        var overall = summary.expectObjectMember("overall");
        out.printf(Locale.ROOT,
                "%-20s %14s %5d%n",
                "Overall (geomean)",
                integers.format(Math.round(number(overall, "ops_per_cpu_sec"))),
                overall.expectNumberMember("benchmark_count").getValue().intValue());
        var ratio = summary.expectObjectMember("cpu_wall_ratio");
        out.println();
        out.printf(Locale.ROOT,
                "CPU/wall ratio: mean %.3f (min %.3f, max %.3f); expect 0.95-1.05 on a quiet host%n",
                number(ratio, "mean"),
                number(ratio, "min"),
                number(ratio, "max"));
        out.printf(Locale.ROOT,
                "Under-warmed benchmarks: %d%n",
                summary.expectNumberMember("under_warmed_benchmarks").getValue().intValue());
        out.println("Results: " + output.toAbsolutePath());
    }

    static double number(ObjectNode node, String member) {
        return node.expectNumberMember(member).getValue().doubleValue();
    }

    static double round(double value, int decimals) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        double scale = Math.pow(10, decimals);
        return Math.round(value * scale) / scale;
    }
}
