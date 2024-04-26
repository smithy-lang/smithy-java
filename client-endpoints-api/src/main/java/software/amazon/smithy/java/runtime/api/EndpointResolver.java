/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.api;

/**
 * Resolves an endpoint for an operation.
 */
public interface EndpointResolver {
    /**
     * Resolves an endpoint using the provided parameters.
     *
     * @param request Request parameters used during endpoint resolution.
     * @return Returns the resolved endpoint.
     */
    Endpoint resolveEndpoint(EndpointResolverRequest request);

    /**
     * Create an endpoint resolver that always returns the same endpoint.
     *
     * @param endpoint Endpoint to always resolve.
     * @return Returns the endpoint resolver.
     */
    static EndpointResolver staticEndpoint(Endpoint endpoint) {
        return params -> endpoint;
    }
}
