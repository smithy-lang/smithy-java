/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.core.endpoint;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.core.schema.TraitKey;

/**
 * Endpoint resolver that decorates another endpoint resolver, adding any host prefixes.
 *
 * @param delegate decorated endpoint resolver.
 */
record HostLabelEndpointResolver(EndpointResolver delegate) implements EndpointResolver {
    @Override
    public CompletableFuture<Endpoint> resolveEndpoint(EndpointResolverParams params) {
        var endpointTrait = params.operation().schema().getTrait(TraitKey.ENDPOINT_TRAIT);
        if (endpointTrait == null) {
            return delegate.resolveEndpoint(params);
        }
        var prefix = HostLabelSerializer.resolvePrefix(endpointTrait.getHostPrefix(), params.inputValue());

        return delegate.resolveEndpoint(params).thenApply(endpoint -> prefix(endpoint, prefix));
    }

    private static Endpoint prefix(Endpoint endpoint, String prefix) {
        try {
            var updatedUri = new URI(
                    endpoint.uri().getScheme().toLowerCase(Locale.US),
                    prefix + endpoint.uri().getHost(),
                    endpoint.uri().getPath(),
                    endpoint.uri().getQuery(),
                    endpoint.uri().getFragment());
            return endpoint.toBuilder().uri(updatedUri).build();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
