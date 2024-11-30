/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core;

/**
 * A plugin modifies a client's configuration when the client is created or at request execution time.
 */
@FunctionalInterface
public interface ClientPlugin {
    /**
     * Modify the provided client configuration.
     *
     * <p>When applying plugins to a {@code ClientConfig.Builder}, use {@link ClientConfig.Builder#applyPlugin)}
     * so that the application of the plugin is tracked with the builder.
     *
     * <pre>{@code
     * // Do this:
     * configBuilder.applyPlugin(new UserAgentPlugin());
     *
     * // Not this:
     * new UserAgentPlugin().configureClient(configBuilder);
     * }</pre>
     */
    void configureClient(ClientConfig.Builder config);
}
