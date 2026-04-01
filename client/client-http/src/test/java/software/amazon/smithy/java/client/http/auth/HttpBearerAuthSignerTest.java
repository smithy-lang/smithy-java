/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;

public class HttpBearerAuthSignerTest {
    @Test
    void testBearerAuthSigner() {
        var tokenIdentity = TokenIdentity.create("token");
        var request = HttpRequest.create()
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setMethod("PUT")
                .setUri(URI.create("https://www.example.com"))
                .toUnmodifiable();

        var signedRequest = HttpBearerAuthSigner.INSTANCE.sign(request, tokenIdentity, Context.empty()).signedRequest();
        var authHeader = signedRequest.headers().firstValue("authorization");
        assertEquals(authHeader, "Bearer token");
    }

    @Test
    void overwritesExistingHeader() {
        var tokenIdentity = TokenIdentity.create("token");
        var request = HttpRequest.create()
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setMethod("PUT")
                .setHeaders(HttpHeaders.of(Map.of("Authorization", List.of("FOO", "BAR"))))
                .setUri(URI.create("https://www.example.com"))
                .toUnmodifiable();

        var signedRequest = HttpBearerAuthSigner.INSTANCE.sign(request, tokenIdentity, Context.empty());
        var authHeader = signedRequest.signedRequest().headers().firstValue("authorization");
        assertEquals(authHeader, "Bearer token");
    }
}
