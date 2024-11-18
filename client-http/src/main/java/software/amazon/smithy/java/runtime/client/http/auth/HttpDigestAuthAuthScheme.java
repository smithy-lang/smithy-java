/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http.auth;

import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.LoginIdentity;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeFactory;
import software.amazon.smithy.java.runtime.http.api.HttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpDigestAuthTrait;

/**
 * Implements the HTTP Digest Authentication Scheme as defined in
 * <a href="https://datatracker.ietf.org/doc/html/rfc2617.html">RFC 2617</a>.
 */
public final class HttpDigestAuthAuthScheme implements AuthScheme<HttpRequest, LoginIdentity> {

    @Override
    public ShapeId schemeId() {
        return HttpDigestAuthTrait.ID;
    }

    @Override
    public Class<HttpRequest> requestClass() {
        return HttpRequest.class;
    }

    @Override
    public Class<LoginIdentity> identityClass() {
        return LoginIdentity.class;
    }

    @Override
    public Signer<HttpRequest, LoginIdentity> signer() {
        return HttpDigestAuthSigner.INSTANCE;
    }

    public static final class Factory implements AuthSchemeFactory<HttpDigestAuthTrait> {

        @Override
        public ShapeId schemeId() {
            return HttpDigestAuthTrait.ID;
        }

        @Override
        public AuthScheme<?, ?> createAuthScheme(HttpDigestAuthTrait trait) {
            return new HttpDigestAuthAuthScheme();
        }
    }
}
