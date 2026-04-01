/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.auth;

import software.amazon.smithy.java.auth.api.SignResult;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.logging.InternalLogger;

final class HttpBearerAuthSigner implements Signer<HttpRequest, TokenIdentity> {
    static final HttpBearerAuthSigner INSTANCE = new HttpBearerAuthSigner();
    private static final InternalLogger LOGGER = InternalLogger.getLogger(HttpBearerAuthSigner.class);
    private static final String SCHEME = "Bearer";

    private HttpBearerAuthSigner() {}

    @Override
    public SignResult<HttpRequest> sign(HttpRequest request, TokenIdentity identity, Context properties) {
        var mod = request.toModifiable();
        if (mod.headers().hasHeader(HeaderName.AUTHORIZATION)) {
            LOGGER.debug("Replaced existing Authorization header value.");
        }
        return new SignResult<>(mod.setHeader(HeaderName.AUTHORIZATION, SCHEME + " " + identity.token()));
    }
}
