/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import software.amazon.smithy.java.core.Version;
import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.NumberNode;
import software.amazon.smithy.model.node.ObjectNode;

/**
 * Converts a JMH JSON result file (produced by {@code jmh -rf json -rff
 * results.json} or by the gradle plugin via {@code resultFormat = "json"})
 * into the cross-language serde benchmark output schema.
 *
 * <p>The output schema is:
 *
 * <pre>{@code
 * {
 *   "metadata": {
 *     "lang": "Java",
 *     "software": [["smithy-java", "<version>"]],
 *     "os": "<descriptor>",
 *     "instance": "<descriptor or EC2 instance type>",
 *     "precision": "-9",
 *     "jvm": {
 *       "id": "<vm name> <vm version>",
 *       "args": "<non-system-property JVM arguments>"
 *     }
 *   },
 *   "serde_benchmarks": [
 *     {
 *       "id": "<test case id>",
 *       "n": <iterations recorded>,
 *       "mean": <ns>,
 *       "p50": <ns>,
 *       "p90": <ns>,
 *       "p95": <ns>,
 *       "p99": <ns>,
 *       "std_dev": <ns>
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>The {@code instance} field is auto-detected: on EC2 it queries IMDSv2
 * for the instance type (e.g. {@code "m7i.xlarge"}); on macOS it reports the
 * chip name via {@code sysctl}; otherwise it reports cores and RAM.
 *
 * <p>Two files are produced: {@code <output-prefix>.json} and {@code
 * <output-prefix>.md} (a rendered markdown table).
 *
 * <p>Called from the Gradle build's {@code jmh} task via {@code doLast}.
 */
public final class JmhResultConverter {

    private JmhResultConverter() {}

    static void main(String[] args) throws IOException {
        String input = null, outputPrefix = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input" -> input = args[++i];
                case "--output-prefix" -> outputPrefix = args[++i];
                default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        if (input == null || outputPrefix == null) {
            throw new IllegalArgumentException("Required: --input <jmh.json> --output-prefix <path>");
        }

        convert(Path.of(input), Path.of(outputPrefix));
    }

    public static void convert(Path jmhResultsPath, Path outputPrefix) throws IOException {
        String os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        String instance = detectMachineInfo();
        String softwareVersion = Version.VERSION;
        Node root = Node.parse(Files.readString(jmhResultsPath, StandardCharsets.UTF_8));
        ObjectNode output = buildOutput(root, instance, os, softwareVersion);

        Path outJson = Path.of(outputPrefix + ".json");
        Path outMd = Path.of(outputPrefix + ".md");
        Files.createDirectories(outJson.getParent());

        Files.writeString(outJson, Node.prettyPrintJson(output), StandardCharsets.UTF_8);
        writeMarkdown(output, outMd);
    }

    private static ObjectNode buildOutput(Node jmhResults, String instance, String os, String softwareVersion) {
        var metadata = Node.objectNodeBuilder()
                .withMember("lang", "Java")
                .withMember("software", ArrayNode.fromNodes(ArrayNode.fromStrings("smithy-java", softwareVersion)))
                .withMember("os", os)
                .withMember("instance", instance)
                .withMember("precision", "-9")
                .withMember("jvm", buildJvmInfo(jmhResults))
                .build();

        return Node.objectNodeBuilder()
                .withMember("metadata", metadata)
                .withMember("serde_benchmarks", buildEntries(jmhResults))
                .build();
    }

    private static ObjectNode buildJvmInfo(Node jmhResults) {
        // Extract JVM details from the first JMH result entry rather than
        // the converter's own process, so values are correct when the
        // converter runs on a different JVM than the benchmarks.
        var firstResult = firstJmhResult(jmhResults);

        String vmName = firstResult.getStringMemberOrDefault("vmName", "unknown");
        String vmVersion = firstResult.getStringMemberOrDefault("vmVersion", "unknown");
        String id = vmName + " " + vmVersion;

        List<String> jvmArgsList = firstResult.getArrayMember("jvmArgs")
                .map(arr -> arr.getElements()
                        .stream()
                        .filter(Node::isStringNode)
                        .map(n -> n.expectStringNode().getValue())
                        .toList())
                .orElse(List.of());

        String args = jvmArgsList.stream()
                .filter(s -> !s.startsWith("-D"))
                .collect(Collectors.joining(" "));

        return Node.objectNodeBuilder()
                .withMember("id", id)
                .withMember("args", args)
                .build();
    }

    private static ObjectNode firstJmhResult(Node jmhResults) {
        if (jmhResults != null && jmhResults.isArrayNode()) {
            var elements = jmhResults.expectArrayNode().getElements();
            if (!elements.isEmpty() && elements.getFirst().isObjectNode()) {
                return elements.getFirst().expectObjectNode();
            }
        }
        return Node.objectNode();
    }

    private static ArrayNode buildEntries(Node jmhResults) {
        if (jmhResults == null || !jmhResults.isArrayNode()) {
            return Node.arrayNode();
        }
        var seen = new LinkedHashSet<String>();
        List<ObjectNode> entries = new ArrayList<>();
        for (Node element : jmhResults.expectArrayNode().getElements()) {
            if (!element.isObjectNode()) {
                continue;
            }
            var result = element.expectObjectNode();
            String id = result.getObjectMember("params")
                    .flatMap(p -> p.getStringMember("testCaseId"))
                    .map(s -> s.getValue())
                    .orElse(null);
            if (id == null || !seen.add(id)) {
                continue;
            }

            var primary = result.getObjectMember("primaryMetric").orElse(Node.objectNode());
            var percentiles = primary.getObjectMember("scorePercentiles").orElse(Node.objectNode());

            entries.add(Node.objectNodeBuilder()
                    .withMember("id", id)
                    .withMember("n", countSamples(primary.getMember("rawDataHistogram").orElse(null)))
                    .withMember("mean", doubleOf(primary, "score"))
                    .withMember("p50", doubleOf(percentiles, "50.0"))
                    .withMember("p90", doubleOf(percentiles, "90.0"))
                    .withMember("p95", doubleOf(percentiles, "95.0"))
                    .withMember("p99", doubleOf(percentiles, "99.0"))
                    .withMember("std_dev", doubleOf(primary, "scoreError"))
                    .build());
        }
        return ArrayNode.fromNodes(entries.toArray(new ObjectNode[0]));
    }

    private static double doubleOf(ObjectNode node, String key) {
        return node.getNumberMember(key).map(NumberNode::getValue).map(Number::doubleValue).orElse(0.0);
    }

    private static long countSamples(Node histogramNode) {
        if (histogramNode == null || !histogramNode.isArrayNode()) {
            return 0;
        }
        long total = 0;
        for (Node fork : histogramNode.expectArrayNode().getElements()) {
            if (!fork.isArrayNode()) {
                continue;
            }
            for (Node iteration : fork.expectArrayNode().getElements()) {
                if (!iteration.isArrayNode()) {
                    continue;
                }
                for (Node bin : iteration.expectArrayNode().getElements()) {
                    if (bin.isArrayNode()) {
                        var elems = bin.expectArrayNode().getElements();
                        if (elems.size() >= 2 && elems.get(1).isNumberNode()) {
                            total += elems.get(1).expectNumberNode().getValue().longValue();
                        }
                    }
                }
            }
        }
        return total;
    }

    private static void writeMarkdown(ObjectNode output, Path file) throws IOException {
        var metadata = output.getObjectMember("metadata").orElse(Node.objectNode());
        var entries = output.getArrayMember("serde_benchmarks").orElse(Node.arrayNode());
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);

        try (var pw = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            pw.println("# " + metadata.getStringMemberOrDefault("lang", "Java"));
            pw.println();
            pw.println("## " + (metadata.getStringMemberOrDefault("os", "")
                    + " " + metadata.getStringMemberOrDefault("instance", "")).trim());
            pw.println();

            pw.println("```");
            var software = metadata.getArrayMember("software").orElse(Node.arrayNode());
            for (Node pair : software.getElements()) {
                if (pair.isArrayNode()) {
                    var elems = pair.expectArrayNode().getElements();
                    if (elems.size() >= 2) {
                        pw.println(elems.get(0).expectStringNode().getValue()
                                + " / " + elems.get(1).expectStringNode().getValue());
                    }
                }
            }
            var jvm = metadata.getObjectMember("jvm").orElse(Node.objectNode());
            pw.println("JVM: " + jvm.getStringMemberOrDefault("id", ""));
            pw.println("Args: " + jvm.getStringMemberOrDefault("args", ""));
            pw.println("```");

            pw.println("|id|n|mean|p50|p90|p95|p99|std_dev|");
            pw.println("|----:|----:|----:|----:|----:|----:|----:|----:|");
            for (Node bm : entries.getElements()) {
                if (!bm.isObjectNode()) {
                    continue;
                }
                var entry = bm.expectObjectNode();
                pw.println("|" + entry.getStringMemberOrDefault("id", "")
                        + "|" + nf.format(longOf(entry, "n"))
                        + "|" + nf.format(Math.round(doubleOf(entry, "mean")))
                        + "|" + nf.format(Math.round(doubleOf(entry, "p50")))
                        + "|" + nf.format(Math.round(doubleOf(entry, "p90")))
                        + "|" + nf.format(Math.round(doubleOf(entry, "p95")))
                        + "|" + nf.format(Math.round(doubleOf(entry, "p99")))
                        + "|" + nf.format(Math.round(doubleOf(entry, "std_dev")))
                        + "|");
            }
        }
    }

    private static long longOf(ObjectNode node, String key) {
        return node.getNumberMember(key).map(NumberNode::getValue).map(Number::longValue).orElse(0L);
    }

    private static String detectMachineInfo() {
        // Try EC2 IMDS first
        String ec2Type = queryEc2InstanceType();
        if (ec2Type != null) {
            return ec2Type;
        }

        // Fall back to cores + RAM summary
        int cores = Runtime.getRuntime().availableProcessors();
        long ramMb = ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os
                ? os.getTotalMemorySize() / (1024 * 1024)
                : Runtime.getRuntime().maxMemory() / (1024 * 1024);
        String ramLabel = ramMb >= 1024 ? (ramMb / 1024) + "GB" : ramMb + "MB";

        // On macOS, try to get the chip name (e.g. "Apple M3 Max")
        String chip = detectMacChip();
        if (chip != null) {
            return chip + " (" + cores + " cores, " + ramLabel + " RAM)";
        }

        return cores + " cores, " + ramLabel + " RAM";
    }

    private static String queryEc2InstanceType() {
        try (var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1))
                .build()) {
            var tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://169.254.169.254/latest/api/token"))
                    .header("X-aws-ec2-metadata-token-ttl-seconds", "21600")
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(1))
                    .build();
            var tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            String token = tokenResponse.body().trim();

            var metadataRequest = HttpRequest.newBuilder()
                    .uri(URI.create("http://169.254.169.254/latest/meta-data/instance-type"))
                    .header("X-aws-ec2-metadata-token", token)
                    .timeout(Duration.ofSeconds(1))
                    .build();
            var metadataResponse = client.send(metadataRequest, HttpResponse.BodyHandlers.ofString());
            if (metadataResponse.statusCode() == 200) {
                return metadataResponse.body().trim();
            }
        } catch (Exception _) {
            // Not on EC2 or IMDS unavailable
        }
        return null;
    }

    private static String detectMacChip() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac")) {
            return null;
        }
        try {
            var proc = new ProcessBuilder("sysctl", "-n", "machdep.cpu.brand_string")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (proc.waitFor() == 0 && !output.isEmpty()) {
                return output;
            }
        } catch (Exception _) {
            // sysctl not available
        }
        return null;
    }
}
