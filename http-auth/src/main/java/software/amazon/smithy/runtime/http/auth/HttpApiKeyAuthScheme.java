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
import software.amazon.smithy.model.traits.HttpApiKeyAuthTrait;

// TODO: Should API key identity be distinct from TokenIdentity?
public final class HttpApiKeyAuthScheme implements AuthScheme<SmithyHttpRequest, TokenIdentity> {
    @Override
    public ShapeId schemeId() {
        return HttpApiKeyAuthTrait.ID;
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
        return HttpApiKeyAuthSigner.INSTANCE;
    }

    public static final class Factory implements AuthSchemeFactory<HttpApiKeyAuthTrait> {

        @Override
        public ShapeId schemeId() {
            return HttpApiKeyAuthTrait.ID;
        }

        @Override
        public AuthScheme<?, ?> createAuthScheme(HttpApiKeyAuthTrait trait) {
            return new HttpApiKeyAuthScheme();
        }
    }
}
