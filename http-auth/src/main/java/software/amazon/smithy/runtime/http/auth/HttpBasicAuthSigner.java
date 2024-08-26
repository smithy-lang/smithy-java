/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.runtime.http.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.LoginIdentity;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;

final class HttpBasicAuthSigner implements Signer<SmithyHttpRequest, LoginIdentity> {
    static final HttpBasicAuthSigner INSTANCE = new HttpBasicAuthSigner();
    private static final String AUTH_HEADER = "Authorization";
    private static final String SCHEME = "Basic";

    private HttpBasicAuthSigner() {}

    @Override
    public SmithyHttpRequest sign(SmithyHttpRequest request, LoginIdentity identity, AuthProperties properties) {
        var identityString = identity.username() + ":" + identity.password();
        var base64Value = Base64.getEncoder().encodeToString(identityString.getBytes(StandardCharsets.UTF_8));
        return request.withAddedHeaders(AUTH_HEADER, SCHEME + " " + base64Value);
    }
}
