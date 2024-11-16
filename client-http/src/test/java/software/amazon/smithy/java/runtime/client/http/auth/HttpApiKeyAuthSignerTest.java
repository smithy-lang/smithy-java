/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import java.util.concurrent.ExecutionException;
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
    void testApiKeyAuthSignerAddsHeaderNoScheme() throws ExecutionException, InterruptedException {
        var authProperties = AuthProperties.builder()
            .put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.HEADER)
            .put(HttpApiKeyAuthScheme.NAME, "x-api-key")
            .build();

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(TEST_REQUEST, TEST_IDENTITY, authProperties).get();
        var authHeader = signedRequest.headers().firstValue("x-api-key");
        assertEquals(authHeader, API_KEY);
    }

    @Test
    void testApiKeyAuthSignerAddsHeaderParamWithCustomScheme() throws ExecutionException, InterruptedException {
        var authProperties = AuthProperties.builder()
            .put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.HEADER)
            .put(HttpApiKeyAuthScheme.NAME, "x-api-key")
            .put(HttpApiKeyAuthScheme.SCHEME, "SCHEME")
            .build();

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(TEST_REQUEST, TEST_IDENTITY, authProperties).get();
        var authHeader = signedRequest.headers().firstValue("x-api-key");
        assertEquals(authHeader, "SCHEME " + API_KEY);
    }

    @Test
    void testOverwritesExistingHeader() throws ExecutionException, InterruptedException {
        var authProperties = AuthProperties.builder()
            .put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.HEADER)
            .put(HttpApiKeyAuthScheme.NAME, "x-api-key")
            .put(HttpApiKeyAuthScheme.SCHEME, "SCHEME")
            .build();
        var updateRequest = TEST_REQUEST.toBuilder().withAddedHeaders("x-api-key", "foo").build();
        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(updateRequest, TEST_IDENTITY, authProperties).get();
        var authHeader = signedRequest.headers().firstValue("x-api-key");
        assertEquals(authHeader, "SCHEME " + API_KEY);
    }

    @Test
    void testApiKeyAuthSignerAddsQueryParam() throws ExecutionException, InterruptedException {
        var authProperties = AuthProperties.builder()
            .put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.QUERY)
            .put(HttpApiKeyAuthScheme.NAME, "apiKey")
            .build();

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(TEST_REQUEST, TEST_IDENTITY, authProperties).get();
        var queryParam = signedRequest.uri().getQuery();
        assertNotNull(queryParam);
        assertEquals(queryParam, "apiKey=my-api-key");
    }

    @Test
    void testApiKeyAuthSignerAddsQueryParamIgnoresScheme() throws ExecutionException, InterruptedException {
        var authProperties = AuthProperties.builder()
            .put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.QUERY)
            .put(HttpApiKeyAuthScheme.NAME, "apiKey")
            .put(HttpApiKeyAuthScheme.SCHEME, "SCHEME")
            .build();

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(TEST_REQUEST, TEST_IDENTITY, authProperties).get();
        var queryParam = signedRequest.uri().getQuery();
        assertNotNull(queryParam);
        assertEquals(queryParam, "apiKey=my-api-key");
    }

    @Test
    void testApiKeyAuthSignerAddsQueryParamsAppendsToExisting() throws ExecutionException, InterruptedException {
        var authProperties = AuthProperties.builder()
            .put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.QUERY)
            .put(HttpApiKeyAuthScheme.NAME, "apiKey")
            .build();
        var updatedRequest = TEST_REQUEST.toBuilder().uri(URI.create("https://www.example.com?x=1")).build();

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(updatedRequest, TEST_IDENTITY, authProperties).get();
        var queryParam = signedRequest.uri().getQuery();
        assertNotNull(queryParam);
        assertEquals(queryParam, "x=1&apiKey=my-api-key");
    }

    @Test
    void testOverwritesExistingQuery() throws ExecutionException, InterruptedException {
        var authProperties = AuthProperties.builder()
            .put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.QUERY)
            .put(HttpApiKeyAuthScheme.NAME, "apiKey")
            .build();
        var updatedRequest = TEST_REQUEST.toBuilder().uri(URI.create("https://www.example.com?x=1&apiKey=foo")).build();

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(updatedRequest, TEST_IDENTITY, authProperties).get();
        var queryParam = signedRequest.uri().getQuery();
        assertNotNull(queryParam);
        assertEquals(queryParam, "x=1&apiKey=my-api-key");
    }
}
