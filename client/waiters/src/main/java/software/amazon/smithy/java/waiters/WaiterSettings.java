package software.amazon.smithy.java.waiters;

import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.waiters.backoff.BackoffStrategy;

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

