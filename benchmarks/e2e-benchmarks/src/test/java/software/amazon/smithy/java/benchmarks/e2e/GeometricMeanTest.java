/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class GeometricMeanTest {

    @Test
    void weightsRatiosNotMagnitudes() {
        assertThat(GeometricMean.of(List.of(10.0, 1000.0))).isCloseTo(100.0, within(1e-9));
        assertThat(GeometricMean.of(List.of(15_000.0, 150.0))).isCloseTo(1_500.0, within(1e-9));
        assertThat(GeometricMean.of(List.of(42.0))).isCloseTo(42.0, within(1e-9));
    }

    @Test
    void rejectsEmptyAndNonPositiveInput() {
        assertThatIllegalArgumentException().isThrownBy(() -> GeometricMean.of(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> GeometricMean.of(List.of(1.0, 0.0)));
        assertThatIllegalArgumentException().isThrownBy(() -> GeometricMean.of(List.of(-1.0)));
    }
}
