/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.util.Collection;

/** Uses a geometric mean so the fastest benchmarks do not dominate the aggregate. */
final class GeometricMean {

    private GeometricMean() {}

    static double of(Collection<Double> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Cannot compute the geometric mean of no values");
        }
        double sum = 0;
        for (double value : values) {
            if (!(value > 0)) {
                throw new IllegalArgumentException("Geometric mean requires positive values, got " + value);
            }
            sum += Math.log(value);
        }
        return Math.exp(sum / values.size());
    }
}
