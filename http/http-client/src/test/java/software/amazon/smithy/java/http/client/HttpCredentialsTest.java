/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.uri.SmithyUri;

class HttpCredentialsTest {

    @Test
    void basicAddsAuthHeader() {
        var creds = new HttpCredentials.Basic("user", "pass");
        var request = HttpRequest.create().setMethod("GET").setUri(SmithyUri.of("http://example.com"));
        boolean result = creds.authenticate(request, null);

        assertTrue(result);

        var built = request;
        var authHeader = built.headers().firstValue("Authorization");
        var expected = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, authHeader);
    }

    @Test
    void basicForProxyAddsProxyAuthHeader() {
        var creds = new HttpCredentials.Basic("user", "pass", true);
        var request = HttpRequest.create().setMethod("GET").setUri(SmithyUri.of("http://example.com"));
        boolean result = creds.authenticate(request, null);

        assertTrue(result);
        var built = request;
        var authHeader = built.headers().firstValue("Proxy-Authorization");
        var expected = "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
        assertEquals(expected, authHeader);
    }

    @Test
    void basicReturnsFalseOnChallenge() {
        var creds = new HttpCredentials.Basic("user", "pass");
        var request = HttpRequest.create().setMethod("GET").setUri(SmithyUri.of("http://example.com"));
        var priorResponse = HttpResponse.create().setStatusCode(401);
        boolean result = creds.authenticate(request, priorResponse);

        assertFalse(result);
    }

    @Test
    void bearerAddsAuthHeader() {
        var creds = new HttpCredentials.Bearer("my-token");
        var request = HttpRequest.create().setMethod("GET").setUri(SmithyUri.of("http://example.com"));
        boolean result = creds.authenticate(request, null);

        assertTrue(result);
        var built = request;
        assertEquals("Bearer my-token", built.headers().firstValue("Authorization"));
    }

    @Test
    void bearerForProxyAddsProxyAuthHeader() {
        var creds = new HttpCredentials.Bearer("my-token", true);
        var request = HttpRequest.create().setMethod("GET").setUri(SmithyUri.of("http://example.com"));
        boolean result = creds.authenticate(request, null);

        assertTrue(result);
        var built = request;
        assertEquals("Bearer my-token", built.headers().firstValue("Proxy-Authorization"));
    }

    @Test
    void bearerReturnsFalseOnChallenge() {
        var creds = new HttpCredentials.Bearer("my-token");
        var request = HttpRequest.create().setMethod("GET").setUri(SmithyUri.of("http://example.com"));
        var priorResponse = HttpResponse.create().setStatusCode(401);
        boolean result = creds.authenticate(request, priorResponse);

        assertFalse(result);
    }
}
