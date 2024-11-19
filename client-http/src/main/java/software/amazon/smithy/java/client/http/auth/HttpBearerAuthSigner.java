/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.auth;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.auth.api.AuthProperties;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.logging.InternalLogger;

final class HttpBearerAuthSigner implements Signer<HttpRequest, TokenIdentity> {
    static final HttpBearerAuthSigner INSTANCE = new HttpBearerAuthSigner();
    private static final InternalLogger LOGGER = InternalLogger.getLogger(HttpBearerAuthSigner.class);
    private static final String AUTHORIZATION_HEADER = "authorization";
    private static final String SCHEME = "Bearer";

    private HttpBearerAuthSigner() {}

    @Override
    public CompletableFuture<HttpRequest> sign(
        HttpRequest request,
        TokenIdentity identity,
        AuthProperties properties
    ) {
        var headers = new LinkedHashMap<>(request.headers().map());
        var existing = headers.put(AUTHORIZATION_HEADER, List.of(SCHEME + " " + identity.token()));
        if (existing != null) {
            LOGGER.debug("Replaced existing Authorization header value.");
        }
        return CompletableFuture.completedFuture(request.toBuilder().headers(HttpHeaders.of(headers)).build());
    }
}
