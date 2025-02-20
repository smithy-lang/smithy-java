/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.metrics.api;

/**
 * A {@link MetricsFactory} that does not emit metrics.
 */
public final class NullMetricsFactory implements MetricsFactory {
    @Override
    public Metrics newMetrics() {
        return new NullMetrics(this);
    }
}
