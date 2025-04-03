/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.waiters;

import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.waiters.backoff.BackoffStrategy;

/**
 * Common settings for all Waiters.
 */
interface WaiterSettings {
    /**
     * Set the strategy to use to calculate wait time between polling requests.
     *
     * @param backoffStrategy strategy to use to calculate wait time between polling requests.
     */
    void backoffStrategy(BackoffStrategy backoffStrategy);

    /**
     * Set the override config to use for polling requests.
     *
     * @param overrideConfig override config to use for polling requests
     */
    void overrideConfig(RequestOverrideConfig overrideConfig);
}
