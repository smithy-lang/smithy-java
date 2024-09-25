/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.endpoint.api;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.model.traits.EndpointTrait;

/**
 * Endpoint resolver that takes a static endpoint.
 *
 * <p>This endpoint resolvers will handle the {@code @endpoint} and {@code @hostLabel} traits automatically, adding
 * a prefix to the endpoint host based on the resolved host prefix.
 *
 * @param endpoint static endpoint.
 */
record StaticEndpointResolver(Endpoint endpoint, boolean ignorePrefix) implements EndpointResolver {

    @Override
    public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
        if (ignorePrefix || !params.operationSchema().hasTrait(EndpointTrait.class)) {
            return CompletableFuture.completedFuture(endpoint);
        }

        var hostPrefix = params.operationSchema().expectTrait(EndpointTrait.class).getHostPrefix();
        var prefix = HostLabelSerializer.resolvePrefix(hostPrefix, params.inputShape());

        URI updatedUri = null;
        try {
            updatedUri = new URI(
                endpoint.uri().getScheme().toLowerCase(Locale.US),
                prefix + endpoint.uri().getHost(),
                endpoint.uri().getPath(),
                endpoint.uri().getQuery(),
                endpoint.uri().getFragment()
            );
        } catch (URISyntaxException e) {
            return CompletableFuture.failedFuture(e);
        }

        return CompletableFuture.completedFuture(endpoint.toBuilder().uri(updatedUri).build());
    }
}
