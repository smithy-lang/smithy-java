/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http.auth;

import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.LoginIdentity;
import software.amazon.smithy.java.runtime.http.api.HttpRequest;

/**
 * TODO: Fully implement
 */
final class HttpDigestAuthSigner implements Signer<HttpRequest, LoginIdentity> {
    static final HttpDigestAuthSigner INSTANCE = new HttpDigestAuthSigner();

    private HttpDigestAuthSigner() {}

    @Override
    public CompletableFuture<HttpRequest> sign(
        HttpRequest request,
        LoginIdentity identity,
        AuthProperties properties
    ) {
        throw new UnsupportedOperationException();
    }
}
