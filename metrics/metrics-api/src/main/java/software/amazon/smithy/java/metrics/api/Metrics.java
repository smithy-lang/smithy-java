/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.metrics.api;

import java.time.Duration;

/**
 * A <tt>Metrics</tt> instance allows the expression of a closely related group of <tt>Metrics Primitives</tt>.
 */
public interface Metrics extends AutoCloseable {
    /**
     * Every metrics instance has a group.
     *
     * <p>Metrics created without a group default to the default group of "".
     */
    String DEFAULT_GROUP = "";

    /**
     * Create a new Metrics instance.
     *
     * <p>The Metrics object on which this method is invoked must not have been closed.
     */
    default Metrics newMetrics() {
        return newMetrics(DEFAULT_GROUP);
    }

    /**
     * Create a new Metrics instance that can be used to collect related information that is part of the same
     * <b>Metrics Record</b> as this object, but is aggregated differently.
     *
     * <p>A group is essentially a well-known, named property used for grouping metrics. All metrics have a group,
     * defaulting to {@link #DEFAULT_GROUP} when not set.
     *
     * <p>For example,
     *
     * <pre>{@code
     * void f(Metrics m) {
     *     String group = "ServiceMetrics";
     *     Metrics m2 = m.newMetrics(group);
     *     m2.addProperty("ServiceName", "aService");
     *     m2.addCount("aCount", 1.0, Unit.ONE);
     *     m2.close();
     * }
     * }</pre>
     *
     * <p>The above function would contribute data to the ServiceMetrics group of <b>Metrics Record</b>. Furthermore,
     *
     * <pre>{@code
     * void g(Metrics m) {
     *     for (int i = 0; i < asManyTimesAsYouWant; i++) {
     *         f(m);
     *     }
     * }
     * }</pre>
     *
     * <p>You can open and close new <em>instances</em> of the same group as many times as is needed. The Metrics
     * object on which this method is invoked must not already have been closed.
     *
     * @param group The group name to use with the created metrics.
     * @return metrics using the given {@code group}.
     */
    Metrics newMetrics(String group);

    /**
     * Discard this Metrics object without flushing its contents to a reporter.
     *
     * <p>This is an optional method that might not be supported by all Metrics implementations. If it returns false,
     * this instance should be closed as usual. If it returns true, this instance can still be closed, but does
     * not need to be.
     *
     * @return true if this instance has successfully been discarded
     */
    default boolean discard() {
        return false;
    }

    /**
     * Close this Metrics object.
     *
     * <p>No further <tt>Metrics Primitives</tt> may be expressed once a Metrics object has been closed. Once closed,
     * a Metrics object cannot be reopened. Collaborating metrics objects may be closed in any order; each metrics
     * object must be closed at least once.
     */
    @Override
    void close();

    /**
     * Add a property to the group of <tt>Metrics Primitives</tt>.
     *
     * <p>These properties are often considered during aggregation by <tt>Reporters</tt>.
     *
     * <p>For example,
     *
     * <pre>{@code
     * void f(Metrics m) {
     *     m.addProperty("ServiceName", "aService");
     *     m.addProperty("Operation", "anOperation");
     * }
     * }</pre>
     *
     * @param name Attribute name
     * @param value Attribute value
     */
    void addProperty(String name, String value);

    /**
     * Add a date to the group of <tt>Metrics Primitives</tt>.
     *
     * <p>These dates are often considered during the aggregation by <tt>Reporters</tt>.
     * For example,
     *
     * <pre>{@code
     * void f(Metrics m) {
     *     m.addDate("StartTime", System.currentTimeMillis());
     *     m.addDate("EndTime", System.currentTimeMillis() + 1000.0);
     * }
     * }</pre>
     *
     * <p>To retrieve this value for the current time, see {@link System#currentTimeMillis}.
     *
     * @param name date name
     * @param value date value in milliseconds since epoch.
     */
    void addDate(String name, double value);

    /**
     * Increment a count primitive based on a boolean value.
     *
     * <p>Values of <code>true</code> are converted to <code>1.0</code> and values of <code>false</code> are converted
     * to <code>0.0</code>. If the named count does not exist, it will be set to the converted <em>value</em>. The
     * unit of the counter is set to the dimensionless unit (i.e., {@link Unit#ONE}).
     *
     * @param name count name
     * @param value count value. Converted to a double of 1.0 if the boolean is true and 0.0 if the boolean is false.
     * @see #addCount(String, double, String, int)
     */
    default void addCount(String name, boolean value) {
        addCount(name, value ? 1.0 : 0.0, Unit.ONE, 1);
    }

    /**
     * Increment a count primitive by <em>value</em>, setting it equal to <em>value</em> if the named count had not
     * existed yet.
     *
     * @param name count Name of the metric primitive.
     * @param value count increment
     * @param unit the unit to affix, based on Unified Code for Units of Measure. See {@link Unit} for common values.
     *
     * @see #addCount(String, double, String, int)
     */
    default void addCount(String name, double value, String unit) {
        addCount(name, value, unit, 1);
    }

    /**
     * Increment a count primitive by <em>value</em>, setting it equal to <em>value</em> if
     * the named count had not existed yet.
     *
     * <p>Calling this method may be more efficient than calling <tt>addCount</tt> repeatedly.
     * For example,
     *
     * <pre>{@code
     * void f(Metrics m) {
     *     m.addCount("CoffeeCups", 2.0, Unit.COUNT, 1000000);
     * }
     * }</pre>
     *
     * @param name count name
     * @param value count increment
     * @param unit the unit to affix, based on Unified Code for Units of Measure. See {@link Unit} for common values.
     * @param repeat the number of samples from which <em>value</em> was accumulated
     *
     * @see #addCount(String, double, String)
     */
    void addCount(String name, double value, String unit, int repeat);

    /**
     * Update a level primitive by factoring <em>value</em> into the running average associated with the named level.
     *
     * @param name level name
     * @param value level value
     * @param unit the unit to affix, based on Unified Code for Units of Measure. See {@link Unit} for common values.
     *
     * @see #addLevel(String, double, String, int)
     */
    default void addLevel(String name, double value, String unit) {
        addLevel(name, value, unit, 1);
    }

    /**
     * Update a level primitive by factoring <em>value</em> into the running average associated with the named level.
     *
     * <p>Calling this method may be more efficient than calling <tt>addLevel</tt> repeatedly.
     *
     * <p>For example,
     *
     * <pre>{@code
     * m.addLevel("Velocity", traveled, "l.y.", 1);
     * }</pre>
     *
     * <p>The above code would update a level named "Velocity" keeping track of the average velocity of a spaceship.
     *
     * @param name level name
     * @param value level value
     * @param unit the unit to affix, based on Unified Code for Units of Measure. See {@link Unit} for common values.
     * @param repeat the number of samples from which <em>value</em> was accumulated
     *
     * @see #addLevel(String, double, String)
     */
    void addLevel(String name, double value, String unit, int repeat);

    /**
     * Increment a time primitive by <em>value</em>, setting it equal to <em>value</em> if the named time had not
     * existed yet.
     *
     * @param name count name
     * @param value Time duration
     *
     * @see #addTime(String, Duration, int)
     */
    default void addTime(String name, Duration value) {
        addTime(name, value, 1);
    }

    /**
     * Increment a time primitive by <em>value</em>, setting it equal to <em>value</em> if the named time had not
     * existed yet.
     *
     * <p>Calling this method may be more efficient than calling <tt>addTime</tt> repeatedly.
     * For example,
     *
     * <pre>{@code
     * void f(Metrics m) {
     *   m.addLevel("TimeSpentWritingCode", Duration.of(1000, ChronoUnit.HOURS), 1);
     * }
     * }</pre>
     *
     * <p>The above code would update a timer named "TimeSpentWritingCode" that was keeping track of
     * how long you spent in front of a computer.
     *
     * @param name count name
     * @param value time duration
     * @param repeat the number of samples from which <em>value</em> was accumulated
     *
     * @see #addTime(String, Duration)
     */
    void addTime(String name, Duration value, int repeat);

    /**
     * Add a single metric entry for <em>repeat</em> instances of <em>name</em> with <em>dimensions</em> and the
     * specified <em>value</em>.
     *
     * <p>For example,
     *
     * <pre>{@code
     * private static final Dimension STORE_FOO = new Dimension("Store", "Foo");
     * private static final Dimension VENDOR_SCOTCH = new Dimension("Vendor", "Scotch");
     *
     * void f(Metrics m) {
     *     m.addMetric("TaxCalcTime", 57.0, 3, Unit.MILLISECOND, STORE_HARDLINE, VENDOR_SCOTCH);
     * }
     * }</pre>
     *
     * The above code would add a single metric entry for 3 instances of a metric named "TaxCalcTime"
     * and the dimensions "Store|Foo" and "Vendor|Scotch" with a value of 57 milliseconds.
     *
     * @param name metric name
     * @param value metric value
     * @param repeat number of instances of this metric to record
     * @param unit the unit to affix, based on Unified Code for Units of Measure. See {@link Unit} for common values.
     * @param dimensions list of relevant dimensions
     */
    void addMetric(String name, double value, int repeat, String unit, Dimension... dimensions);

    /**
     * Call addMetric with just a single repeat.
     *
     * @param name metric name
     * @param value metric value
     * @param unit the unit to affix, based on Unified Code for Units of Measure. See {@link Unit} for common values.
     * @param dimensions list of relevant dimensions
     */
    default void addMetric(String name, double value, String unit, Dimension... dimensions) {
        addMetric(name, value, 1, unit, dimensions);
    }

    /**
     * Add a metric ratio for <em>name</em> with the given <em>dimensions</em> and the specified
     * <em>value</em>/<em>ratio</em>.
     *
     * <p>For example,
     *
     * <pre>{@code
     * private static final Dimension STORE_FOO = new Dimension("Store", "Foo");
     * private static final Dimension VENDOR_SCOTCH = new Dimension("Vendor", "Scotch");
     *
     * void f(Metrics m) {
     *     m.addRatio("CacheHitRatio", 0.95, 400, STORE_HARDLINE, VENDOR_SCOTCH);
     * }
     * }</pre>
     *
     * The above code would add a metric ratio named "CacheHitRatio" and the dimensions "Store|Foo" and
     * "Vendor|Scotch" with 95% out of 400.
     *
     * @param name count name
     * @param ratio A value between 0 to 1,representing the percent ratio
     * @param count total count
     * @param dimensions list of relevant dimensions
     */
    void addRatio(String name, double ratio, int count, Dimension... dimensions);
}
