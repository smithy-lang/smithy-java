/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.metrics.api;

/**
 * A <tt>MetricsFactory</tt> allows for the creation of unrelated <b>MetricsRecords</b>.
 *
 * <p>To begin collecting metrics, you instantiate a concrete instance of a <tt>MetricsFactory</tt> and pass it into
 * your application.
 *
 * <pre>{@code
 * MetricsFactory factory = new NullMetricsFactory();
 * Metrics metrics = factory.newMetrics();
 * metrics.addCount("Example", 1.0);
 * metrics.close();
 * }</pre>
 */
public interface MetricsFactory {
    /**
     * Create a new <tt>Metrics</tt> instance that can be used to contribute <tt>Metrics</tt> to <b>MetricsRecords</b>.
     *
     * @return Metrics instance
     */
    Metrics newMetrics();
}
