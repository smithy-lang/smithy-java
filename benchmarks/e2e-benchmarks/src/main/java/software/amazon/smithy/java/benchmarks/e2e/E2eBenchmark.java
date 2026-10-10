/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import software.amazon.smithy.java.benchmarks.e2e.CpuTimeRunner.Measurement;
import software.amazon.smithy.java.benchmarks.e2e.CpuTimeRunner.Settings;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.model.node.ObjectNode;

public final class E2eBenchmark {

    private E2eBenchmark() {}

    public static void main(String[] args) throws Exception {
        BenchmarkOptions options;
        try {
            options = BenchmarkOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println();
            System.err.print(BenchmarkOptions.usage());
            System.exit(2);
            return;
        }
        if (options.help()) {
            System.out.print(BenchmarkOptions.usage());
            return;
        }

        quietLogging();
        List<String> ids = options.selectIds();
        if (options.list()) {
            ids.forEach(System.out::println);
            System.out.println(ids.size() + " benchmark(s)");
            return;
        }
        if (ids.isEmpty()) {
            System.err.println("No benchmarks match the given --protocol / --filter.");
            System.exit(2);
            return;
        }
        System.exit(run(options, ids));
    }

    static int run(BenchmarkOptions options, List<String> ids) throws IOException {
        Settings settings = Settings.standard();
        var runner = new CpuTimeRunner(settings);
        var integers = NumberFormat.getIntegerInstance(Locale.US);
        Instant started = Instant.now();
        Map<BenchmarkProtocol, BenchmarkClient> clients = new EnumMap<>(BenchmarkProtocol.class);
        List<ObjectNode> results = new ArrayList<>();

        try (Target target = Target.open(options.mode())) {
            System.err.println("transport: " + target.description());
            String instanceType = Environment.instanceType(options.instanceType());
            try {
                int index = 0;
                for (String id : ids) {
                    index++;
                    BenchmarkCase benchmarkCase = BenchmarkCases.build(id);
                    BenchmarkClient client = clients.computeIfAbsent(
                            benchmarkCase.protocol(),
                            protocol -> new BenchmarkClient(protocol, target.transport(), target.endpoint()));
                    BenchmarkClient.Call call = client.prepare(benchmarkCase);
                    System.err.printf(Locale.ROOT, "[%d/%d] %s ... ", index, ids.size(), id);
                    System.err.flush();

                    Measurement m;
                    try {
                        target.respondWith(benchmarkCase.response());
                        // One checked call before warmup: the response must deserialize into the expected output.
                        validateOutput(benchmarkCase, call.invoke());
                        m = runner.run(call::invoke);
                    } catch (Throwable t) {
                        System.err.println("FAILED");
                        System.err.println("Benchmark " + id + " threw. A benchmark that throws fails the run.");
                        t.printStackTrace(System.err);
                        return 1;
                    }
                    results.add(RunReport.benchmark(benchmarkCase, m));
                    System.err.printf(Locale.ROOT,
                            "%s ops/CPU-sec  (%s iterations, %.2f s CPU, cpu/wall %.2f, warmup %s%s, jit %d ms%s)%n",
                            integers.format(Math.round(m.opsPerCpuSecond())),
                            integers.format(m.iterations()),
                            m.processCpuNanos() / 1e9,
                            m.cpuWallRatio(),
                            integers.format(m.warmup().iterations()),
                            m.warmup().hitCap() ? " (cap)" : "",
                            m.jitMillis(),
                            m.underWarmed() ? ", UNDER-WARMED" : "");
                }
            } finally {
                clients.values().forEach(BenchmarkClient::close);
            }

            var environment = Environment.capture(instanceType);
            var metadata = RunReport.metadata(
                    options,
                    settings,
                    environment,
                    started,
                    Instant.now(),
                    hasXbatch(),
                    target,
                    results.size());
            var report = RunReport.report(metadata, results);
            RunReport.write(report, options.output());
            RunReport.printSummary(report, options.output(), System.out);
            return 0;
        }
    }

    static boolean hasXbatch() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-Xbatch");
    }

    /** Checks output values before measurement. Matching status and length alone does not prove the workload ran. */
    private static void validateOutput(BenchmarkCase benchmarkCase, SerializableStruct output) {
        boolean minimal = benchmarkCase.id().endsWith("_Baseline") || benchmarkCase.id().endsWith("_Example");
        if (benchmarkCase.source() != BenchmarkCase.Source.RESPONSE || minimal) {
            return;
        }
        for (String member : List.of("Item", "MetricDataResults", "CopyObjectResult", "Body")) {
            Schema schema = benchmarkCase.operation().outputSchema().member(member);
            if (schema != null && output.getMemberValue(schema) == null) {
                throw new IllegalStateException(benchmarkCase.id() + ": response deserialized but output member '"
                        + member + "' is null; the fixture does not match the benchmark's expected workload.");
            }
        }
    }

    private static void quietLogging() {
        if (!"true".equals(System.getProperty("smithy.bench.debug"))) {
            LogManager.getLogManager().reset();
            Logger.getLogger("").setLevel(Level.WARNING);
        }
    }
}
