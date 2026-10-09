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

/**
 * The single-run results file: one run of this harness, describing only itself. Comparing a baseline against a
 * current build is a separate step over two such files ({@link CompareRuns}), so each file stays a faithful record
 * of what was measured.
 */
final class RunReport {

    static final String SCHEMA = "smithy-java/e2e-ops-cpusec/1";
    static final String METRIC = "ops_per_cpu_second";
    static final String MEASUREMENT = "com.sun.management.OperatingSystemMXBean.getProcessCpuTime";
    static final String HTTP_MOCK = "In-process ClientTransport returning one pre-built canned HTTP response per "
            + "benchmark. No sockets and no localhost server. The request body is fully consumed as a real transport "
            + "would; each call gets a new zero-copy view over the same response bytes.";

    private RunReport() {}

    static ObjectNode benchmark(BenchmarkCase benchmarkCase, Measurement m, CountingTransport transport) {
        long httpRequests = transport.requests();
        long requestBodyBytes = transport.requestBodyBytes();
        var warmup = Node.objectNodeBuilder()
                .withMember("iterations", m.warmup().iterations())
                .withMember("automatic", m.warmup().automatic())
                .withMember("hit_cap", m.warmup().hitCap())
                .withMember("final_chunk_jit_share", round(m.warmup().finalChunkJitShare(), 5))
                .build();
        var verification = Node.objectNodeBuilder()
                .withMember("jit_compilation_millis_during_measurement", m.jitMillis())
                .withMember("jit_compilation_share_of_wall", round(m.jitShareOfWall(), 5))
                .withMember("under_warmed", m.underWarmed())
                .withMember("gc_count_during_measurement", m.gcCount())
                .withMember("gc_millis_during_measurement", m.gcMillis())
                .withMember("http_requests", httpRequests)
                .withMember("http_requests_match_iterations", httpRequests == m.iterations())
                .withMember("request_body_bytes_per_op", m.iterations() == 0 ? 0 : requestBodyBytes / m.iterations())
                .withMember("response_body_bytes", benchmarkCase.response().bodyLength())
                .withMember("response_status_observed", transport.lastResponseStatus())
                .withMember("response_length_observed", transport.lastResponseLength())
                .withMember("http_version", transport.lastHttpVersion())
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
                .withMember("ops_per_thread_cpu_sec", round(m.opsPerThreadCpuSecond(), 2))
                .withMember("cpu_seconds", round(m.processCpuNanos() / 1e9, 6))
                .withMember("thread_cpu_seconds", round(m.threadCpuNanos() / 1e9, 6))
                .withMember("wall_seconds", round(m.wallNanos() / 1e9, 6))
                .withMember("cpu_wall_ratio", round(m.cpuWallRatio(), 4))
                .withMember("warmup", warmup)
                .withMember("verification", verification)
                .build();
    }

    static ObjectNode metadata(
            BenchmarkOptions options,
            Settings settings,
            ObjectNode environment,
            Instant started,
            Instant finished,
            boolean backgroundJitDisabled,
            boolean forkedPerProtocol,
            int benchmarkCount,
            String transportDescription
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
                .withMember("warmup_policy_detail", settings.warmup().detail());
        if (!settings.warmup().automatic()) {
            builder.withMember("warmup_iterations", settings.warmup().iterations());
        }
        builder.withMember("background_jit_compilation_disabled", backgroundJitDisabled)
                .withMember("forked_per_protocol", forkedPerProtocol)
                .withMember("transport", options.transport().label())
                .withMember("transport_impl", transportDescription)
                .withMember("tls_verification", NetworkTransport.tlsVerification(options.transport()))
                .withMember("http_mock",
                        options.transport().isNetwork() ? "none: real transport to a fixture server" : HTTP_MOCK)
                .withMember("endpoint", options.endpoint())
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

    static List<ObjectNode> benchmarks(ObjectNode report) {
        List<ObjectNode> result = new ArrayList<>();
        for (Node node : report.expectArrayMember("benchmarks").getElements()) {
            result.add(node.expectObjectNode());
        }
        return result;
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
        long warmupTotal = 0;
        int hitCap = 0;
        int underWarmed = 0;
        int extraRequests = 0;
        for (var benchmark : benchmarks) {
            double ratio = number(benchmark, "cpu_wall_ratio");
            ratioMin = Math.min(ratioMin, ratio);
            ratioMax = Math.max(ratioMax, ratio);
            ratioSum += ratio;
            var warmup = benchmark.expectObjectMember("warmup");
            long warmupIterations = warmup.expectNumberMember("iterations").getValue().longValue();
            warmupMin = Math.min(warmupMin, warmupIterations);
            warmupMax = Math.max(warmupMax, warmupIterations);
            warmupTotal += warmupIterations;
            if (warmup.expectBooleanMember("hit_cap").getValue()) {
                hitCap++;
            }
            var verification = benchmark.expectObjectMember("verification");
            if (verification.expectBooleanMember("under_warmed").getValue()) {
                underWarmed++;
            }
            if (!verification.expectBooleanMember("http_requests_match_iterations").getValue()) {
                extraRequests++;
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
                .withMember("total_iterations", warmupTotal)
                .withMember("benchmarks_that_hit_the_cap", hitCap)
                .build();
        var verification = Node.objectNodeBuilder()
                .withMember("under_warmed_benchmarks", underWarmed)
                .withMember("benchmarks_with_extra_http_requests", extraRequests)
                .withMember("warmup_adequacy",
                        underWarmed == 0
                                ? "adequate: no benchmark spent more than "
                                        + (int) (CpuTimeRunner.UNDER_WARMED_JIT_SHARE * 100)
                                        + "% of its measured window compiling"
                                : underWarmed + " benchmark(s) spent more than "
                                        + (int) (CpuTimeRunner.UNDER_WARMED_JIT_SHARE * 100)
                                        + "% of the measured window compiling; widen the window with "
                                        + "--min-measure-cpu-seconds")
                .build();
        return Node.objectNodeBuilder()
                .withMember("protocols", protocols.build())
                .withMember("overall", overall)
                .withMember("cpu_wall_ratio", cpuWall)
                .withMember("warmup", warmupSummary)
                .withMember("verification", verification)
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

    static ObjectNode read(Path path) {
        try {
            var node = Node.parse(Files.readString(path, StandardCharsets.UTF_8)).expectObjectNode();
            String schema = node.getStringMemberOrDefault("schema", "");
            if (!SCHEMA.equals(schema)) {
                throw new IllegalArgumentException(path + " is not a " + SCHEMA + " results file (schema: '"
                        + schema + "')");
            }
            return node;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        }
    }

    static void printSummary(ObjectNode report, Path output, PrintStream out) {
        var metadata = report.expectObjectMember("metadata");
        var summary = report.expectObjectMember("summary");
        var environment = metadata.expectObjectMember("environment");
        NumberFormat integers = NumberFormat.getIntegerInstance(Locale.US);

        out.println();
        out.printf(Locale.ROOT,
                "smithy-java e2e ops/CPU-sec: %d benchmarks on %s, %s %s%s%n",
                metadata.expectNumberMember("benchmark_count").getValue().intValue(),
                metadata.expectStringMember("instance").getValue(),
                environment.expectObjectMember("java").expectStringMember("vm_name").getValue(),
                environment.expectObjectMember("java").expectStringMember("version").getValue(),
                metadata.expectBooleanMember("background_jit_compilation_disabled").getValue() ? " (-Xbatch)" : "");
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
        var verification = summary.expectObjectMember("verification");
        out.printf(Locale.ROOT,
                "Under-warmed benchmarks: %d; benchmarks with retries: %d%n",
                verification.expectNumberMember("under_warmed_benchmarks").getValue().intValue(),
                verification.expectNumberMember("benchmarks_with_extra_http_requests").getValue().intValue());
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
