/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.util.Collection;

/**
 * Geometric mean computed in log space: the cross-SDK aggregation for ops/CPU-sec composites.
 *
 * <p>ops/CPU-sec spans orders of magnitude across payload sizes, and an arithmetic mean would let the fastest
 * benchmarks dominate.
 */
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
