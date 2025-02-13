/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.metrics.api;

import java.util.Objects;

/**
 * Describes an immutable metric dimension to attach to a metric.
 *
 * <p>A dimension essentially a label that a metrics sink can index.
 */
public record Dimension(String metricClass, String instance) {
    public Dimension(String metricClass, String instance) {
        this.metricClass = Objects.requireNonNull(metricClass);
        this.instance = Objects.requireNonNull(instance);
    }
}
