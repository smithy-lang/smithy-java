/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.metrics.api;

import java.time.Duration;

public final class NullMetricsReporter implements MetricsReporter {
    @Override
    public void beginReport() {}

    @Override
    public void close() {}

    @Override
    public void addProperty(String group, String name, String value) {}

    @Override
    public void addDate(String group, String name, double value) {}

    @Override
    public void addCount(String group, String name, double value, String unit, int repeat) {}

    @Override
    public void addLevel(String group, String name, double value, String unit, int repeat) {}

    @Override
    public void addTime(String group, String name, Duration value, int repeat) {}

    @Override
    public void addMetric(String group, String name, double value, int repeat, String unit, Dimension... dimensions) {}

    @Override
    public void addRatio(String group, String name, double ratio, int count, Dimension... dimensions) {}
}
