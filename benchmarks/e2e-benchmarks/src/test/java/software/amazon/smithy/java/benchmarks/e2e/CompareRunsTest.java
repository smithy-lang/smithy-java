/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;

class CompareRunsTest {

    @TempDir
    Path tmp;

    @Test
    void comparesTwoRunsAndWritesTheCrossSdkFiles() throws Exception {
        Path baseline = write("baseline.json",
                report(Map.of(
                        "awsJson1_0_GetItemOutput_S",
                        10_000.0,
                        "awsJson1_0_PutItemRequest_Baseline",
                        20_000.0,
                        "restXml_GetObject_S",
                        8_000.0), "aaaaaaa"));
        Path current = write("current.json",
                report(Map.of(
                        "awsJson1_0_GetItemOutput_S",
                        12_500.0,
                        "awsJson1_0_PutItemRequest_Baseline",
                        20_000.0,
                        "restXml_GetObject_S",
                        10_000.0), "bbbbbbb"));
        Path out = tmp.resolve("results/smithy-java/test_ocs_results");

        int exit = CompareRuns.run(new String[] {
                "--baseline",
                baseline.toString(),
                "--current",
                current.toString(),
                "--out",
                out.toString()});

        assertThat(exit).isZero();
        var json = Node.parse(Files.readString(Path.of(out + ".json"))).expectObjectNode();
        var benchmarks = json.expectArrayMember("benchmarks").getElements();
        assertThat(benchmarks).hasSize(3);
        var first = benchmarks.get(0).expectObjectNode();
        // Canonical order puts the PutItem request after the GetItem outputs.
        assertThat(first.expectStringMember("id").getValue()).isEqualTo("awsJson1_0_GetItemOutput_S");
        assertThat(first.expectNumberMember("baseline").getValue().longValue()).isEqualTo(10_000);
        assertThat(first.expectNumberMember("current").getValue().longValue()).isEqualTo(12_500);
        assertThat(first.expectNumberMember("improvement_pct").getValue().doubleValue()).isCloseTo(25.0, within(1e-9));

        var summary = json.expectObjectMember("summary");
        var awsJson = summary.expectObjectMember("protocols").expectObjectMember("AwsJson10");
        assertThat(awsJson.expectNumberMember("benchmark_count").getValue().intValue()).isEqualTo(2);
        assertThat(awsJson.expectNumberMember("baseline").getValue().longValue())
                .isEqualTo(Math.round(Math.sqrt(10_000.0 * 20_000.0)));
        var overall = summary.expectObjectMember("overall");
        assertThat(overall.expectNumberMember("benchmark_count").getValue().intValue()).isEqualTo(3);
        assertThat(overall.expectStringMember("aggregation").getValue()).isEqualTo("geometric_mean");

        var metadata = json.expectObjectMember("metadata");
        assertThat(metadata.expectStringMember("lang").getValue()).isEqualTo("smithy-java");
        assertThat(metadata.expectStringMember("baseline_commit").getValue()).isEqualTo("aaaaaaa");
        assertThat(metadata.expectStringMember("current_commit").getValue()).isEqualTo("bbbbbbb");
        assertThat(metadata.expectStringMember("stop_condition").getValue())
                .isEqualTo(
                        "min 50000 iterations OR 5 seconds CPU time (first met wins) and at least 1 second(s) of CPU time");

        String md = Files.readString(Path.of(out + ".md"), StandardCharsets.UTF_8);
        assertThat(md).startsWith("# smithy-java — Ops/CPU-Sec E2E Results");
        assertThat(md).contains("| awsJson1_0_GetItemOutput_S | 10,000 | 12,500 | +25% |");
        assertThat(md).contains("### Protocol Summary (geometric mean)");
        assertThat(md).contains("| **Overall** |");
    }

    @Test
    void directoriesAreMergedAndRepeatedSamplesUseTheMedian() throws Exception {
        Path baselineDir = tmp.resolve("baseline");
        Path currentDir = tmp.resolve("current");
        Files.createDirectories(baselineDir);
        Files.createDirectories(currentDir);
        write(baselineDir.resolve("run1.json"), report(Map.of("restXml_GetObject_S", 1_000.0), "a"));
        write(currentDir.resolve("run1.json"), report(Map.of("restXml_GetObject_S", 1_100.0), "b"));
        write(currentDir.resolve("run2.json"), report(Map.of("restXml_GetObject_S", 1_900.0), "b"));
        write(currentDir.resolve("run3.json"), report(Map.of("restXml_GetObject_S", 1_200.0), "b"));
        Path out = tmp.resolve("merged");

        int exit = CompareRuns.run(new String[] {
                "--baseline",
                baselineDir.toString(),
                "--current",
                currentDir.toString(),
                "--out",
                out.toString()});

        assertThat(exit).isZero();
        var json = Node.parse(Files.readString(Path.of(out + ".json"))).expectObjectNode();
        var entry = json.expectArrayMember("benchmarks").getElements().get(0).expectObjectNode();
        assertThat(entry.expectNumberMember("current").getValue().longValue()).as("median of 1100, 1900, 1200")
                .isEqualTo(1_200);
        assertThat(entry.expectNumberMember("improvement_pct").getValue().doubleValue()).isCloseTo(20.0, within(1e-9));
        assertThat(json.expectObjectMember("metadata").expectNumberMember("current_runs").getValue().intValue())
                .isEqualTo(3);
    }

    @Test
    void differentBenchmarkSetsAreAnErrorUnlessPartialIsAllowed() throws Exception {
        Path baseline = write("baseline.json",
                report(Map.of(
                        "restXml_GetObject_S",
                        1_000.0,
                        "restXml_GetObject_M",
                        900.0), "a"));
        Path current = write("current.json", report(Map.of("restXml_GetObject_S", 1_100.0), "b"));
        Path out = tmp.resolve("partial");

        assertThat(CompareRuns.run(new String[] {
                "--baseline",
                baseline.toString(),
                "--current",
                current.toString(),
                "--out",
                out.toString()})).isEqualTo(2);
        assertThat(Files.exists(Path.of(out + ".json"))).isFalse();

        assertThat(CompareRuns.run(new String[] {
                "--baseline",
                baseline.toString(),
                "--current",
                current.toString(),
                "--out",
                out.toString(),
                "--allow-partial"})).isZero();
        var json = Node.parse(Files.readString(Path.of(out + ".json"))).expectObjectNode();
        assertThat(json.expectArrayMember("benchmarks").getElements()).hasSize(1);
        assertThat(json.expectObjectMember("metadata").expectArrayMember("comparability_warnings").getElements())
                .anySatisfy(w -> assertThat(w.expectStringNode().getValue()).contains("Benchmark sets differ"));
    }

    @Test
    void differentStopConditionsAreNotComparable() throws Exception {
        Path baseline = write("baseline.json", report(Map.of("restXml_GetObject_S", 1_000.0), "a"));
        var options = BenchmarkOptions.parse(
                new String[] {"--min-measure-cpu-seconds", "2", "--instance-type", "test"});
        Path current = write("current.json", report(options, Map.of("restXml_GetObject_S", 1_100.0), "b"));

        assertThat(CompareRuns.run(new String[] {
                "--baseline",
                baseline.toString(),
                "--current",
                current.toString(),
                "--out",
                tmp.resolve("x").toString()})).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"transport", "client", "commit", "background_jit_compilation_disabled", "environment"})
    void rejectsMixedSamplesWithinOneSide(String member) throws Exception {
        Path directory = tmp.resolve("mixed");
        Files.createDirectories(directory);
        var first = report(Map.of("restXml_GetObject_S", 1_000.0), "a");
        write(directory.resolve("run1.json"), first);
        var changedMetadata = first.expectObjectMember("metadata").toBuilder().withMember(member, "different").build();
        write(directory.resolve("run2.json"), first.toBuilder().withMember("metadata", changedMetadata).build());

        assertThatThrownBy(() -> CompareRuns.Side.load(directory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different " + member);
    }

    @Test
    void formatsPercentagesLikeTheSharedMarkdownScript() {
        assertThat(CompareRuns.formatPct(3.05)).isEqualTo("+3.05%");
        assertThat(CompareRuns.formatPct(13.5)).isEqualTo("+13.5%");
        assertThat(CompareRuns.formatPct(-1.64)).isEqualTo("−1.64%");
        assertThat(CompareRuns.formatPct(0)).isEqualTo("+0%");
        assertThat(CompareRuns.improvementPct(9_389, 13_059)).isCloseTo(39.09, within(1e-9));
    }

    private Path write(String name, ObjectNode report) throws Exception {
        return write(tmp.resolve(name), report);
    }

    private static Path write(Path path, ObjectNode report) throws Exception {
        Files.writeString(path, Node.prettyPrintJson(report));
        return path;
    }

    private static ObjectNode report(Map<String, Double> values, String commit) {
        return report(BenchmarkOptions.parse(new String[] {"--instance-type", "test-instance"}), values, commit);
    }

    /** A single-run results file with the shape RunReport writes, without running anything. */
    private static ObjectNode report(BenchmarkOptions options, Map<String, Double> values, String commit) {
        List<ObjectNode> benchmarks = new ArrayList<>();
        for (var entry : values.entrySet()) {
            var protocol = BenchmarkProtocol.forBenchmarkId(entry.getKey());
            benchmarks.add(Node.objectNodeBuilder()
                    .withMember("id", entry.getKey())
                    .withMember("protocol", protocol.summaryName())
                    .withMember("operation", "Test")
                    .withMember("source", "request")
                    .withMember("iterations", 50_000)
                    .withMember("ops_per_cpu_sec", entry.getValue())
                    .withMember("cpu_wall_ratio", 1.0)
                    .withMember("warmup",
                            Node.objectNodeBuilder()
                                    .withMember("iterations", 20_000)
                                    .withMember("automatic", true)
                                    .withMember("hit_cap", false)
                                    .build())
                    .withMember("verification",
                            Node.objectNodeBuilder()
                                    .withMember("under_warmed", false)
                                    .withMember("http_requests_match_iterations", true)
                                    .build())
                    .build());
        }
        var environment = Environment.capture("test-instance")
                .toBuilder()
                .withMember("commit", commit)
                .build();
        var metadata = RunReport.metadata(
                options,
                options.settings(),
                environment,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-01T00:05:00Z"),
                true,
                true,
                benchmarks.size(),
                "test transport");
        return RunReport.report(metadata, benchmarks);
    }
}
