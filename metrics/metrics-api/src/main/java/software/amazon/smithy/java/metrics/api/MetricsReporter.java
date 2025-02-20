/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.metrics.api;

import java.time.Duration;

/**
 * A reporter is used to aggregate and transmit {@link Metrics}.
 */
public interface MetricsReporter extends AutoCloseable {
    /**
     * Starts report aggregation.
     *
     * <p>Call {@link #close()} to finish the report.
     */
    void beginReport();

    /**
     * Finishes the report.
     */
    @Override
    void close();

    /**
     * Add a property to the group of <tt>Metrics Primitives</tt>.
     *
     * @param group The metrics group.
     * @param name Attribute name
     * @param value Attribute value
     * 
     * @see Metrics#addProperty(String, String) 
     */
    void addProperty(String group, String name, String value);

    /**
     * Report a date to the group of <tt>Metrics Primitives</tt>.
     *
     * @param group The metrics group.
     * @param name date name
     * @param value date value in milliseconds since epoch.
     * 
     * @see Metrics#addDate(String, double) 
     */
    void addDate(String group, String name, double value);

    /**
     * Increment a count primitive by <em>value</em>, setting it equal to <em>value</em> if
     * the named count had not existed yet.
     *
     * @param group The metrics group.
     * @param name count name
     * @param value count increment
     * @param unit the unit to affix, based on Unified Code for Units of Measure. See {@link Unit} for common values.
     * @param repeat the number of samples from which <em>value</em> was accumulated
     *
     * @see Metrics#addCount(String, double, String, int) 
     */
    void addCount(String group, String name, double value, String unit, int repeat);

    /**
     * Update a reported level primitive by factoring <em>value</em> into the running average associated with the named
     * level.
     *
     * @param group The metrics group.
     * @param name level name
     * @param value level value
     * @param unit the unit to affix, based on Unified Code for Units of Measure. See {@link Unit} for common values.
     * @param repeat the number of samples from which <em>value</em> was accumulated
     *
     * @see Metrics#addLevel(String, double, String, int)
     */
    void addLevel(String group, String name, double value, String unit, int repeat);

    /**
     * Increment a reported time primitive by <em>value</em>, setting it equal to <em>value</em> if the named time
     * had not existed yet.
     *
     * @param group The metrics group.
     * @param name count name
     * @param value time duration
     * @param repeat the number of samples from which <em>value</em> was accumulated
     *
     * @see Metrics#addTime(String, Duration, int) 
     */
    void addTime(String group, String name, Duration value, int repeat);

    /**
     * Report a single metric entry.
     *
     * @param group The metrics group.
     * @param name metric name
     * @param value metric value
     * @param repeat number of instances of this metric to record (e.g., 1 means a single repetition).
     * @param unit the unit to affix, based on Unified Code for Units of Measure. See {@link Unit} for common values.
     * @param dimensions list of relevant dimensions
     *
     * @see Metrics#addMetric(String, double, int, String, Dimension...) 
     */
    void addMetric(String group, String name, double value, int repeat, String unit, Dimension... dimensions);

    /**
     * Report a metric ratio.
     *
     * @param group The metrics group.
     * @param name count name
     * @param ratio A value between 0 to 1,representing the percent ratio
     * @param count total count
     * @param dimensions list of relevant dimensions
     *
     * @see Metrics#addRatio(String, double, int, Dimension...) 
     */
    void addRatio(String group, String name, double ratio, int count, Dimension... dimensions);
}
