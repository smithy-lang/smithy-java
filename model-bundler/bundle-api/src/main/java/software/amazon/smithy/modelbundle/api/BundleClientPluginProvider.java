/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import software.amazon.smithy.java.client.core.ClientPlugin;

/**
 * A ConfigProvider is used to parse a bundle of service information (model, auth configuration, endpoints, etc.) and
 * configure outgoing client calls as necessary.
 *
 * <p>Implementations of this interface can define a wrapper type that adds additional parameters to vended MCP tools.
 * For example, an AWS auth provider can make a wrapper that adds the region and AWS credential profile name as
 * arguments to tools generated for AWS APIs. A wrapper type does not need to be defined if no per-request parameters
 * need to be injected.
 *
 * <p>The ConfigProvider is responsible for configuring outbound client calls with endpoint, identity, and auth resolver
 * mechanisms. The default implementation of {@link #adaptConfig(T)} orchestrates the calls to all other ConfigProvider
 * APIs and should not be overridden. If an override is needed, the {@code super} method should be called and the
 * returned RequestOverrideConfig.Builder should be modified.
 */
public interface BundleClientPluginProvider {
    /**
     * Creates a new ClientPlugin that can be used to configure the client.
     *
     * @return a new ClientPlugin
     */
    ClientPlugin newPlugin();
}
