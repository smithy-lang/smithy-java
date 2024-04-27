/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoints.api;

/**
 * Resolves an endpoint for an operation.
 */
@FunctionalInterface
public interface EndpointResolver {
    /**
     * Resolves an endpoint using the provided parameters.
     *
     * @param params The parameters used during endpoint resolution.
     * @return the resolved endpoint.
     */
    Endpoint resolveEndpoint(EndpointResolverParams params);

    /**
     * Create an endpoint resolver that always returns the same endpoint.
     *
     * @param endpoint Endpoint to always resolve.
     * @return the endpoint resolver.
     */
    static EndpointResolver staticEndpoint(Endpoint endpoint) {
        return params -> endpoint;
    }
}
