/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import software.amazon.smithy.java.benchmarks.e2e.CpuTimeRunner.Measurement;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.model.node.ObjectNode;

public final class E2eBenchmark {

    private E2eBenchmark() {}

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("compare")) {
            System.exit(CompareRuns.run(Arrays.copyOfRange(args, 1, args.length)));
            return;
        }
        if (args.length > 0 && args[0].equals("export-fixture")) {
            quietLogging();
            System.exit(FixtureExport.run(Arrays.copyOfRange(args, 1, args.length)));
            return;
        }

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

        System.exit(options.inProcess() ? runInProcess(options, ids) : forkPerProtocol(options, ids));
    }

    static int runInProcess(BenchmarkOptions options, List<String> ids) {
        var settings = options.settings();
        var runner = new CpuTimeRunner(settings);
        var integers = NumberFormat.getIntegerInstance(Locale.US);
        Instant started = Instant.now();
        boolean xbatch = hasXbatch();
        Map<BenchmarkProtocol, BenchmarkClient> clients = new EnumMap<>(BenchmarkProtocol.class);
        List<ObjectNode> results = new ArrayList<>();
        try {
            int index = 0;
            for (String id : ids) {
                index++;
                BenchmarkCase benchmarkCase = BenchmarkCases.build(id);
                BenchmarkClient client = clients.computeIfAbsent(
                        benchmarkCase.protocol(),
                        protocol -> new BenchmarkClient(protocol, newTransport(options), options.endpoint()));
                BenchmarkClient.Call call = client.prepare(benchmarkCase);
                CountingTransport transport = client.transport();
                System.err.printf(Locale.ROOT, "[%d/%d] %s ... ", index, ids.size(), id);
                System.err.flush();

                Measurement m;
                try {
                    // Check the fixture and output before measurement.
                    SerializableStruct output = call.invoke();
                    transport.validateLast(benchmarkCase);
                    validateWorkload(benchmarkCase, output);
                    m = runner.run(call::invoke, transport::resetCounters);
                } catch (Throwable t) {
                    System.err.println("FAILED");
                    System.err.println("Benchmark " + id + " threw. A benchmark that throws fails the run.");
                    t.printStackTrace(System.err);
                    return 1;
                }
                results.add(RunReport.benchmark(benchmarkCase, m, transport));
                System.err.printf(Locale.ROOT,
                        "%s ops/CPU-sec  (%s iterations, %.2f s CPU, cpu/wall %.2f, warmup %s%s, jit %d ms%s)%n",
                        integers.format(Math.round(m.opsPerCpuSecond())),
                        integers.format(m.iterations()),
                        m.processCpuNanos() / 1e9,
                        m.cpuWallRatio(),
                        integers.format(m.warmup().iterations()),
                        m.warmup().automatic() ? (m.warmup().hitCap() ? " auto/cap" : " auto") : " fixed",
                        m.jitMillis(),
                        m.underWarmed() ? ", UNDER-WARMED" : "");
            }
        } finally {
            clients.values().forEach(BenchmarkClient::close);
        }

        Instant finished = Instant.now();
        var environment = Environment.capture(Environment.instanceType(options.instanceType()));
        var metadata = RunReport.metadata(
                options,
                settings,
                environment,
                started,
                finished,
                xbatch,
                false,
                results.size(),
                transportDescription(clients));
        var report = RunReport.report(metadata, results);
        RunReport.write(report, options.output());
        if (!options.child()) {
            RunReport.printSummary(report, options.output(), System.out);
        }
        return 0;
    }

    static int forkPerProtocol(BenchmarkOptions options, List<String> ids) throws IOException, InterruptedException {
        Instant started = Instant.now();
        var protocols = EnumSet.noneOf(BenchmarkProtocol.class);
        for (String id : ids) {
            protocols.add(BenchmarkProtocol.forBenchmarkId(id));
        }
        // Resolve IMDS once and pass the instance type to each child.
        String instanceType = Environment.instanceType(options.instanceType());

        Path tmp = Files.createTempDirectory("smithy-java-e2e-");
        List<ObjectNode> benchmarks = new ArrayList<>();
        Boolean xbatch = null;
        String childTransport = null;
        ObjectNode childJava = null;
        List<Path> childOutputs = new ArrayList<>();
        for (var protocol : protocols) {
            Path childOutput = tmp.resolve(protocol.summaryName() + ".json");
            childOutputs.add(childOutput);
            System.err.printf(Locale.ROOT, "=== %s: fresh JVM with -Xbatch ===%n", protocol.summaryName());
            int code = spawn(options.childArgs(protocol, childOutput, instanceType));
            if (code != 0) {
                System.err.println("The " + protocol.summaryName() + " child JVM exited with status " + code
                        + "; partial results are in " + tmp);
                return code;
            }
            var child = RunReport.read(childOutput);
            benchmarks.addAll(RunReport.benchmarks(child));
            if (xbatch == null) {
                var childMetadata = child.expectObjectMember("metadata");
                xbatch = childMetadata.expectBooleanMember("background_jit_compilation_disabled").getValue();
                childJava = childMetadata.expectObjectMember("environment").expectObjectMember("java");
                childTransport = childMetadata.getStringMemberOrDefault("transport_impl", null);
            }
        }

        // Record the measured child JVM flags.
        var environment = Environment.capture(instanceType);
        if (childJava != null) {
            environment = environment.toBuilder().withMember("java", childJava).build();
        }
        var metadata = RunReport.metadata(
                options,
                options.settings(),
                environment,
                started,
                Instant.now(),
                xbatch != null && xbatch,
                true,
                benchmarks.size(),
                childTransport != null ? childTransport : newTransport(options).description());
        var report = RunReport.report(metadata, benchmarks);
        RunReport.write(report, options.output());
        RunReport.printSummary(report, options.output(), System.out);

        for (Path childOutput : childOutputs) {
            Files.deleteIfExists(childOutput);
        }
        Files.deleteIfExists(tmp);
        return 0;
    }

    static int spawn(List<String> programArgs) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
        command.addAll(jvmArgs);
        if (!jvmArgs.contains("-Xbatch")) {
            command.add("-Xbatch");
        }
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(E2eBenchmark.class.getName());
        command.addAll(programArgs);
        return new ProcessBuilder(command).inheritIO().start().waitFor();
    }

    static CountingTransport newTransport(BenchmarkOptions options) {
        return options.transport().isNetwork()
                ? NetworkTransport.create(options.transport())
                : new MockHttpTransport();
    }

    private static String transportDescription(Map<BenchmarkProtocol, BenchmarkClient> clients) {
        return clients.values().iterator().next().transport().description();
    }

    static boolean hasXbatch() {
        return ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-Xbatch");
    }

    /** Checks output values before measurement. Matching status and length alone does not prove the workload is correct. */
    private static void validateWorkload(BenchmarkCase benchmarkCase, SerializableStruct output) {
        boolean minimal = benchmarkCase.id().endsWith("_Baseline") || benchmarkCase.id().endsWith("_Example");
        if (benchmarkCase.source() != BenchmarkCase.Source.RESPONSE || minimal) {
            return;
        }
        for (String member : java.util.List.of("Item", "MetricDataResults", "CopyObjectResult", "Body")) {
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
