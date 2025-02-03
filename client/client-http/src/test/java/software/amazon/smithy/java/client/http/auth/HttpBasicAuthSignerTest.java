/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.AuthProperties;
import software.amazon.smithy.java.auth.api.identity.LoginIdentity;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;

public class HttpBasicAuthSignerTest {
    @Test
    void testBasicAuthSigner() throws ExecutionException, InterruptedException {
        var username = "username";
        var password = "password";
        var testIdentity = LoginIdentity.create(username, password);
        var request = HttpRequest.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .method("PUT")
                .uri(URI.create("https://www.example.com"))
                .build();

        var expectedHeader = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        var signedRequest = HttpBasicAuthSigner.INSTANCE.sign(request, testIdentity, AuthProperties.empty()).get();
        var authHeader = signedRequest.headers().firstValue("authorization");
        assertEquals(authHeader, expectedHeader);
    }

    @Test
    void overwritesExistingHeader() throws ExecutionException, InterruptedException {
        var username = "username";
        var password = "password";
        var testIdentity = LoginIdentity.create(username, password);
        var request = HttpRequest.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .method("PUT")
                .headers(HttpHeaders.of(Map.of("Authorization", List.of("FOO", "BAR"))))
                .uri(URI.create("https://www.example.com"))
                .build();

        var expectedHeader = "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        var signedRequest = HttpBasicAuthSigner.INSTANCE.sign(request, testIdentity, AuthProperties.empty()).get();
        var authHeader = signedRequest.headers().firstValue("authorization");
        assertEquals(authHeader, expectedHeader);
    }
}
