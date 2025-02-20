/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.metrics.api;

/**
 * Interface for an object that contains a {@code Metrics} instance.
 */
public interface MetricsProvider {
    /**
     * Gets the contained {@code Metrics} instance.
     *
     * @return The {@code Metrics} instance.
     */
    Metrics getMetrics();
}
