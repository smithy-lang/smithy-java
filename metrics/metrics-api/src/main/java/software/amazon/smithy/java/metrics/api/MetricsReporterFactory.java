/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.metrics.api;

/**
 * Creates {@link MetricsReporter} instances.
 */
public interface MetricsReporterFactory {
    /**
     * Create a new {@link MetricsReporter}.
     *
     * @return the {@link MetricsReporter} instance.
     */
    MetricsReporter newReporter();
}
