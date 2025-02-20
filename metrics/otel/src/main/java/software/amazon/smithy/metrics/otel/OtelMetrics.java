/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.metrics.otel;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.java.metrics.api.Dimension;
import software.amazon.smithy.java.metrics.api.Metrics;
import software.amazon.smithy.java.metrics.api.MetricsReporter;
import software.amazon.smithy.java.metrics.api.Unit;

/**
 * Reports metrics to OTel.
 *
 * <p>The following methods of {@link Metrics} are reported as OTel {@link DoubleCounter}.
 *
 * <ul>
 *     <li>{@code Metrics#addCount}</li>
 * </ul>
 *
 * <p>The following methods of {@link Metrics} are reported as OTel {@link DoubleHistogram}.
 *
 * <ul>
 *     <li>{@code Metrics#addDate}</li>
 *     <li>{@code Metrics#addTime}</li>
 *     <li>{@code Metrics#addLevel}</li>
 *     <li>{@code Metrics#addRatio}</li>
 * </ul>
 *
 * <p>This class is thread-safe, though it does manage some internal state. Measurements of the same name and unit are
 * reused each time that name and unit is used. Properties added to a group are peristed and made available to the
 * {@link AttributesFactory} to decide if they should be OTel attributes.
 *
 * <p>Attributes associated with each measurement are decided using {@link AttributesFactory}. A default
 * AttributeFactory is used when one is not provided. The default AttributeFactory assigns an attribute named
 * "group" for the group if "group" is not empty, assigns an OTel attribute for each property of the group, and
 * assigns an OTel attribute for each {@link Dimension}.
 */
public class OtelMetrics implements MetricsReporter {

    private final AttributesFactory attributesFactory;
    private final Meter meter;

    private final Map<String, Map<String, String>> groupProperties = new ConcurrentHashMap<>();
    private final Map<CacheKey, DoubleHistogram> histogramCache = new ConcurrentHashMap<>();
    private final Map<CacheKey, DoubleCounter> doubleCounterCache = new ConcurrentHashMap<>();

    private record CacheKey(String name, String unit) {}

    private OtelMetrics(Builder builder) {
        this.meter = builder.meter;
        this.attributesFactory = builder.attributesFactory;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public void beginReport() {
        groupProperties.clear();
    }

    @Override
    public void close() {
        // Cleanup?
    }

    @Override
    public void addProperty(String group, String name, String value) {
        getGroupProperties(group).put(name, value);
    }

    private Map<String, String> getGroupProperties(String group) {
        return groupProperties.computeIfAbsent(group, k -> new ConcurrentHashMap<>());
    }

    private DoubleHistogram getHistogram(String name, String unit) {
        return histogramCache.computeIfAbsent(new CacheKey(name, unit),
                key -> meter.histogramBuilder(key.name).setUnit(key.unit).build());
    }

    private DoubleCounter getCounter(String name, String unit) {
        return doubleCounterCache.computeIfAbsent(new CacheKey(name, unit),
                key -> meter.counterBuilder(key.name).ofDoubles().setUnit(key.unit).build());
    }

    @Override
    public void addDate(String group, String name, double value) {
        Attributes attributes = attributesFactory.newAttributes(
                group,
                name,
                AttributesFactory.MeasurementKind.DATE,
                getGroupProperties(group));
        getHistogram(name, Unit.MILLISECOND).record(value, attributes);
    }

    @Override
    public void addCount(String group, String name, double value, String unit, int repeat) {
        Attributes attributes = attributesFactory.newAttributes(
                group,
                name,
                AttributesFactory.MeasurementKind.COUNT,
                getGroupProperties(group));
        var counter = getCounter(name, unit);
        for (int i = 0; i < repeat; i++) {
            counter.add(value, attributes);
        }
    }

    @Override
    public void addLevel(String group, String name, double value, String unit, int repeat) {
        Attributes attributes = attributesFactory.newAttributes(
                group,
                name,
                AttributesFactory.MeasurementKind.LEVEL,
                getGroupProperties(group));
        var histogram = getHistogram(name, unit);
        for (int i = 0; i < repeat; i++) {
            histogram.record(value, attributes);
        }
    }

    @Override
    public void addTime(String group, String name, Duration value, int repeat) {
        Attributes attributes = attributesFactory.newAttributes(
                group,
                name,
                AttributesFactory.MeasurementKind.TIME,
                getGroupProperties(group));
        double millis = value.toMillis();
        var histogram = getHistogram(name, Unit.MILLISECOND);
        for (var i = 1; i < repeat; i++) {
            histogram.record(millis, attributes);
        }
    }

    @Override
    public void addMetric(String group, String name, double value, int repeat, String unit, Dimension... dimensions) {
        Attributes attributes = attributesFactory.newAttributes(
                group,
                name,
                AttributesFactory.MeasurementKind.METRIC,
                getGroupProperties(group),
                dimensions);
        var histogram = getHistogram(name, unit);
        for (var i = 1; i < repeat; i++) {
            histogram.record(value, attributes);
        }
    }

    @Override
    public void addRatio(String group, String name, double ratio, int count, Dimension... dimensions) {
        Attributes attributes = attributesFactory.newAttributes(
                group,
                name,
                AttributesFactory.MeasurementKind.RATIO,
                getGroupProperties(group),
                dimensions);
        var ratioHistogram = getHistogram(name, Unit.COUNT);
        for (int i = 0; i < count; i++) {
            ratioHistogram.record(ratio, attributes);
        }
    }

    /**
     * Builds up a {@link OtelMetrics}.
     */
    public static final class Builder {
        private Meter meter;
        private AttributesFactory attributesFactory;

        private Builder() {}

        /**
         * Builds the reporter.
         *
         * @return the built reporter.
         * @throws NullPointerException if a {@link Meter} wasn't set.
         */
        public OtelMetrics build() {
            Objects.requireNonNull(meter, "Meter must not be null");
            if (attributesFactory == null) {
                attributesFactory = new DefaultAttributesFactory();
            }
            return new OtelMetrics(this);
        }

        /**
         * The required Meter to use when reporting metrics.
         *
         * @param meter The meter to use.
         * @return the builder.
         */
        public Builder meter(Meter meter) {
            this.meter = meter;
            return this;
        }

        /**
         * An optional factory used to create attributes instances.
         *
         * <p>If an attribute factory is not provided, {@link DefaultAttributesFactory} is used by default.
         *
         * @param attributesFactory Factory used to create attributes.
         * @return the builder.
         * @see DefaultAttributesFactory
         */
        public Builder attributesFactory(AttributesFactory attributesFactory) {
            this.attributesFactory = attributesFactory;
            return this;
        }
    }
}
