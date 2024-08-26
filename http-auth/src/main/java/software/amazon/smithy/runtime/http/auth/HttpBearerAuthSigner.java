/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.runtime.http.auth;

import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;

final class HttpBearerAuthSigner implements Signer<SmithyHttpRequest, TokenIdentity> {
    static final HttpBearerAuthSigner INSTANCE = new HttpBearerAuthSigner();
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String SCHEME = "Bearer";

    private HttpBearerAuthSigner() {}

    @Override
    public SmithyHttpRequest sign(SmithyHttpRequest request, TokenIdentity identity, AuthProperties properties) {
        return request.withAddedHeaders(AUTHORIZATION_HEADER, SCHEME + " " + identity.token());
    }
}
