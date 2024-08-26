/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.runtime.http.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpVersion;

public class HttpBearerAuthSignerTest {
    @Test
    void testBearerAuthSigner() {
        var tokenIdentity = TokenIdentity.create("token");
        var request = SmithyHttpRequest.builder()
            .httpVersion(SmithyHttpVersion.HTTP_1_1)
            .method("PUT")
            .uri(URI.create("https://www.example.com"))
            .build();

        var signedRequest = HttpBearerAuthSigner.INSTANCE.sign(request, tokenIdentity, AuthProperties.empty());
        var authHeader = signedRequest.headers().map().get("Authorization");
        assertNotNull(authHeader);
        assertEquals(authHeader.get(0), "Bearer token");
    }
}
