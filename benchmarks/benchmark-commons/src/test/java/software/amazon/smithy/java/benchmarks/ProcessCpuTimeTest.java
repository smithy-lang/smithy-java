/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class ProcessCpuTimeTest {

    @Test
    void computesOperationsPerCpuSecond() {
        assertThat(ProcessCpuTime.operationsPerSecond(1_000, 500_000_000)).isEqualTo(2_000);
    }

    @Test
    void rejectsInvalidInputs() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProcessCpuTime.operationsPerSecond(-1, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ProcessCpuTime.operationsPerSecond(1, 0));
    }
}
