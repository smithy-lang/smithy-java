/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BenchmarkOptionsTest {

    @Test
    void defaultsAreTheCrossSdkConfiguration() {
        var options = BenchmarkOptions.parse(new String[0]);

        assertThat(options.mode()).isEqualTo(Mode.STUB);
        assertThat(options.selectIds()).isEqualTo(BenchmarkCases.canonicalIds());
        assertThat(options.allModelCases()).isFalse();
        assertThat(options.list()).isFalse();
        assertThat(options.output().toString()).startsWith("e2e-ops-cpusec-").endsWith(".json");
        assertThat(options.instanceType()).isNull();
        assertThat(options.notes()).isNull();
    }

    @Test
    void selectsByProtocolAndFilter() {
        var options =
                BenchmarkOptions.parse(new String[] {"--protocol", "restXml,AwsQuery", "--filter", "getobject_s"});
        assertThat(options.selectIds()).containsExactly("restXml_GetObject_S");
    }

    @Test
    void parsesEveryRemainingOption() {
        var options = BenchmarkOptions.parse(new String[] {
                "--mode",
                "HTTPS",
                "--all-model-cases",
                "--output",
                "out.json",
                "--instance-type",
                "m7i.metal-24xl",
                "--notes",
                "sample 1",
                "--list"});

        assertThat(options.mode()).isEqualTo(Mode.HTTPS);
        assertThat(options.allModelCases()).isTrue();
        assertThat(options.selectIds()).isEqualTo(BenchmarkCases.allIds());
        assertThat(options.output()).isEqualTo(Path.of("out.json"));
        assertThat(options.instanceType()).isEqualTo("m7i.metal-24xl");
        assertThat(options.notes()).isEqualTo("sample 1");
        assertThat(options.list()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"--unknown",
            "--transport",
            "--endpoint",
            "--in-process",
            "--child",
            "--min-measure-cpu-seconds"})
    void rejectsUnknownAndRemovedArguments(String flag) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BenchmarkOptions.parse(new String[] {flag, "x"}))
                .withMessageContaining("Unknown argument");
    }

    @Test
    void rejectsBadValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BenchmarkOptions.parse(new String[] {"--mode", "http"}))
                .withMessageContaining("Expected stub or https");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BenchmarkOptions.parse(new String[] {"--protocol"}))
                .withMessageContaining("requires a value");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BenchmarkOptions.parse(new String[] {"--protocol", "soap"}))
                .withMessageContaining("Unknown protocol");
    }

    @Test
    void usageDocumentsOnlyTheRemainingOptions() {
        List<String> lines = List.of(BenchmarkOptions.usage().split("\n"));
        assertThat(lines).anyMatch(l -> l.contains("--mode MODE"));
        assertThat(lines).noneMatch(l -> l.contains("--transport") || l.contains("--in-process")
                || l.contains("--endpoint")
                || l.contains("--min-measure-cpu-seconds"));
    }
}
