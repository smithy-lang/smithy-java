/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.example.middleware;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.auth.api.AuthProperties;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.http.api.HttpRequest;

final class CustomAuthSigner implements Signer<HttpRequest, TokenIdentity> {
    static final CustomAuthSigner INSTANCE = new CustomAuthSigner();
    private static final String AUTHORIZATION_HEADER = "X-Custom-Authorization";

    private CustomAuthSigner() {}

    @Override
    public CompletableFuture<HttpRequest> sign(
            HttpRequest request,
            TokenIdentity identity,
            AuthProperties properties
    ) {
        var headers = request.headers().toModifiable();
        headers.putHeader(AUTHORIZATION_HEADER, identity.token());
        return CompletableFuture.completedFuture(request.toBuilder().headers(headers).build());
    }
}
