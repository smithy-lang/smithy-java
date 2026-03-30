/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.endpoints;

import java.util.Locale;
import software.amazon.smithy.java.core.schema.TraitKey;

/**
 * Endpoint resolver that decorates another endpoint resolver, adding any host prefixes.
 *
 * @param delegate decorated endpoint resolver.
 */
record HostLabelEndpointResolver(EndpointResolver delegate) implements EndpointResolver {
    @Override
    public Endpoint resolveEndpoint(EndpointResolverParams params) {
        var endpointTrait = params.operation().schema().getTrait(TraitKey.ENDPOINT_TRAIT);
        if (endpointTrait == null) {
            return delegate.resolveEndpoint(params);
        }
        var prefix = HostLabelSerializer.resolvePrefix(endpointTrait.getHostPrefix(), params.inputValue());
        var endpoint = delegate.resolveEndpoint(params);
        return prefix(endpoint, prefix);
    }

    private static Endpoint prefix(Endpoint endpoint, String prefix) {
        var uri = endpoint.uri();
        var updatedUri = uri
                .withScheme(uri.getScheme().toLowerCase(Locale.ROOT))
                .withHost(prefix + uri.getHost());
        return endpoint.toBuilder().uri(updatedUri).build();
    }
}
