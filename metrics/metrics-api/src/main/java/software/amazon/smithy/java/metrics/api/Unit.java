/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.metrics.api;

/**
 * Common units of measurements defined by <a href="https://ucum.org/ucum">The Unified Code for Units of Measure</a>.
 */
public final class Unit {

    private Unit() {}

    // Time-based units.

    public static final String NANOSECOND = "ns";
    public static final String MICROSECOND = "us";
    public static final String MILLISECOND = "ms";
    public static final String SECOND = "s";
    public static final String MINUTE = "min";
    public static final String HOUR = "h";
    public static final String DAY = "d";
    public static final String WEEK = "wk";
    public static final String YEAR = "a";
    public static final String MONTH = "mo";

    // Size / Storage Units

    public static final String BYTE = "By";
    public static final String KILOBYTE = "kBy";
    public static final String MEGABYTE = "MBy";
    public static final String GIGABYTE = "GBy";
    public static final String TERABYTE = "TBy";

    // Count / Dimensionless units

    /**
     * Dimensionless unit (1), used for pure counts, ratios, and percentages.
     */
    public static final String COUNT = "1";

    /**
     * Dimensionless unit (1), used for pure counts, ratios, and percentages.
     *
     * <p>An alias for {@link #COUNT}.
     */
    public static final String ONE = "1";

    /**
     * Percentage, a unit representing a fractional value (0-100% scale).
     *
     * <p>For example, CPU utilization as a percentage.
     */
    public static final String PERCENT = "%";
}
