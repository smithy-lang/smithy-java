/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.example.middleware;

import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.model.shapes.ShapeId;

public final class CustomAuthScheme implements AuthScheme<HttpRequest, TokenIdentity> {
    private static final ShapeId SCHEME_ID = ShapeId.from("smithy.example.middleware#customAuth");

    @Override
    public ShapeId schemeId() {
        return SCHEME_ID;
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
        return CustomAuthSigner.INSTANCE;
    }
}
