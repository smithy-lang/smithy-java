/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OpsPerCpuSecondProfilerTest {

    @Test
    void aggregatesOperationsAndCpuTimeIndependently() {
        var first = new OpsPerCpuSecondProfiler.OpsPerCpuSecondResult(1_000, 1_000_000_000);
        var second = new OpsPerCpuSecondProfiler.OpsPerCpuSecondResult(1_000, 3_000_000_000L);

        var aggregate = first.getIterationAggregator().aggregate(List.of(first, second));

        assertThat(aggregate.getScore()).isEqualTo(500);
    }
}
