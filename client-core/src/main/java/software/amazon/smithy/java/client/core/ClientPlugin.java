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
     * <p>When applying plugins to a {@code ClientConfig.Builder}, use {@link ClientConfig.Builder#applyPlugin}
     * so that the application of the plugin is tracked with the builder.
     *
     * <p><strong>Do this:</strong>
     * <pre>{@code
     * configBuilder.applyPlugin(new UserAgentPlugin());
     * }</pre>
     *
     * <p><strong>Don't do this:</strong>
     * <pre>{@code
     * new UserAgentPlugin().configureClient(configBuilder);
     * }</pre>
     */
    void configureClient(ClientConfig.Builder config);
}
