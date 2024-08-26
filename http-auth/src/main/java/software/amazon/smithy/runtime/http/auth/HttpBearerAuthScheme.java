/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.runtime.http.auth;

import software.amazon.smithy.java.runtime.auth.api.AuthSchemeFactory;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpBearerAuthTrait;

/**
 * Implements the HTTP Bearer Authentication Scheme as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc2617.html>RFC 2617</a>.
 */
public final class HttpBearerAuthScheme implements AuthScheme<SmithyHttpRequest, TokenIdentity> {
    @Override
    public ShapeId schemeId() {
        return HttpBearerAuthTrait.ID;
    }

    @Override
    public Class<SmithyHttpRequest> requestClass() {
        return SmithyHttpRequest.class;
    }

    @Override
    public Class<TokenIdentity> identityClass() {
        return TokenIdentity.class;
    }

    @Override
    public Signer<SmithyHttpRequest, TokenIdentity> signer() {
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
