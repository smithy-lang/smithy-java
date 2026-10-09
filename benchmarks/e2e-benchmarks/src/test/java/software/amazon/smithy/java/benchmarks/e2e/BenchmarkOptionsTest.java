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

        assertThat(options.transport()).isEqualTo(TransportMode.STUB);
        assertThat(options.endpoint()).isEqualTo(BenchmarkProtocol.ENDPOINT);
        assertThat(options.inProcess()).isFalse();
        assertThat(options.child()).isFalse();
        assertThat(options.selectIds()).isEqualTo(BenchmarkCases.canonicalIds());
        var settings = options.settings();
        assertThat(settings.minIterations()).isEqualTo(CpuTimeRunner.DEFAULT_MIN_ITERATIONS);
        assertThat(settings.minCpuSeconds()).isEqualTo(CpuTimeRunner.DEFAULT_MIN_CPU_SECONDS);
        assertThat(settings.checkInterval()).isEqualTo(CpuTimeRunner.DEFAULT_CHECK_INTERVAL);
        assertThat(settings.minMeasureCpuSeconds()).isEqualTo(CpuTimeRunner.DEFAULT_MIN_MEASURE_CPU_SECONDS);
        assertThat(settings.warmup().automatic()).isTrue();
    }

    @Test
    void selectsByProtocolAndFilter() {
        var options =
                BenchmarkOptions.parse(new String[] {"--protocol", "restXml,AwsQuery", "--filter", "getobject_s"});
        assertThat(options.selectIds()).containsExactly("restXml_GetObject_S");
    }

    @Test
    void childArgsReproduceTheRunForOneProtocol() {
        var options = BenchmarkOptions.parse(new String[] {
                "--filter",
                "GetItem",
                "--all-model-cases",
                "--min-measure-cpu-seconds",
                "2",
                "--transport",
                "https",
                "--endpoint",
                "https://127.0.0.1:9443",
                "--notes",
                "sample 1"});

        var child = BenchmarkOptions.parse(options.childArgs(
                BenchmarkProtocol.RPC_V2_CBOR,
                Path.of("child.json"),
                "m7i.metal-24xl").toArray(String[]::new));

        assertThat(child.child()).isTrue();
        assertThat(child.inProcess()).isTrue();
        assertThat(child.protocols()).containsExactly(BenchmarkProtocol.RPC_V2_CBOR);
        assertThat(child.filters()).containsExactly("getitem");
        assertThat(child.allModelCases()).isTrue();
        assertThat(child.settings().minMeasureCpuSeconds()).isEqualTo(2.0);
        assertThat(child.transport()).isEqualTo(TransportMode.HTTPS);
        assertThat(child.endpoint()).isEqualTo("https://127.0.0.1:9443");
        assertThat(child.instanceType()).isEqualTo("m7i.metal-24xl");
        assertThat(child.notes()).isEqualTo("sample 1");
        assertThat(child.output()).isEqualTo(Path.of("child.json"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--unknown", "--transport-impl"})
    void rejectsUnknownArguments(String flag) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BenchmarkOptions.parse(new String[] {flag, "x"}))
                .withMessageContaining("Unknown argument");
    }

    @Test
    void endpointRequiresANetworkTransport() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BenchmarkOptions.parse(new String[] {"--endpoint", "http://localhost:1"}))
                .withMessageContaining("--endpoint only applies");
        assertThat(BenchmarkOptions.parse(new String[] {"--transport", "http"}).endpoint())
                .isEqualTo("http://127.0.0.1:8080");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8443", "https:/localhost:8443", "/relative"})
    void httpsRequiresAnHttpsEndpoint(String endpoint) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BenchmarkOptions.parse(new String[] {
                        "--transport",
                        "https",
                        "--endpoint",
                        endpoint}))
                .withMessageContaining("absolute https URL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity"})
    void rejectsNonFiniteMeasurementWindows(String seconds) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BenchmarkOptions.parse(new String[] {"--min-measure-cpu-seconds", seconds}))
                .withMessageContaining("finite");
    }

    @Test
    void rejectsBadValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BenchmarkOptions.parse(new String[] {"--min-measure-cpu-seconds", "-1"}));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BenchmarkOptions.parse(new String[] {"--min-measure-cpu-seconds", "soon"}))
                .withMessageContaining("expects a number");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BenchmarkOptions.parse(new String[] {"--protocol"}))
                .withMessageContaining("requires a value");
        assertThat(List.of(BenchmarkOptions.usage().split("\n"))).noneMatch(l -> l.contains("--transport-impl"));
    }
}
