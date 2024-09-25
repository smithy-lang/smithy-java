/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoint.api;

import java.util.Objects;
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
     * Create a default endpoint resolver that always returns the same endpoint with prefixes added if relevant.
     *
     * <p>This endpoint resolvers will handle the {@link software.amazon.smithy.model.traits.HostLabelTrait} and
     * {@link software.amazon.smithy.model.traits.EndpointTrait} traits automatically, adding a prefix to the endpoint
     * host based on the resolved host prefix.
     *
     * @param endpoint Endpoint to always resolve.
     * @return the endpoint resolver.
     */
    static EndpointResolver staticEndpoint(Endpoint endpoint) {
        return new HostLabelEndpointResolver(staticHost(endpoint));
    }

    /**
     * Create an endpoint resolver that always returns the same host.
     *
     * @param endpoint Endpoint to always resolve.
     * @return the endpoint resolver.
     */
    static EndpointResolver staticHost(Endpoint endpoint) {
        Objects.requireNonNull(endpoint);
        return params -> CompletableFuture.completedFuture(endpoint);
    }
}
