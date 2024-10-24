/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.LoginIdentity;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;

final class HttpBasicAuthSigner implements Signer<SmithyHttpRequest, LoginIdentity> {
    static final HttpBasicAuthSigner INSTANCE = new HttpBasicAuthSigner();
    private static final InternalLogger LOGGER = InternalLogger.getLogger(HttpBasicAuthSigner.class);
    private static final String AUTHORIZATION_HEADER = "authorization";
    private static final String SCHEME = "Basic";

    private HttpBasicAuthSigner() {}

    @Override
    public CompletableFuture<SmithyHttpRequest> sign(
        SmithyHttpRequest request,
        LoginIdentity identity,
        AuthProperties properties
    ) {
        var identityString = identity.username() + ":" + identity.password();
        var base64Value = Base64.getEncoder().encodeToString(identityString.getBytes(StandardCharsets.UTF_8));
        var headers = new LinkedHashMap<>(request.headers().map());
        var existing = headers.put(AUTHORIZATION_HEADER, List.of(SCHEME + " " + base64Value));
        if (existing != null) {
            LOGGER.debug("Replaced existing Authorization header value.");
        }
        return CompletableFuture.completedFuture(request.withHeaders(HttpHeaders.of(headers)));
    }
}
