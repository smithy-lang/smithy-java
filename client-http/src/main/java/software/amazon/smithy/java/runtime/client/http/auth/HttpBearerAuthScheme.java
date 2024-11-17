/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http.auth;

import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeFactory;
import software.amazon.smithy.java.runtime.http.api.HttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpBearerAuthTrait;

/**
 * Implements the HTTP Bearer Authentication Scheme as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc2617.html">RFC 2617</a>.
 */
public final class HttpBearerAuthScheme implements AuthScheme<HttpRequest, TokenIdentity> {
    @Override
    public ShapeId schemeId() {
        return HttpBearerAuthTrait.ID;
    }

    @Override
    public Class<HttpRequest> requestClass() {
        return HttpRequest.class;
    }

    @Override
    public Class<TokenIdentity> identityClass() {
        return TokenIdentity.class;
    }

    @Override
    public Signer<HttpRequest, TokenIdentity> signer() {
        return HttpBearerAuthSigner.INSTANCE;
    }

    public static final class Factory implements AuthSchemeFactory<HttpBearerAuthTrait> {

        @Override
        public ShapeId schemeId() {
            return HttpBearerAuthTrait.ID;
        }

        @Override
        public AuthScheme<?, ?> createAuthScheme(HttpBearerAuthTrait trait) {
            return new HttpBearerAuthScheme();
        }
    }
}
