/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.modelbundle.api;

import java.util.List;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeOption;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolverParams;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.model.shapes.ShapeId;

final class StaticAuthSchemeResolver implements AuthSchemeResolver {
    static final StaticAuthSchemeResolver INSTANCE = new StaticAuthSchemeResolver();
    static final ShapeId CONFIGURED_AUTH = ShapeId.from("modelbundle#configuredAuth");
    private static final List<AuthSchemeOption> AUTH_SCHEME_OPTION = List.of(new AuthSchemeOption(CONFIGURED_AUTH));

    @Override
    public List<AuthSchemeOption> resolveAuthScheme(AuthSchemeResolverParams params) {
        return AUTH_SCHEME_OPTION;
    }

    static <RequestT, IdentityT extends Identity> AuthScheme<RequestT, IdentityT> staticScheme(
            AuthScheme<RequestT, IdentityT> actual
    ) {
        return new AuthScheme<>() {
            @Override
            public ShapeId schemeId() {
                return StaticAuthSchemeResolver.CONFIGURED_AUTH;
            }

            @Override
            public Class<RequestT> requestClass() {
                return actual.requestClass();
            }

            @Override
            public Class<IdentityT> identityClass() {
                return actual.identityClass();
            }

            @Override
            public Signer<RequestT, IdentityT> signer() {
                return actual.signer();
            }

            @Override
            public IdentityResolver<IdentityT> identityResolver(IdentityResolvers resolvers) {
                return actual.identityResolver(resolvers);
            }

            @Override
            public Context getSignerProperties(Context context) {
                return actual.getSignerProperties(context);
            }

            @Override
            public Context getIdentityProperties(Context context) {
                return actual.getIdentityProperties(context);
            }
        };
    }
}
