/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.metrics.api;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * A simple reporter that logs metrics to a configurable {@link Consumer}.
 *
 * <p>The log message format of this class is not stable and may change over time. Do not rely on the output of
 * this reporter to remain the same over time.
 */
public final class LoggingMetricsReporter implements MetricsReporter {

    private final Consumer<String> logger;
    private final Map<String, String> properties = new TreeMap<>();

    /**
     * Create a {@link MetricsReporterFactory} used with {@link LoggingMetricsReporter}.
     *
     * @param logger The logging consumer to use.
     * @return the created factory.
     */
    public static MetricsReporterFactory createFactory(Consumer<String> logger) {
        return () -> new LoggingMetricsReporter(logger);
    }

    public LoggingMetricsReporter(Consumer<String> logger) {
        this.logger = logger;
    }

    @Override
    public void beginReport() {}

    @Override
    public void close() {}

    @Override
    public void addProperty(String group, String name, String value) {
        properties.put(Objects.requireNonNull(name), Objects.requireNonNull(value));
    }

    @Override
    public void addTime(String group, String name, Duration value, int repeat) {
        log(group, name, "time", value.toString(), "repeat=" + repeat, null);
    }

    @Override
    public void addRatio(String group, String name, double ratio, int count, Dimension... dimensions) {
        log(group, name, "ratio", String.valueOf(ratio), "count=" + count, null, dimensions);
    }

    @Override
    public void addMetric(String group, String name, double value, int repeat, String unit, Dimension... dimensions) {
        log(group, name, "metric", String.valueOf(value), "repeat=" + repeat, unit, dimensions);
    }

    @Override
    public void addLevel(String group, String name, double value, String unit, int repeat) {
        log(group, name, "level", String.valueOf(value), "repeat=" + repeat, unit);
    }

    @Override
    public void addDate(String group, String name, double value) {
        log(group, name, "time", String.valueOf(value), null, null);
    }

    @Override
    public void addCount(String group, String name, double value, String unit, int repeat) {
        log(group, name, "count", String.valueOf(value), "repeat=" + repeat, unit);
    }

    private void log(
            String group,
            String name,
            String kind,
            String value,
            String repeatOrCount,
            String unit,
            Dimension... dimensions
    ) {
        StringBuilder builder = new StringBuilder();
        builder
                .append("[group=")
                .append(group)
                .append(", name=")
                .append(name)
                .append(", kind=")
                .append(kind)
                .append(", value=")
                .append(value);

        if (repeatOrCount != null) {
            builder.append(", ").append(repeatOrCount);
        }

        if (unit != null) {
            builder.append(", ").append(unit);
        }

        if (dimensions != null) {
            for (Dimension dimension : dimensions) {
                builder.append(", ").append(dimension);
            }
        }

        if (!properties.isEmpty()) {
            builder.append(", properties={");
            for (var entry : properties.entrySet()) {
                builder.append(", ").append(entry.getKey()).append("=").append(entry.getValue()).append("|");
            }
            builder.deleteCharAt(builder.length() - 1);
            builder.append("}");
        }

        builder.append("\n");
        logger.accept(builder.toString());
    }
}
