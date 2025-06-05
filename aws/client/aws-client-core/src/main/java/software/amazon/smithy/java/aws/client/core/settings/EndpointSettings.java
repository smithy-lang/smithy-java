/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.client.core.settings;

import software.amazon.smithy.java.client.core.ClientSetting;
import software.amazon.smithy.java.context.Context;

/**
 * Configures AWS specific endpoint settings.
 */
public interface EndpointSettings<B extends ClientSetting<B>> extends RegionSetting<B>, AccountIdSetting<B> {
    /**
     * If the SDK client is configured to use dual stack endpoints, defaults to false.
     */
    Context.Key<Boolean> USE_DUAL_STACK = Context.key("Whether to use dual stack endpoint");

    /**
     * If the SDK client is configured to use FIPS-compliant endpoints, defaults to false.
     */
    Context.Key<Boolean> USE_FIPS = Context.key("Whether to use FIPS endpoints");

    /**
     * The mode used when resolving Account ID based endpoints.
     */
    Context.Key<String> ACCOUNT_ID_ENDPOINT_MODE = Context.key("Account ID endpoint mode");

    /**
     * Configures if the SDK uses dual stack endpoints. Defaults to false.
     *
     * @param useDualStack True to enable dual stack.
     * @return self
     */
    default B useDualStackEndpoint(boolean useDualStack) {
        return putConfig(USE_DUAL_STACK, useDualStack);
    }

    /**
     * Configures if the SDK uses FIPS endpoints. Defaults to false.
     *
     * @param useFips True to enable FIPS endpoints.
     * @return self
     */
    default B useFipsEndpoint(boolean useFips) {
        return putConfig(USE_FIPS, useFips);
    }

    /**
     * Sets the account ID endpoint mode for endpoint resolution.
     *
     * @param accountIdEndpointMode Account ID based endpoint resolution mode.
     * @return self
     */
    default B accountIdEndpointMode(String accountIdEndpointMode) {
        return putConfig(ACCOUNT_ID_ENDPOINT_MODE, accountIdEndpointMode);
    }
}
