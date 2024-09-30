/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.Identity;
import software.amazon.smithy.java.runtime.client.core.auth.identity.IdentityResolver;
import software.amazon.smithy.java.runtime.client.core.auth.identity.IdentityResolvers;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.runtime.client.core.auth.scheme.AuthSchemeFactory;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Test auth scheme to test discovery of auth schemes during client code generation.
 */
public final class TestAuthScheme implements AuthScheme<SmithyHttpRequest, Identity> {
    public static final String SIGNATURE_HEADER = "x-signature";

    public TestAuthScheme() {
        // Public, no-arg constructor required
    }

    @Override
    public ShapeId schemeId() {
        return TestAuthSchemeTrait.ID;
    }

    @Override
    public Class<SmithyHttpRequest> requestClass() {
        return SmithyHttpRequest.class;
    }

    @Override
    public Class<Identity> identityClass() {
        return Identity.class;
    }

    @Override
    public Optional<IdentityResolver<Identity>> identityResolver(IdentityResolvers resolvers) {
        return AuthScheme.noAuthAuthScheme().identityResolver(resolvers);
    }

    @Override
    public Signer<SmithyHttpRequest, Identity> signer() {
        return new TestSigner();
    }

    private static final class TestSigner implements Signer<SmithyHttpRequest, Identity> {
        @Override
        public CompletableFuture<SmithyHttpRequest> sign(
            SmithyHttpRequest request,
            Identity identity,
            AuthProperties properties
        ) {
            return CompletableFuture.completedFuture(
                request.withAddedHeaders(SIGNATURE_HEADER, "smithy-test-signature")
            );
        }
    }

    public static final class Factory implements AuthSchemeFactory<TestAuthSchemeTrait> {

        @Override
        public ShapeId schemeId() {
            return TestAuthSchemeTrait.ID;
        }

        @Override
        public AuthScheme<SmithyHttpRequest, Identity> createAuthScheme(TestAuthSchemeTrait trait) {
            return new TestAuthScheme();
        }
    }
}
