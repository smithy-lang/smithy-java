/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.runtime.http.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.runtime.auth.api.AuthProperties;
import software.amazon.smithy.java.runtime.auth.api.identity.LoginIdentity;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpVersion;

public class HttpBasicAuthSignerTest {
    @Test
    void testBasicAuthSigner() throws ExecutionException, InterruptedException {
        var username = "username";
        var password = "password";
        var testIdentity = LoginIdentity.create(username, password);
        var request = SmithyHttpRequest.builder()
            .httpVersion(SmithyHttpVersion.HTTP_1_1)
            .method("PUT")
            .uri(URI.create("https://www.example.com"))
            .build();

        var expectedHeader = "Basic " + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        var signedRequest = HttpBasicAuthSigner.INSTANCE.sign(request, testIdentity, AuthProperties.empty()).get();
        var authHeader = signedRequest.headers().map().get("Authorization");
        assertNotNull(authHeader);
        assertEquals(authHeader.get(0), expectedHeader);
    }

    @Test
    void overwritesExistingHeader() throws ExecutionException, InterruptedException {
        var username = "username";
        var password = "password";
        var testIdentity = LoginIdentity.create(username, password);
        var request = SmithyHttpRequest.builder()
            .httpVersion(SmithyHttpVersion.HTTP_1_1)
            .method("PUT")
            .headers(HttpHeaders.of(Map.of("Authorization", List.of("FOO", "BAR")), (k, v) -> true))
            .uri(URI.create("https://www.example.com"))
            .build();

        var expectedHeader = "Basic " + Base64.getEncoder()
            .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        var signedRequest = HttpBasicAuthSigner.INSTANCE.sign(request, testIdentity, AuthProperties.empty()).get();
        var authHeader = signedRequest.headers().map().get("Authorization");
        assertNotNull(authHeader);
        assertEquals(authHeader.get(0), expectedHeader);
    }
}
