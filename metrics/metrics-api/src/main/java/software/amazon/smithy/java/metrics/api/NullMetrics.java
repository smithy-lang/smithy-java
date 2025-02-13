/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.metrics.api;

import java.time.Duration;

final class NullMetrics implements Metrics {

    private final NullMetricsFactory factory;

    NullMetrics(NullMetricsFactory factory) {
        this.factory = factory;
    }

    @Override
    public Metrics newMetrics(String group) {
        return new NullMetrics(factory);
    }

    @Override
    public void close() {}

    @Override
    public void addProperty(String name, String value) {}

    @Override
    public void addDate(String name, double value) {}

    @Override
    public void addCount(String name, double value, String unit, int repeat) {}

    @Override
    public void addLevel(String name, double value, String unit, int repeat) {}

    @Override
    public void addTime(String name, Duration time, int repeat) {}

    @Override
    public void addMetric(String name, double value, String unit, Dimension... dimensions) {}

    @Override
    public void addMetric(String name, double value, int repeat, String unit, Dimension... dimensions) {}

    @Override
    public void addRatio(String name, double ratio, int count, Dimension... dimensions) {}
}
