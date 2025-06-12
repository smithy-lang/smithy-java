/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.endpoint;

import java.util.concurrent.CompletableFuture;

/**
 * An endpoint resolver that always returns the same endpoint.
 *
 * @param endpoint Endpoint to return exactly.
 */
record StaticHostResolver(Endpoint endpoint) implements EndpointResolver {
    @Override
    public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
        return CompletableFuture.completedFuture(endpoint);
    }
}
