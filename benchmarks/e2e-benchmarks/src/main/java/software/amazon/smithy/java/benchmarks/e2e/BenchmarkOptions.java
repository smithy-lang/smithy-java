/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Benchmark selection, transport and output options; only the CPU-time floor is configurable in the measurement.
 */
final class BenchmarkOptions {

    private static final DateTimeFormatter OUTPUT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private Set<BenchmarkProtocol> protocols = EnumSet.allOf(BenchmarkProtocol.class);
    private List<String> filters = List.of();
    private boolean allModelCases;
    private double minMeasureCpuSeconds = CpuTimeRunner.DEFAULT_MIN_MEASURE_CPU_SECONDS;
    private TransportMode transport = TransportMode.STUB;
    private String endpoint;
    private Path output;
    private String instanceType;
    private String notes;
    private boolean inProcess;
    private boolean child;
    private boolean list;
    private boolean help;

    private BenchmarkOptions() {}

    static BenchmarkOptions parse(String[] args) {
        var options = new BenchmarkOptions();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--protocol" -> {
                    options.protocols = EnumSet.noneOf(BenchmarkProtocol.class);
                    for (String name : value(args, ++i, arg).split(",")) {
                        if (name.equalsIgnoreCase("all")) {
                            options.protocols = EnumSet.allOf(BenchmarkProtocol.class);
                        } else if (!name.isBlank()) {
                            options.protocols.add(BenchmarkProtocol.parse(name.strip()));
                        }
                    }
                }
                case "--filter" -> {
                    var filters = new ArrayList<String>();
                    for (String f : value(args, ++i, arg).split(",")) {
                        if (!f.isBlank()) {
                            filters.add(f.strip().toLowerCase(Locale.ROOT));
                        }
                    }
                    options.filters = List.copyOf(filters);
                }
                case "--all-model-cases" -> options.allModelCases = true;
                case "--min-measure-cpu-seconds" ->
                    options.minMeasureCpuSeconds = parseDouble(value(args, ++i, arg), arg);
                case "--transport" -> options.transport = TransportMode.parse(value(args, ++i, arg));
                case "--endpoint" -> options.endpoint = value(args, ++i, arg);
                case "--output" -> options.output = Path.of(value(args, ++i, arg));
                case "--instance-type" -> options.instanceType = value(args, ++i, arg);
                case "--notes" -> options.notes = value(args, ++i, arg);
                case "--in-process" -> options.inProcess = true;
                case "--child" -> {
                    options.child = true;
                    options.inProcess = true;
                }
                case "--list" -> options.list = true;
                case "--help", "-h" -> options.help = true;
                default -> throw new IllegalArgumentException("Unknown argument '" + arg + "'");
            }
        }
        if (options.output == null) {
            options.output = Path.of("e2e-ops-cpusec-"
                    + LocalDateTime.now(ZoneOffset.UTC).format(OUTPUT_TIMESTAMP) + ".json");
        }
        if (options.endpoint == null) {
            options.endpoint = options.transport.defaultEndpoint();
        } else if (!options.transport.isNetwork()) {
            throw new IllegalArgumentException("--endpoint only applies to --transport http or https");
        }
        if (options.transport.isNetwork()) {
            URI uri = URI.create(options.endpoint);
            if (!options.transport.label().equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("--endpoint must be an absolute " + options.transport.label()
                        + " URL with a host");
            }
        }
        // Validate the loop parameters eagerly so bad values fail before any JVM is launched.
        options.settings();
        return options;
    }

    Set<BenchmarkProtocol> protocols() {
        return protocols;
    }

    List<String> filters() {
        return filters;
    }

    boolean allModelCases() {
        return allModelCases;
    }

    Path output() {
        return output;
    }

    /** The instance type to record, or null to look it up. */
    String instanceType() {
        return instanceType;
    }

    String notes() {
        return notes;
    }

    TransportMode transport() {
        return transport;
    }

    String endpoint() {
        return endpoint;
    }

    /** Run in this JVM rather than forking one child JVM per protocol. */
    boolean inProcess() {
        return inProcess;
    }

    /** This JVM is a child of the per-protocol fork: write the results file, let the parent print the summary. */
    boolean child() {
        return child;
    }

    boolean list() {
        return list;
    }

    boolean help() {
        return help;
    }

    CpuTimeRunner.Settings settings() {
        return CpuTimeRunner.Settings.standard(minMeasureCpuSeconds);
    }

    /** The benchmark ids selected by {@code --protocol}, {@code --filter} and {@code --all-model-cases}, in order. */
    List<String> selectIds() {
        List<String> candidates = allModelCases ? BenchmarkCases.allIds() : BenchmarkCases.canonicalIds();
        List<String> selected = new ArrayList<>();
        for (String id : candidates) {
            if (!protocols.contains(BenchmarkProtocol.forBenchmarkId(id))) {
                continue;
            }
            if (!filters.isEmpty() && !matchesFilter(id)) {
                continue;
            }
            selected.add(id);
        }
        return selected;
    }

    private boolean matchesFilter(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        for (String filter : filters) {
            if (lower.contains(filter)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Arguments for a child JVM that runs one protocol in-process and writes to {@code childOutput}. The parent
     * has already resolved the instance type, so children never query IMDS themselves.
     */
    List<String> childArgs(BenchmarkProtocol protocol, Path childOutput, String instanceType) {
        List<String> args = new ArrayList<>();
        args.add("--child");
        args.add("--protocol");
        args.add(protocol.idPrefix());
        if (!filters.isEmpty()) {
            args.add("--filter");
            args.add(String.join(",", filters));
        }
        if (allModelCases) {
            args.add("--all-model-cases");
        }
        args.add("--min-measure-cpu-seconds");
        args.add(Double.toString(minMeasureCpuSeconds));
        if (transport.isNetwork()) {
            args.add("--transport");
            args.add(transport.label());
            args.add("--endpoint");
            args.add(endpoint);
        }
        args.add("--instance-type");
        args.add(instanceType);
        if (notes != null) {
            args.add("--notes");
            args.add(notes);
        }
        args.add("--output");
        args.add(childOutput.toString());
        return args;
    }

    private static String value(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " requires a value");
        }
        return args[index];
    }

    private static double parseDouble(String value, String flag) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(flag + " expects a number, got '" + value + "'");
        }
    }

    static String usage() {
        return """
                smithy-java serde E2E ops/CPU-sec benchmark

                Usage:
                  java -jar smithy-java-e2e-benchmark.jar [options]
                  java -jar smithy-java-e2e-benchmark.jar compare --baseline <file|dir> --current <file|dir> --out <prefix>
                  java -jar smithy-java-e2e-benchmark.jar export-fixture <benchmark-id> [--out DIR]

                Runs each benchmark as a complete generated-client call and reports operations per process
                CPU-second. The loop is the cross-SDK one: warm up until JIT compilation goes quiet, then
                iterate until 50,000 iterations or 5 seconds of process CPU time, whichever comes first,
                checking every 100 iterations. One child JVM per protocol is launched with -Xbatch and the
                results are merged into one file.

                Selection:
                  --protocol NAMES         Comma-separated: awsJson1_0, rpcv2Cbor, awsQuery, restJson1, restXml
                                           (default: all)
                  --filter SUBSTRINGS      Case-insensitive substrings matched against benchmark ids
                  --all-model-cases        Include the smithy-java-only cases (WideTypes, OutOfOrder) in
                                           addition to the 71 canonical cross-SDK benchmarks
                  --list                   Print the selected benchmark ids and exit

                Transport:
                  --transport MODE         stub (default): the in-process mock, the cross-SDK configuration.
                                           http or https: smithy-java's own HTTP client (BoringSSL TLS) against
                                           a fixture server that serves the benchmark's response, one fixture
                                           per server run; see export-fixture
                  --endpoint URL           The fixture server (default http://127.0.0.1:8080 or
                                           https://127.0.0.1:8443)

                Measurement:
                  --min-measure-cpu-seconds S
                                           Never close a measured window before S seconds of process CPU time
                                           (default 1). The JVM reads process CPU time in 10 ms ticks on Linux,
                                           so a shorter window is quantized; 0 restores the literal cross-SDK
                                           loop, and a large value gives a profiler a long steady-state window
                  --in-process             Run in this JVM instead of one child JVM per protocol, for attaching
                                           a profiler (add -Xbatch yourself to match the child JVMs)

                Output:
                  --output PATH            Results file (default e2e-ops-cpusec-<timestamp>.json)
                  --instance-type TYPE     Record this instance type instead of querying IMDSv2
                  --notes TEXT             Free-form annotation recorded in metadata
                  --help                   Show this message

                JVM flags given to this process (for example -Dsmithy-java.* toggles or -Xmx) are forwarded to
                the child JVMs.
                """;
    }
}
