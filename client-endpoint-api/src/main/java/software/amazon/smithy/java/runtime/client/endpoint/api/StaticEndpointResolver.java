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
record StaticEndpointResolver(Endpoint endpoint) implements EndpointResolver {

    @Override
    public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
        if (!params.operationSchema().hasTrait(EndpointTrait.class)) {
            return CompletableFuture.completedFuture(endpoint);
        }
        var hostPrefix = params.operationSchema().expectTrait(EndpointTrait.class).getHostPrefix();

        String prefix;
        // The prefix is static and can simply be prepended to endpoint host name with no templating.
        if (hostPrefix.getLabels().isEmpty()) {
            prefix = hostPrefix.toString();
        } else {
            var serializer = new HostLabelSerializer(hostPrefix);
            params.inputShape().serialize(serializer);
            serializer.flush();
            prefix = serializer.prefix();
        }

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
