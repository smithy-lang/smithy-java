/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.benchmarks.e2e.CpuTimeRunner.Settings;
import software.amazon.smithy.java.benchmarks.e2e.CpuTimeRunner.Warmup;

class CpuTimeRunnerTest {

    @Test
    void runsExactlyTheIterationFloorWhenTheCpuStopIsDisabled() throws Throwable {
        var calls = new AtomicLong();
        var runner = new CpuTimeRunner(new Settings(500, 0, 100, 0, Warmup.fixed(7)));

        var m = runner.run(calls::incrementAndGet);

        assertThat(m.iterations()).isEqualTo(500);
        assertThat(m.warmup().iterations()).isEqualTo(7);
        assertThat(m.warmup().automatic()).isFalse();
        assertThat(calls.get()).as("warmup calls plus measured calls").isEqualTo(507);
        assertThat(m.wallNanos()).isPositive();
        assertThat(m.processCpuNanos()).isNotNegative();
    }

    @Test
    void iterationFloorWinsWhenReachedFirst() throws Throwable {
        var runner = new CpuTimeRunner(new Settings(300, 60, 100, 0, Warmup.fixed(0)));
        var m = runner.run(() -> {});
        assertThat(m.iterations()).isEqualTo(300);
    }

    @Test
    void cpuFloorStopsASlowBenchmarkEarly() throws Throwable {
        var sink = new AtomicLong();
        var runner = new CpuTimeRunner(new Settings(Long.MAX_VALUE, 0.05, 100, 0, Warmup.fixed(0)));

        var m = runner.run(() -> {
            long x = sink.get();
            for (int i = 0; i < 20_000; i++) {
                x = x * 31 + i;
            }
            sink.set(x);
        });

        assertThat(m.iterations() % 100).as("stops on a check boundary").isZero();
        assertThat(m.processCpuNanos()).isGreaterThanOrEqualTo(40_000_000L);
        assertThat(m.opsPerCpuSecond()).isPositive();
    }

    @Test
    void cpuTimeFloorKeepsAFastBenchmarkRunning() throws Throwable {
        var runner = new CpuTimeRunner(new Settings(100, 0, 100, 0.05, Warmup.fixed(0)));
        var m = runner.run(() -> {});
        assertThat(m.iterations()).as("kept going past the iteration floor").isGreaterThan(100);
        assertThat(m.iterations() % 100).isZero();
        assertThat(m.processCpuNanos()).isGreaterThanOrEqualTo(40_000_000L);
    }

    @Test
    void automaticWarmupRespectsTheFloor() throws Throwable {
        var runner = new CpuTimeRunner(new Settings(100, 0, 100, 0, Warmup.auto()));
        var m = runner.run(() -> {});
        assertThat(m.warmup().automatic()).isTrue();
        assertThat(m.warmup().iterations()).isGreaterThanOrEqualTo(Warmup.AUTO_FLOOR);
        assertThat(m.warmup().iterations()).isLessThanOrEqualTo(Warmup.AUTO_CAP);
    }

    @Test
    void standardSettingsAreTheCrossSdkRuleWithTheOneSecondFloor() {
        var standard = Settings.standard();
        assertThat(standard.minIterations()).isEqualTo(50_000);
        assertThat(standard.minCpuSeconds()).isEqualTo(5.0);
        assertThat(standard.checkInterval()).isEqualTo(100);
        assertThat(standard.minMeasureCpuSeconds()).isEqualTo(1.0);
        assertThat(standard.warmup().automatic()).isTrue();
        assertThat(standard.stopCondition())
                .isEqualTo(
                        "min 50000 iterations OR 5 seconds CPU time (first met wins) and at least 1 second(s) of CPU time");
        assertThat(new Settings(50_000, 5, 100, 0, Warmup.auto()).stopCondition())
                .isEqualTo("min 50000 iterations OR 5 seconds CPU time (first met wins)");
        assertThat(new Settings(50_000, 0, 100, 0, Warmup.auto()).stopCondition())
                .isEqualTo("fixed 50000 iterations for every benchmark, no time-based stop");
        assertThat(new Settings(1000, 2.5, 100, 0, Warmup.auto()).stopCondition())
                .isEqualTo("min 1000 iterations OR 2.5 seconds CPU time (first met wins)");
    }

    @Test
    void rejectsInvalidSettings() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Settings(0, 1, 100, 0, Warmup.auto()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Settings(10, -1, 100, 0, Warmup.auto()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Settings(10, 1, 0, 0, Warmup.auto()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Settings(10, 1, 100, -1, Warmup.auto()));
        assertThatIllegalArgumentException().isThrownBy(() -> Warmup.fixed(-1));
    }
}
