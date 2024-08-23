/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.runtime.http.auth;

import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.Signer;
import software.amazon.smithy.java.runtime.auth.api.identity.LoginIdentity;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;

final class HttpBasicAuthSigner implements Signer<SmithyHttpRequest, LoginIdentity> {
    public static final HttpBasicAuthSigner INSTANCE = new HttpBasicAuthSigner();

    private HttpBasicAuthSigner() {}

    @Override
    public SmithyHttpRequest sign(SmithyHttpRequest request, LoginIdentity identity, AuthProperties properties) {
        return null;
    }
}
