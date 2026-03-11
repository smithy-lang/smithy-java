/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.endpoint;

/**
 * An endpoint resolver that always returns the same endpoint.
 *
 * @param endpoint Endpoint to return exactly.
 */
record StaticHostResolver(Endpoint endpoint) implements EndpointResolver {
    @Override
    public Endpoint resolveEndpoint(EndpointResolverParams params) {
        return endpoint;
    }
}
