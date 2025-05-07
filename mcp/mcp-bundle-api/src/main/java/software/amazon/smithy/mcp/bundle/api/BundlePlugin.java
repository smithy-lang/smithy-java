/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.mcp.bundle.api;

import software.amazon.smithy.java.client.core.Client;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;

/**
 * A BundlePlugin applies the settings specified in a {@link software.amazon.smithy.modelbundle.api.model.Bundle}
 * on a per-call basis.
 */
public interface BundlePlugin {
    /**
     * Applies the bundle-specific settings to a client call.
     * @return a {@link RequestOverrideConfig.Builder} with the settings from the bundle applied
     */
    <C extends Client, B extends Client.Builder<C, B>> B configureClient(B clientBuilder);
}
