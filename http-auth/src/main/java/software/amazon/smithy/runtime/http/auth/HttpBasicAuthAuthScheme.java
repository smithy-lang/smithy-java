/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.runtime.http.auth;

import software.amazon.smithy.java.runtime.auth.api.AuthSchemeFactory;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.LoginIdentity;
import software.amazon.smithy.java.runtime.auth.api.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.traits.HttpBasicAuthTrait;

public final class HttpBasicAuthAuthScheme implements AuthScheme<SmithyHttpRequest, LoginIdentity> {

    @Override
    public ShapeId schemeId() {
        return HttpBasicAuthTrait.ID;
    }

    @Override
    public Class<SmithyHttpRequest> requestClass() {
        return SmithyHttpRequest.class;
    }

    @Override
    public Class<LoginIdentity> identityClass() {
        return LoginIdentity.class;
    }

    @Override
    public Signer<SmithyHttpRequest, LoginIdentity> signer() {
        return HttpBasicAuthSigner.INSTANCE;
    }

    public static final class Factory implements AuthSchemeFactory<HttpBasicAuthTrait> {

        @Override
        public ShapeId schemeId() {
            return HttpBasicAuthTrait.ID;
        }

        @Override
        public AuthScheme<?, ?> createAuthScheme(HttpBasicAuthTrait trait) {
            return new HttpBasicAuthAuthScheme();
        }
    }
}
