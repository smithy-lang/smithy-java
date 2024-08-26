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
import software.amazon.smithy.java.runtime.auth.api.identity.ApiKeyIdentity;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpVersion;
import software.amazon.smithy.model.traits.HttpApiKeyAuthTrait;

public class HttpApiKeyAuthSignerTest {
    private static final String API_KEY = "my-api-key";
    private static final ApiKeyIdentity TEST_IDENTITY = ApiKeyIdentity.create(API_KEY);
    private static final SmithyHttpRequest TEST_REQUEST = SmithyHttpRequest.builder()
        .httpVersion(SmithyHttpVersion.HTTP_1_1)
        .method("PUT")
        .uri(URI.create("https://www.example.com"))
        .build();

    @Test
    void testApiKeyAuthSignerAddsHeaderNoScheme() {
        var authProperties = AuthProperties.builder()
            .put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.HEADER)
            .put(HttpApiKeyAuthScheme.NAME, "x-api-key")
            .build();

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(TEST_REQUEST, TEST_IDENTITY, authProperties);
        var authHeader = signedRequest.headers().map().get("x-api-key");
        assertNotNull(authHeader);
        assertEquals(authHeader.get(0), API_KEY);
    }

    @Test
    void testApiKeyAuthSignerAddsHeaderParamWithCustomScheme() {
        var authProperties = AuthProperties.builder()
            .put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.HEADER)
            .put(HttpApiKeyAuthScheme.NAME, "x-api-key")
            .put(HttpApiKeyAuthScheme.SCHEME, "SCHEME")
            .build();

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(TEST_REQUEST, TEST_IDENTITY, authProperties);
        var authHeader = signedRequest.headers().map().get("x-api-key");
        assertNotNull(authHeader);
        assertEquals(authHeader.get(0), "SCHEME " + API_KEY);
    }

    @Test
    void testApiKeyAuthSignerAddsQueryParam() {
        var authProperties = AuthProperties.builder()
            .put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.QUERY)
            .put(HttpApiKeyAuthScheme.NAME, "apiKey")
            .build();

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(TEST_REQUEST, TEST_IDENTITY, authProperties);
        var queryParam = signedRequest.uri().getQuery();
        assertNotNull(queryParam);
        assertEquals(queryParam, "apiKey=my-api-key");
    }

    @Test
    void testApiKeyAuthSignerAddsQueryParamIgnoresScheme() {
        var authProperties = AuthProperties.builder()
            .put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.QUERY)
            .put(HttpApiKeyAuthScheme.NAME, "apiKey")
            .put(HttpApiKeyAuthScheme.SCHEME, "SCHEME")
            .build();

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(TEST_REQUEST, TEST_IDENTITY, authProperties);
        var queryParam = signedRequest.uri().getQuery();
        assertNotNull(queryParam);
        assertEquals(queryParam, "apiKey=my-api-key");
    }

    @Test
    void testApiKeyAuthSignerAddsQueryParamsAppendsToExisting() {
        var authProperties = AuthProperties.builder()
            .put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.QUERY)
            .put(HttpApiKeyAuthScheme.NAME, "apiKey")
            .build();
        var updatedRequest = TEST_REQUEST.withUri(URI.create("https://www.example.com?x=1"));

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(updatedRequest, TEST_IDENTITY, authProperties);
        var queryParam = signedRequest.uri().getQuery();
        assertNotNull(queryParam);
        assertEquals(queryParam, "x=1&apiKey=my-api-key");
    }
}
