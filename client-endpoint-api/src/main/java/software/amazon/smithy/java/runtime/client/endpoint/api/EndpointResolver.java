/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoint.api;

import java.util.concurrent.CompletableFuture;

/**
 * Resolves an endpoint for an operation.
 */
@FunctionalInterface
public interface EndpointResolver {
    /**
     * Resolves an endpoint using the provided parameters.
     *
     * @param params The parameters used during endpoint resolution.
     * @return a CompletableFuture for the resolved endpoint.
     */
    CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params);

    /**
     * Create an endpoint resolver that always returns the same endpoint.
     *
     * @param endpoint Endpoint to always resolve.
     * @return the endpoint resolver.
     */
    static EndpointResolver staticEndpoint(Endpoint endpoint) {
        return params -> CompletableFuture.completedFuture(endpoint);
    }
}
