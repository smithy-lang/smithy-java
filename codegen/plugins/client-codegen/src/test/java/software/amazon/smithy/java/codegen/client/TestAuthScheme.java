/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.auth.api.identity.IdentityResolvers;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeFactory;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Test auth scheme to test discovery of auth schemes during client code generation.
 */
public final class TestAuthScheme implements AuthScheme<HttpRequest, Identity> {
    public static final String SIGNATURE_HEADER = "x-signature";

    public TestAuthScheme() {
        // Public, no-arg constructor required
    }

    @Override
    public ShapeId schemeId() {
        return TestAuthSchemeTrait.ID;
    }

    @Override
    public Class<HttpRequest> requestClass() {
        return HttpRequest.class;
    }

    @Override
    public Class<Identity> identityClass() {
        return Identity.class;
    }

    @Override
    public IdentityResolver<Identity> identityResolver(IdentityResolvers resolvers) {
        return AuthScheme.noAuthAuthScheme().identityResolver(resolvers);
    }

    @Override
    public Signer<HttpRequest, Identity> signer() {
        return new TestSigner();
    }

    private static final class TestSigner implements Signer<HttpRequest, Identity> {
        @Override
        public HttpRequest sign(HttpRequest request, Identity identity, Context properties) {
            return request.toBuilder().withAddedHeader(SIGNATURE_HEADER, "smithy-test-signature").build();
        }
    }

    public static final class Factory implements AuthSchemeFactory<TestAuthSchemeTrait> {

        @Override
        public ShapeId schemeId() {
            return TestAuthSchemeTrait.ID;
        }

        @Override
        public AuthScheme<HttpRequest, Identity> createAuthScheme(TestAuthSchemeTrait trait) {
            return new TestAuthScheme();
        }
    }
}
