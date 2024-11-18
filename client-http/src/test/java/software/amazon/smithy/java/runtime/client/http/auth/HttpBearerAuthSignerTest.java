/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.identity.TokenIdentity;
import software.amazon.smithy.java.runtime.http.api.HttpHeaders;
import software.amazon.smithy.java.runtime.http.api.HttpRequest;
import software.amazon.smithy.java.runtime.http.api.HttpVersion;

public class HttpBearerAuthSignerTest {
    @Test
    void testBearerAuthSigner() throws ExecutionException, InterruptedException {
        var tokenIdentity = TokenIdentity.create("token");
        var request = HttpRequest.builder()
            .httpVersion(HttpVersion.HTTP_1_1)
            .method("PUT")
            .uri(URI.create("https://www.example.com"))
            .build();

        var signedRequest = HttpBearerAuthSigner.INSTANCE.sign(request, tokenIdentity, AuthProperties.empty()).get();
        var authHeader = signedRequest.headers().firstValue("authorization");
        assertEquals(authHeader, "Bearer token");
    }

    @Test
    void overwritesExistingHeader() throws ExecutionException, InterruptedException {
        var tokenIdentity = TokenIdentity.create("token");
        var request = HttpRequest.builder()
            .httpVersion(HttpVersion.HTTP_1_1)
            .method("PUT")
            .headers(HttpHeaders.of(Map.of("Authorization", List.of("FOO", "BAR"))))
            .uri(URI.create("https://www.example.com"))
            .build();

        var signedRequest = HttpBearerAuthSigner.INSTANCE.sign(request, tokenIdentity, AuthProperties.empty()).get();
        var authHeader = signedRequest.headers().firstValue("authorization");
        assertEquals(authHeader, "Bearer token");
    }
}
