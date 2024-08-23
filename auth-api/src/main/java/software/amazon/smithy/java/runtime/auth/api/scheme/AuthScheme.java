/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.auth.api.scheme;

import java.util.Optional;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.AwsCredentialsIdentity;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.runtime.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.runtime.auth.api.identity.TokenIdentity;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * An authentication scheme, composed of:
 *
 * <ol>
 *     <li>A scheme ID - A unique identifier for the authentication scheme.</li>
 *     <li>An identity resolver - An API that can be queried to acquire the customer's identity.</li>
 *     <li>A signer - An API that can be used to sign requests.</li>
 * </ol>
 *
 * See example auth schemes defined <a href="https://smithy.io/2.0/spec/authentication-traits.html">here</a>.
 *
 * @param <IdentityT> The {@link Identity} used by this authentication scheme.
 * @param <RequestT>  The request to sign.
 */
public interface AuthScheme<RequestT, IdentityT extends Identity> {
    /**
     * Retrieve the authentication scheme ID, a unique identifier for the authentication scheme (e.g., aws.auth#sigv4).
     *
     * @return the scheme ID.
     */
    ShapeId schemeId();

    /**
     * Get the request type that this auth scheme can sign.
     *
     * @return the request type that can be signed.
     */
    Class<RequestT> requestClass();

    /**
     * Get the identity class this auth scheme can sign.
     *
     * @return the identity type that can be signed.
     */
    Class<IdentityT> identityClass();

    /**
     * Retrieve the identity resolver associated with this authentication scheme. The identity generated by this
     * resolver is guaranteed to be supported by the signer in this authentication scheme.
     *
     * <p>For example, if the scheme ID is aws.auth#sigv4, the resolver returns an {@link AwsCredentialsIdentity}, if
     * the scheme ID is httpBearerAuth, the resolver returns a {@link TokenIdentity}.
     *
     * <p>Note, the returned identity resolver may differ from the type of identity resolver retrieved from the
     * provided {@link IdentityResolvers}.
     *
     * @param resolvers Resolver repository.
     * @return the optionally located identity resolver.
     */
    default Optional<IdentityResolver<IdentityT>> identityResolver(IdentityResolvers resolvers) {
        return Optional.ofNullable(resolvers.identityResolver(identityClass()));
    }

    /**
     * Gets the default signer properties from the context.
     *
     * @param context request context
     */
    default AuthProperties getSignerProperties(Context context) {
        return AuthProperties.empty();
    }

    /**
     * Gets the default identity properties from the context.
     *
     * @param context request context
     */
    default AuthProperties getIdentityProperties(Context context) {
        return AuthProperties.empty();
    }

    /**
     * Retrieve the signer associated with this authentication scheme.
     *
     * <p>This signer is guaranteed to support the identity generated by the identity resolver in this authentication
     * scheme.
     *
     * @return the signer.
     */
    Signer<RequestT, IdentityT> signer();

    /**
     * Create a simple AuthScheme.
     *
     * @param schemeId      Auth scheme shape ID.
     * @param requestClass  Request class supported by the auth scheme.
     * @param identityClass Identity class supported by the auth scheme.
     * @param signer        Signed used with this auth scheme.
     * @return the created AuthScheme.
     * @param <RequestT> Request type.
     * @param <IdentityT> Identity type.
     */
    static <RequestT, IdentityT extends Identity> AuthScheme<RequestT, IdentityT> of(
        ShapeId schemeId,
        Class<RequestT> requestClass,
        Class<IdentityT> identityClass,
        Signer<RequestT, IdentityT> signer
    ) {
        return new AuthSchemeRecord<>(schemeId, requestClass, identityClass, signer);
    }

    /**
     * Retrieve the {@code smithy.api#noAuth} auth scheme, which represent no authentication to be performed.
     *
     * @return the {@code smithy.api#noAuth} auth scheme
     */
    static AuthScheme<Object, Identity> noAuthAuthScheme() {
        return NoAuthAuthScheme.INSTANCE;
    }
}
