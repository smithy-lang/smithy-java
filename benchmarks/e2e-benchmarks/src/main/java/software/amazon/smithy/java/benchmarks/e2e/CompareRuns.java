/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Turns a baseline run and a current run into the cross-SDK comparison files
 * ({@code <prefix>.json} and {@code <prefix>.md}) whose schema and layout match
 * {@code results/<sdk>/<instancetype>_ocs_results.*} in AwsSdkPerformanceBenchmarkModels.
 *
 * <p>Either side may be a single results file or a directory of them. A directory is merged: runs covering
 * different protocols are concatenated, and when the same benchmark appears in several runs (the SOP asks for
 * three interleaved samples per side) the median ops/CPU-sec is used.
 *
 * <p>Comparability is checked, not assumed. Different stop conditions, CPU measurements or iteration floors are
 * errors. A different benchmark set is an error unless {@code --allow-partial} is given, in which case the
 * intersection is compared and the difference is recorded as a warning. Differences in instance, CPU, JVM or JIT
 * settings are warnings carried into the output.
 */
final class CompareRuns {

    static final String SCHEMA = "smithy-java/e2e-ops-cpusec-comparison/1";

    private CompareRuns() {}

    static int run(String[] args) {
        Path baseline = null;
        Path current = null;
        Path out = null;
        String lang = "smithy-java";
        boolean allowPartial = false;
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--baseline" -> baseline = Path.of(value(args, ++i, "--baseline"));
                    case "--current" -> current = Path.of(value(args, ++i, "--current"));
                    case "--out", "--output", "-o" -> out = Path.of(value(args, ++i, "--out"));
                    case "--lang" -> lang = value(args, ++i, "--lang");
                    case "--allow-partial" -> allowPartial = true;
                    case "--help", "-h" -> {
                        System.out.print(usage());
                        return 0;
                    }
                    default -> throw new IllegalArgumentException("Unknown argument '" + args[i] + "'");
                }
            }
            if (baseline == null || current == null || out == null) {
                throw new IllegalArgumentException("--baseline, --current and --out are required");
            }
            var comparison = compare(Side.load(baseline), Side.load(current), lang, allowPartial);
            write(comparison, out);
            System.out.print(comparison.textReport());
            System.out.println("Wrote " + out + ".json and " + out + ".md");
            return 0;
        } catch (IllegalArgumentException | IllegalStateException | UncheckedIOException e) {
            System.err.println("error: " + e.getMessage());
            if (!(e instanceof UncheckedIOException)) {
                System.err.println();
                System.err.print(usage());
            }
            return 2;
        }
    }

    static String usage() {
        return """
                Usage:
                  java -jar smithy-java-e2e-benchmark.jar compare --baseline <file|dir> --current <file|dir> --out <prefix>
                         [--lang <label>] [--allow-partial]

                Writes <prefix>.json and <prefix>.md in the cross-SDK ops/CPU-sec results layout, for example
                  --out results/smithy-java/m7imetal24xl_ocs_results

                A directory is merged: per-protocol runs are concatenated and repeated samples of the same
                benchmark are reduced to their median.
                """;
    }

    static Comparison compare(Side baseline, Side current, String lang, boolean allowPartial) {
        List<String> warnings = new ArrayList<>();

        // Hard requirements: the two sides must have measured the same thing the same way.
        requireEqual(baseline, current, "stop_condition", "stop conditions");
        requireEqual(baseline, current, "measurement", "CPU time measurements");
        requireEqual(baseline, current, "min_iterations", "iteration floors");
        requireEqual(baseline, current, "check_interval", "check intervals");
        requireEqual(baseline, current, "client", "client modes");
        requireEqual(baseline, current, "transport", "transports");
        warnIfDifferent(baseline, current, warnings, "transport_impl", "transport implementations");

        // Soft requirements: carried as warnings.
        warnIfDifferent(baseline, current, warnings, "instance", "instance types");
        warnIfDifferent(baseline, current, warnings, "warmup_policy", "warmup policies");
        warnIfDifferent(baseline, current, warnings, "background_jit_compilation_disabled", "-Xbatch settings");
        warnIfDifferent(baseline, current, warnings, "forked_per_protocol", "per-protocol forking");
        if (!baseline.environment("cpu").equals(current.environment("cpu"))) {
            warnings.add("Different CPUs: baseline '" + baseline.environment("cpu") + "', current '"
                    + current.environment("cpu") + "'");
        }
        if (!baseline.environment("available_processors").equals(current.environment("available_processors"))) {
            warnings.add("Different core counts: baseline " + baseline.environment("available_processors")
                    + ", current " + current.environment("available_processors"));
        }
        if (!baseline.javaVersion().equals(current.javaVersion())) {
            warnings.add("Different JVMs: baseline " + baseline.javaVersion() + ", current " + current.javaVersion());
        }
        if (!baseline.hasXbatch()) {
            warnings.add("Baseline was measured without -Xbatch");
        }
        if (!current.hasXbatch()) {
            warnings.add("Current was measured without -Xbatch");
        }

        // The benchmark set.
        Set<String> common = new LinkedHashSet<>();
        for (String id : orderedIds(baseline.samples.keySet(), current.samples.keySet())) {
            if (baseline.samples.containsKey(id) && current.samples.containsKey(id)) {
                common.add(id);
            }
        }
        int onlyBaseline = baseline.samples.size() - common.size();
        int onlyCurrent = current.samples.size() - common.size();
        if (onlyBaseline > 0 || onlyCurrent > 0) {
            String message = "Benchmark sets differ: " + onlyBaseline + " only in baseline, " + onlyCurrent
                    + " only in current, " + common.size() + " in common";
            if (!allowPartial) {
                throw new IllegalStateException(message + ". Pass --allow-partial to compare the intersection.");
            }
            warnings.add(message + "; compared the intersection");
        }
        if (common.isEmpty()) {
            throw new IllegalStateException("The two sides have no benchmarks in common");
        }

        // Per-benchmark entries and per-protocol / overall geometric means.
        List<ObjectNode> entries = new ArrayList<>();
        Map<String, List<Double>> baselineByProtocol = new LinkedHashMap<>();
        Map<String, List<Double>> currentByProtocol = new LinkedHashMap<>();
        List<Double> baselineAll = new ArrayList<>();
        List<Double> currentAll = new ArrayList<>();
        for (String id : common) {
            double b = baseline.median(id);
            double c = current.median(id);
            String protocol = current.protocolOf.get(id);
            entries.add(Node.objectNodeBuilder()
                    .withMember("id", id)
                    .withMember("baseline", Math.round(b))
                    .withMember("current", Math.round(c))
                    .withMember("improvement_pct", improvementPct(b, c))
                    .build());
            baselineByProtocol.computeIfAbsent(protocol, k -> new ArrayList<>()).add(b);
            currentByProtocol.computeIfAbsent(protocol, k -> new ArrayList<>()).add(c);
            baselineAll.add(b);
            currentAll.add(c);
        }

        var protocols = Node.objectNodeBuilder();
        List<ProtocolSummary> protocolSummaries = new ArrayList<>();
        for (var protocol : BenchmarkProtocol.values()) {
            var b = baselineByProtocol.get(protocol.summaryName());
            var c = currentByProtocol.get(protocol.summaryName());
            if (b == null || c == null) {
                continue;
            }
            var summary =
                    new ProtocolSummary(protocol.summaryName(), GeometricMean.of(b), GeometricMean.of(c), b.size());
            protocolSummaries.add(summary);
            protocols.withMember(protocol.summaryName(), summary.toNode());
        }
        var overall = new ProtocolSummary("Overall",
                GeometricMean.of(baselineAll),
                GeometricMean.of(currentAll),
                common.size());

        var quality = Node.objectNodeBuilder()
                .withMember("baseline_cpu_wall_ratio_mean", baseline.cpuWallRatioMean())
                .withMember("current_cpu_wall_ratio_mean", current.cpuWallRatioMean())
                .withMember("baseline_under_warmed_benchmarks", baseline.underWarmed)
                .withMember("current_under_warmed_benchmarks", current.underWarmed)
                .withMember("baseline_samples_per_benchmark", baseline.samplesPerBenchmark())
                .withMember("current_samples_per_benchmark", current.samplesPerBenchmark())
                .build();

        var metadata = Node.objectNodeBuilder()
                .withMember("lang", lang)
                .withMember("sdk", "smithy-java")
                .withMember("instance", current.metadata("instance"))
                .withMember("region", current.metadata("region"))
                .withMember("metric", RunReport.METRIC)
                .withMember("measurement", current.metadata("measurement"))
                .withMember("stop_condition", current.metadata("stop_condition"))
                .withMember("min_iterations", current.metadataNode("min_iterations"))
                .withMember("warmup_policy", current.metadata("warmup_policy"))
                .withMember("warmup_policy_detail", current.metadata("warmup_policy_detail"));
        if (current.metadataNode("warmup_iterations") != null) {
            metadata.withMember("warmup_iterations", current.metadataNode("warmup_iterations"));
        }
        metadata.withMember("background_jit_compilation_disabled", current.hasXbatch())
                .withMember("client", current.metadata("client"))
                .withMember("http_mock", current.metadata("http_mock"))
                .withMember("software",
                        ArrayNode.fromNodes(
                                ArrayNode.fromStrings("Java", current.javaVersion()),
                                ArrayNode.fromStrings("smithy-java", current.metadata("sdk_version"))))
                .withMember("os", current.environment("os_label"))
                .withMember("cpu", current.environment("cpu"))
                .withMember("baseline_sdk_version", baseline.metadata("sdk_version"))
                .withMember("baseline_date", baseline.date())
                .withMember("baseline_commit", baseline.metadata("commit"))
                .withMember("baseline_branch", baseline.metadata("branch"))
                .withMember("baseline_runs", baseline.files.size())
                .withMember("current_sdk_version", current.metadata("sdk_version"))
                .withMember("current_date", current.date())
                .withMember("current_commit", current.metadata("commit"))
                .withMember("current_branch", current.metadata("branch"))
                .withMember("current_runs", current.files.size())
                .withMember("comparability_warnings", Node.fromStrings(warnings));

        var summary = Node.objectNodeBuilder()
                .withMember("protocols", protocols.build())
                .withMember("overall", overall.toNode().toBuilder().withMember("aggregation", "geometric_mean").build())
                .withMember("quality", quality)
                .build();
        var json = Node.objectNodeBuilder()
                .withMember("schema", SCHEMA)
                .withMember("metadata", metadata.build())
                .withMember("benchmarks", ArrayNode.fromNodes(entries.toArray(new Node[0])))
                .withMember("summary", summary)
                .build();
        return new Comparison(json, protocolSummaries, overall, warnings, baseline, current);
    }

    static void write(Comparison comparison, Path prefix) {
        Path json = Path.of(prefix + ".json");
        Path md = Path.of(prefix + ".md");
        try {
            if (json.getParent() != null) {
                Files.createDirectories(json.getParent());
            }
            Files.writeString(json, Node.prettyPrintJson(comparison.json()) + "\n", StandardCharsets.UTF_8);
            Files.writeString(md, comparison.markdown(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write " + prefix + ".{json,md}", e);
        }
    }

    /** Canonical ids in reporting order first, then anything else alphabetically. */
    private static List<String> orderedIds(Set<String> a, Set<String> b) {
        Set<String> all = new LinkedHashSet<>(a);
        all.addAll(b);
        List<String> ordered = new ArrayList<>();
        for (String id : BenchmarkCases.canonicalIds()) {
            if (all.remove(id)) {
                ordered.add(id);
            }
        }
        List<String> rest = new ArrayList<>(all);
        Collections.sort(rest);
        ordered.addAll(rest);
        return ordered;
    }

    private static void requireEqual(Side baseline, Side current, String member, String what) {
        String b = baseline.metadata(member);
        String c = current.metadata(member);
        if (!b.equals(c)) {
            throw new IllegalStateException("The runs are not comparable: different " + what
                    + " (baseline '" + b + "', current '" + c + "')");
        }
    }

    private static void warnIfDifferent(
            Side baseline,
            Side current,
            List<String> warnings,
            String member,
            String what
    ) {
        String b = baseline.metadata(member);
        String c = current.metadata(member);
        if (!b.equals(c)) {
            warnings.add("Different " + what + ": baseline '" + b + "', current '" + c + "'");
        }
    }

    static double improvementPct(double baseline, double current) {
        return RunReport.round((current / baseline - 1) * 100, 2);
    }

    private static String value(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " requires a value");
        }
        return args[index];
    }

    /** Formats a percentage the way the shared markdown generator does: explicit sign, U+2212 for negatives. */
    static String formatPct(double pct) {
        String magnitude = BigDecimal.valueOf(Math.abs(pct)).stripTrailingZeros().toPlainString();
        return (pct < 0 ? "−" : "+") + magnitude + "%";
    }

    static String formatInt(long value) {
        return NumberFormat.getIntegerInstance(Locale.US).format(value);
    }

    record ProtocolSummary(String name, double baseline, double current, int count) {
        double improvementPct() {
            return CompareRuns.improvementPct(baseline, current);
        }

        ObjectNode toNode() {
            return Node.objectNodeBuilder()
                    .withMember("baseline", Math.round(baseline))
                    .withMember("current", Math.round(current))
                    .withMember("improvement_pct", improvementPct())
                    .withMember("benchmark_count", count)
                    .build();
        }
    }

    record Comparison(
            ObjectNode json,
            List<ProtocolSummary> protocols,
            ProtocolSummary overall,
            List<String> warnings,
            Side baseline,
            Side current) {

        /** Markdown in the exact layout of AwsSdkPerformanceBenchmarkModels' {@code scripts/markdown-ocs.js}. */
        String markdown() {
            var metadata = json.expectObjectMember("metadata");
            var sb = new StringBuilder();
            sb.append("# ")
                    .append(metadata.expectStringMember("lang").getValue())
                    .append(" — Ops/CPU-Sec E2E Results\n\n");
            sb.append("## ")
                    .append(metadata.expectStringMember("os").getValue())
                    .append(' ')
                    .append(metadata.expectStringMember("instance").getValue())
                    .append(" (")
                    .append(metadata.expectStringMember("region").getValue())
                    .append(")\n\n");
            sb.append("```\n");
            for (Node pair : metadata.expectArrayMember("software").getElements()) {
                var elements = pair.expectArrayNode().getElements();
                sb.append(elements.get(0).expectStringNode().getValue())
                        .append(" / ")
                        .append(elements.get(1).expectStringNode().getValue())
                        .append('\n');
            }
            sb.append("Metric: ")
                    .append(metadata.expectStringMember("metric").getValue())
                    .append(" (higher is better)\n");
            sb.append("Stop condition: ").append(metadata.expectStringMember("stop_condition").getValue()).append('\n');
            sb.append("Baseline: ")
                    .append(metadata.expectStringMember("baseline_date").getValue())
                    .append(" (commit ")
                    .append(metadata.expectStringMember("baseline_commit").getValue())
                    .append(")\n");
            sb.append("Current: ")
                    .append(metadata.expectStringMember("current_date").getValue())
                    .append(" (commit ")
                    .append(metadata.expectStringMember("current_commit").getValue())
                    .append(")\n");
            sb.append("```\n\n");

            sb.append("| id | baseline | current | improvement_pct |\n");
            sb.append("|---:|---:|---:|---:|\n");
            for (Node node : json.expectArrayMember("benchmarks").getElements()) {
                var b = node.expectObjectNode();
                sb.append("| ")
                        .append(b.expectStringMember("id").getValue())
                        .append(" | ")
                        .append(formatInt(b.expectNumberMember("baseline").getValue().longValue()))
                        .append(" | ")
                        .append(formatInt(b.expectNumberMember("current").getValue().longValue()))
                        .append(" | ")
                        .append(formatPct(b.expectNumberMember("improvement_pct").getValue().doubleValue()))
                        .append(" |\n");
            }

            sb.append("\n### Protocol Summary (geometric mean)\n\n");
            sb.append("| Protocol | Baseline | Current | Improvement |\n");
            sb.append("|---|---:|---:|---:|\n");
            for (var p : protocols) {
                sb.append("| ")
                        .append(p.name())
                        .append(" | ")
                        .append(formatInt(Math.round(p.baseline())))
                        .append(" | ")
                        .append(formatInt(Math.round(p.current())))
                        .append(" | ")
                        .append(formatPct(p.improvementPct()))
                        .append(" |\n");
            }
            sb.append("| **Overall** | **")
                    .append(formatInt(Math.round(overall.baseline())))
                    .append("** | **")
                    .append(formatInt(Math.round(overall.current())))
                    .append("** | **")
                    .append(formatPct(overall.improvementPct()))
                    .append("** |\n\n");
            return sb.toString();
        }

        /** The report block the SOP asks each SDK to submit. */
        String textReport() {
            var metadata = json.expectObjectMember("metadata");
            var sb = new StringBuilder();
            sb.append(
                    String.format(Locale.ROOT, "%nSDK:          %s%n", metadata.expectStringMember("lang").getValue()));
            sb.append(String.format(Locale.ROOT, "Architecture: %s%n", current.environment("os_label")));
            sb.append(String
                    .format(Locale.ROOT, "Instance:     %s%n", metadata.expectStringMember("instance").getValue()));
            sb.append(String.format(Locale.ROOT,
                    "Date:         %s (baseline %s)%n%n",
                    metadata.expectStringMember("current_date").getValue(),
                    metadata.expectStringMember("baseline_date").getValue()));
            sb.append("Per-Protocol Geometric Mean (ops/CPU-sec):\n");
            sb.append(String
                    .format(Locale.ROOT, "  %-12s %10s %10s %10s%n", "Protocol", "Baseline", "Current", "Delta %"));
            for (var p : protocols) {
                sb.append(String.format(Locale.ROOT,
                        "  %-12s %10s %10s %10s%n",
                        p.name(),
                        formatInt(Math.round(p.baseline())),
                        formatInt(Math.round(p.current())),
                        formatPct(p.improvementPct())));
            }
            sb.append(String.format(Locale.ROOT, "%nOverall Geometric Mean:%n"));
            sb.append(String.format(Locale.ROOT,
                    "  Baseline:     %s ops/CPU-sec%n",
                    formatInt(Math.round(overall.baseline()))));
            sb.append(String
                    .format(Locale.ROOT, "  Current:      %s ops/CPU-sec%n", formatInt(Math.round(overall.current()))));
            sb.append(String.format(Locale.ROOT, "  Delta:        %s%n", formatPct(overall.improvementPct())));
            double reduction = (1 - overall.baseline() / overall.current()) * 100;
            sb.append(String.format(Locale.ROOT,
                    "%nCPU Time Reduction:%n  %.1f%% %s CPU time per operation%n",
                    Math.abs(reduction),
                    reduction >= 0 ? "less" : "more"));
            sb.append(String.format(Locale.ROOT,
                    "%nCPU/Wall Ratio: baseline %.3f, current %.3f (expect 0.95-1.05)%n",
                    baseline.cpuWallRatioMean(),
                    current.cpuWallRatioMean()));
            if (!warnings.isEmpty()) {
                sb.append("\nComparability warnings:\n");
                for (String warning : warnings) {
                    sb.append("  - ").append(warning).append('\n');
                }
            }
            sb.append('\n');
            return sb.toString();
        }
    }

    /** One side of a comparison: every single-run file for it, merged. */
    static final class Side {
        final List<Path> files = new ArrayList<>();
        final Map<String, List<Double>> samples = new LinkedHashMap<>();
        final Map<String, String> protocolOf = new LinkedHashMap<>();
        private final List<Double> cpuWallRatioMeans = new ArrayList<>();
        private ObjectNode metadata;
        private int underWarmed;

        static Side load(Path path) {
            var side = new Side();
            List<Path> candidates = new ArrayList<>();
            if (Files.isDirectory(path)) {
                try (Stream<Path> stream = Files.list(path)) {
                    stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
                            .sorted()
                            .forEach(candidates::add);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to list " + path, e);
                }
                if (candidates.isEmpty()) {
                    throw new IllegalArgumentException("No .json results files in " + path);
                }
            } else if (Files.isRegularFile(path)) {
                candidates.add(path);
            } else {
                throw new IllegalArgumentException("No such file or directory: " + path);
            }
            for (Path file : candidates) {
                side.add(file, RunReport.read(file));
            }
            return side;
        }

        private void add(Path file, ObjectNode report) {
            var reportMetadata = report.expectObjectMember("metadata");
            if (metadata == null) {
                metadata = reportMetadata;
            } else {
                for (String member : List.of(
                        "stop_condition",
                        "measurement",
                        "min_iterations",
                        "check_interval",
                        "client",
                        "transport",
                        "transport_impl",
                        "tls_verification",
                        "commit",
                        "sdk_version",
                        "instance",
                        "warmup_policy",
                        "warmup_policy_detail",
                        "background_jit_compilation_disabled",
                        "forked_per_protocol",
                        "environment")) {
                    String first = string(metadata, member);
                    String other = string(reportMetadata, member);
                    if (!first.equals(other)) {
                        throw new IllegalStateException(file + " has a different " + member + " ('" + other
                                + "') than " + files.get(0) + " ('" + first + "'); runs on one side must match");
                    }
                }
            }
            files.add(file);
            for (var benchmark : RunReport.benchmarks(report)) {
                String id = benchmark.expectStringMember("id").getValue();
                samples.computeIfAbsent(id, k -> new ArrayList<>()).add(RunReport.number(benchmark, "ops_per_cpu_sec"));
                protocolOf.put(id, benchmark.expectStringMember("protocol").getValue());
                if (benchmark.expectObjectMember("verification").expectBooleanMember("under_warmed").getValue()) {
                    underWarmed++;
                }
            }
            cpuWallRatioMeans.add(RunReport.number(
                    report.expectObjectMember("summary").expectObjectMember("cpu_wall_ratio"),
                    "mean"));
        }

        double median(String id) {
            List<Double> values = new ArrayList<>(samples.get(id));
            Collections.sort(values);
            int n = values.size();
            return n % 2 == 1 ? values.get(n / 2) : (values.get(n / 2 - 1) + values.get(n / 2)) / 2;
        }

        String samplesPerBenchmark() {
            int min = Integer.MAX_VALUE;
            int max = 0;
            for (var values : samples.values()) {
                min = Math.min(min, values.size());
                max = Math.max(max, values.size());
            }
            return min == max ? Integer.toString(max) : min + "-" + max;
        }

        double cpuWallRatioMean() {
            double sum = 0;
            for (double v : cpuWallRatioMeans) {
                sum += v;
            }
            return RunReport.round(sum / cpuWallRatioMeans.size(), 4);
        }

        String metadata(String member) {
            return string(metadata, member);
        }

        Node metadataNode(String member) {
            return metadata.getMember(member).orElse(null);
        }

        String environment(String member) {
            return string(metadata.expectObjectMember("environment"), member);
        }

        String javaVersion() {
            return string(metadata.expectObjectMember("environment").expectObjectMember("java"), "version");
        }

        boolean hasXbatch() {
            return metadata.getBooleanMemberOrDefault("background_jit_compilation_disabled", false);
        }

        String date() {
            String started = metadata("run_started_utc");
            return started.length() >= 10 ? started.substring(0, 10) : started;
        }

        private static String string(ObjectNode node, String member) {
            return node.getMember(member).map(n -> {
                if (n.isStringNode()) {
                    return n.expectStringNode().getValue();
                } else if (n.isNumberNode()) {
                    return n.expectNumberNode().getValue().toString();
                } else if (n.isBooleanNode()) {
                    return Boolean.toString(n.expectBooleanNode().getValue());
                }
                return Node.printJson(n);
            }).orElse("unknown");
        }
    }
}
