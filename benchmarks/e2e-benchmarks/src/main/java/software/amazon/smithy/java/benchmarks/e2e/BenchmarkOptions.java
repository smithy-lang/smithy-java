/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class BenchmarkOptions {

    private static final DateTimeFormatter OUTPUT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private Mode mode = Mode.STUB;
    private Set<BenchmarkProtocol> protocols = EnumSet.allOf(BenchmarkProtocol.class);
    private List<String> filters = List.of();
    private boolean allModelCases;
    private Path output;
    private String instanceType;
    private String notes;
    private boolean list;
    private boolean help;

    private BenchmarkOptions() {}

    static BenchmarkOptions parse(String[] args) {
        var options = new BenchmarkOptions();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--mode" -> options.mode = Mode.parse(value(args, ++i, arg));
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
                case "--output" -> options.output = Path.of(value(args, ++i, arg));
                case "--instance-type" -> options.instanceType = value(args, ++i, arg);
                case "--notes" -> options.notes = value(args, ++i, arg);
                case "--list" -> options.list = true;
                case "--help", "-h" -> options.help = true;
                default -> throw new IllegalArgumentException("Unknown argument '" + arg + "'");
            }
        }
        if (options.output == null) {
            options.output = Path.of("e2e-ops-cpusec-"
                    + LocalDateTime.now(ZoneOffset.UTC).format(OUTPUT_TIMESTAMP) + ".json");
        }
        return options;
    }

    Mode mode() {
        return mode;
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

    String instanceType() {
        return instanceType;
    }

    String notes() {
        return notes;
    }

    boolean list() {
        return list;
    }

    boolean help() {
        return help;
    }

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

    private static String value(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " requires a value");
        }
        return args[index];
    }

    static String usage() {
        return """
                smithy-java serde E2E ops/CPU-sec benchmark

                Usage:
                  java -Xbatch -jar smithy-java-e2e-benchmark.jar [options]

                Runs complete generated-client calls in this JVM and reports operations per process
                CPU-second. Each case gets one checked call, automatic warmup, System.gc(), and measurement.
                Measurement stops at 50,000 iterations or 5 seconds of CPU time, whichever comes first,
                with a minimum of 1 second of CPU time. Run with -Xbatch to disable background JIT compilation.

                  --mode MODE              stub (default): in-process canned responses, the cross-SDK configuration
                                           https: smithy-java's HTTP client over TLS (BoringSSL) to a fixture
                                           server started in a child JVM, which serves each case's response in turn
                  --protocol NAMES         Comma-separated: awsJson1_0, rpcv2Cbor, awsQuery, restJson1, restXml
                                           (default: all)
                  --filter SUBSTRINGS      Case-insensitive substrings matched against benchmark ids
                  --all-model-cases        Include WideTypes and OutOfOrder cases outside the 71 canonical cases
                  --list                   Print the selected benchmark ids and exit
                  --output PATH            Results file (default e2e-ops-cpusec-<timestamp>.json)
                  --instance-type TYPE     Record this instance type instead of querying IMDSv2
                  --notes TEXT             Free-form annotation recorded in metadata
                  --help                   Show this message

                Compare a baseline run with a current run using scripts/compare-ocs.py.
                """;
    }
}
