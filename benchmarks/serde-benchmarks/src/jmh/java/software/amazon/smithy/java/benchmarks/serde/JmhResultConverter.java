/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.serde;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 *     "instance": "<descriptor>",
 *     "precision": "-9"
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
 * <p>Two files are produced: {@code <output-prefix>.json} and {@code
 * <output-prefix>.md} (a rendered markdown table).
 *
 * <p>CLI usage:
 *
 * <pre>
 *   --input &lt;jmh-results.json&gt;  (required) JMH JSON output file
 *   --output-prefix &lt;path&gt;      (required) prefix for output .json and .md files
 *   --instance &lt;label&gt;          (optional) e.g. "m7i.xlarge"; default "unknown"
 *   --os &lt;label&gt;                (optional) free-form OS descriptor
 *   --software-version &lt;ver&gt;    (optional) override the smithy-java version label
 * </pre>
 */
public final class JmhResultConverter {

    private JmhResultConverter() {}

    public static void main(String[] args) throws IOException {
        Args parsed = Args.parse(args);
        convert(parsed);
    }

    static void convert(Args args) throws IOException {
        Node root = Node.parse(Files.readString(new File(args.input).toPath(), StandardCharsets.UTF_8));
        ObjectNode output = buildOutput(root, args);

        File outJson = new File(args.outputPrefix + ".json");
        File outMd = new File(args.outputPrefix + ".md");

        File parent = outJson.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        Files.writeString(outJson.toPath(), Node.prettyPrintJson(output), StandardCharsets.UTF_8);
        writeMarkdown(output, outMd);

        System.out.println("Wrote " + outJson.getAbsolutePath());
        System.out.println("Wrote " + outMd.getAbsolutePath());
    }

    static ObjectNode buildOutput(Node jmhResults, Args args) {
        return Node.objectNodeBuilder()
                .withMember("metadata", buildMetadata(args))
                .withMember("serde_benchmarks", buildEntries(jmhResults))
                .build();
    }

    private static ObjectNode buildMetadata(Args args) {
        ArrayNode software = ArrayNode.fromNodes(
                ArrayNode.fromStrings("smithy-java", args.softwareVersion));
        return Node.objectNodeBuilder()
                .withMember("lang", "Java")
                .withMember("software", software)
                .withMember("os", args.os)
                .withMember("instance", args.instance)
                .withMember("precision", "-9")
                .build();
    }

    private static ArrayNode buildEntries(Node jmhResults) {
        if (jmhResults == null || !jmhResults.isArrayNode()) {
            return Node.arrayNode();
        }
        // Dedupe on test case id — keep the first observation. JMH does not
        // produce duplicates in normal operation, but multiple `jmh` task runs
        // appended into the same file would.
        List<ObjectNode> entries = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Node element : jmhResults.expectArrayNode().getElements()) {
            if (!element.isObjectNode())
                continue;
            ObjectNode result = element.expectObjectNode();
            String id = extractTestCaseId(result);
            if (id == null || !seen.add(id)) {
                continue;
            }
            ObjectNode entry = convertSingleResult(result, id);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return ArrayNode.fromNodes(entries.toArray(new ObjectNode[0]));
    }

    private static ObjectNode convertSingleResult(ObjectNode result, String id) {
        ObjectNode primary = result.getObjectMember("primaryMetric").orElse(Node.objectNode());
        double mean = primary.getNumberMember("score").map(NumberNode::getValue).map(Number::doubleValue).orElse(0.0);
        double stdDev =
                primary.getNumberMember("scoreError").map(NumberNode::getValue).map(Number::doubleValue).orElse(0.0);

        ObjectNode percentiles = primary.getObjectMember("scorePercentiles").orElse(Node.objectNode());
        double p50 = percentiles.getNumberMember("50.0").map(NumberNode::getValue).map(Number::doubleValue).orElse(0.0);
        double p90 = percentiles.getNumberMember("90.0").map(NumberNode::getValue).map(Number::doubleValue).orElse(0.0);
        double p95 = percentiles.getNumberMember("95.0").map(NumberNode::getValue).map(Number::doubleValue).orElse(0.0);
        double p99 = percentiles.getNumberMember("99.0").map(NumberNode::getValue).map(Number::doubleValue).orElse(0.0);

        long n = computeTotalInvocations(primary.getMember("rawDataHistogram").orElse(null));

        return Node.objectNodeBuilder()
                .withMember("id", id)
                .withMember("n", n)
                .withMember("mean", mean)
                .withMember("p50", p50)
                .withMember("p90", p90)
                .withMember("p95", p95)
                .withMember("p99", p99)
                .withMember("std_dev", stdDev)
                .build();
    }

    /**
     * Sum invocation counts from JMH's nested {@code [fork][iteration] =
     * [[value, count], ...]} histogram. Returns 0 when the histogram is
     * absent (which happens for non-SampleTime modes).
     */
    private static long computeTotalInvocations(Node histogramNode) {
        if (histogramNode == null || !histogramNode.isArrayNode()) {
            return 0;
        }
        long total = 0;
        for (Node fork : histogramNode.expectArrayNode().getElements()) {
            if (!fork.isArrayNode())
                continue;
            for (Node iteration : fork.expectArrayNode().getElements()) {
                if (!iteration.isArrayNode())
                    continue;
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

    private static String extractTestCaseId(ObjectNode result) {
        return result.getObjectMember("params")
                .flatMap(p -> p.getStringMember("testCaseId"))
                .map(s -> s.getValue())
                .orElse(null);
    }

    private static void writeMarkdown(ObjectNode output, File file) throws IOException {
        ObjectNode metadata = output.getObjectMember("metadata").orElse(Node.objectNode());
        ArrayNode entries = output.getArrayMember("serde_benchmarks").orElse(Node.arrayNode());
        NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);

        try (PrintWriter pw = new PrintWriter(file, StandardCharsets.UTF_8)) {
            pw.println("# " + metadata.getStringMemberOrDefault("lang", "Java"));
            pw.println();
            String os = metadata.getStringMemberOrDefault("os", "");
            String instance = metadata.getStringMemberOrDefault("instance", "");
            pw.println("## " + (os + " " + instance).trim());
            pw.println();

            ArrayNode software = metadata.getArrayMember("software").orElse(Node.arrayNode());
            if (!software.isEmpty()) {
                pw.println("```");
                for (Node pair : software.getElements()) {
                    if (pair.isArrayNode()) {
                        var elems = pair.expectArrayNode().getElements();
                        if (elems.size() >= 2) {
                            pw.println(elems.get(0).expectStringNode().getValue()
                                    + " / "
                                    + elems.get(1).expectStringNode().getValue());
                        }
                    }
                }
                pw.println("```");
            }

            pw.println("|id|n|mean|p50|p90|p95|p99|std_dev|");
            pw.println("|----:|----:|----:|----:|----:|----:|----:|----:|");
            for (Node bm : entries.getElements()) {
                if (!bm.isObjectNode())
                    continue;
                ObjectNode entry = bm.expectObjectNode();
                pw.println("|" + entry.getStringMemberOrDefault("id", "")
                        + "|"
                        + nf.format(
                                entry.getNumberMember("n").map(NumberNode::getValue).map(Number::longValue).orElse(0L))
                        + "|"
                        + nf.format(Math.round(entry.getNumberMember("mean")
                                .map(NumberNode::getValue)
                                .map(Number::doubleValue)
                                .orElse(0.0)))
                        + "|"
                        + nf.format(Math.round(entry.getNumberMember("p50")
                                .map(NumberNode::getValue)
                                .map(Number::doubleValue)
                                .orElse(0.0)))
                        + "|"
                        + nf.format(Math.round(entry.getNumberMember("p90")
                                .map(NumberNode::getValue)
                                .map(Number::doubleValue)
                                .orElse(0.0)))
                        + "|"
                        + nf.format(Math.round(entry.getNumberMember("p95")
                                .map(NumberNode::getValue)
                                .map(Number::doubleValue)
                                .orElse(0.0)))
                        + "|"
                        + nf.format(Math.round(entry.getNumberMember("p99")
                                .map(NumberNode::getValue)
                                .map(Number::doubleValue)
                                .orElse(0.0)))
                        + "|"
                        + nf.format(Math.round(entry.getNumberMember("std_dev")
                                .map(NumberNode::getValue)
                                .map(Number::doubleValue)
                                .orElse(0.0)))
                        + "|");
            }
        }
    }

    /** Parsed CLI args. */
    static final class Args {
        String input;
        String outputPrefix;
        String instance = "unknown";
        String os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        String softwareVersion = readVersionResource();

        static Args parse(String[] argv) {
            Args a = new Args();
            for (int i = 0; i < argv.length; i++) {
                switch (argv[i]) {
                    case "--input" -> a.input = require(argv, ++i, "--input");
                    case "--output-prefix" -> a.outputPrefix = require(argv, ++i, "--output-prefix");
                    case "--instance" -> a.instance = require(argv, ++i, "--instance");
                    case "--os" -> a.os = require(argv, ++i, "--os");
                    case "--software-version" -> a.softwareVersion = require(argv, ++i, "--software-version");
                    default -> throw new IllegalArgumentException("Unknown argument: " + argv[i]);
                }
            }
            if (a.input == null || a.outputPrefix == null) {
                throw new IllegalArgumentException(
                        "Required arguments: --input <jmh.json> --output-prefix <path>");
            }
            return a;
        }

        private static String require(String[] argv, int idx, String flag) {
            if (idx >= argv.length) {
                throw new IllegalArgumentException("Missing value for " + flag);
            }
            return argv[idx];
        }

        /**
         * Read the smithy-java version from the {@code VERSION} resource if it
         * is on the classpath; otherwise return {@code "unknown"}.
         */
        private static String readVersionResource() {
            try (var in = JmhResultConverter.class.getResourceAsStream("/VERSION")) {
                if (in == null) {
                    return "unknown";
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                return "unknown";
            }
        }
    }
}
