/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.auth.api.identity.ApiKeyIdentity;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.model.traits.HttpApiKeyAuthTrait;

public class HttpApiKeyAuthSignerTest {
    private static final String API_KEY = "my-api-key";
    private static final ApiKeyIdentity TEST_IDENTITY = ApiKeyIdentity.create(API_KEY);
    private static final HttpRequest TEST_REQUEST = HttpRequest.builder()
            .httpVersion(HttpVersion.HTTP_1_1)
            .method("PUT")
            .uri(URI.create("https://www.example.com"))
            .build();

    @Test
    void testApiKeyAuthSignerAddsHeaderNoScheme() {
        var authProperties = Context.create();
        authProperties.put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.HEADER);
        authProperties.put(HttpApiKeyAuthScheme.NAME, "x-api-key");

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(TEST_REQUEST, TEST_IDENTITY, authProperties);
        var authHeader = signedRequest.headers().firstValue("x-api-key");
        assertEquals(authHeader, API_KEY);
    }

    @Test
    void testApiKeyAuthSignerAddsHeaderParamWithCustomScheme() {
        var authProperties = Context.create();
        authProperties.put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.HEADER);
        authProperties.put(HttpApiKeyAuthScheme.NAME, "x-api-key");
        authProperties.put(HttpApiKeyAuthScheme.SCHEME, "SCHEME");

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(TEST_REQUEST, TEST_IDENTITY, authProperties);
        var authHeader = signedRequest.headers().firstValue("x-api-key");
        assertEquals(authHeader, "SCHEME " + API_KEY);
    }

    @Test
    void testOverwritesExistingHeader() {
        var authProperties = Context.create();
        authProperties.put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.HEADER);
        authProperties.put(HttpApiKeyAuthScheme.NAME, "x-api-key");
        authProperties.put(HttpApiKeyAuthScheme.SCHEME, "SCHEME");

        var updateRequest = TEST_REQUEST.toBuilder().withAddedHeader("x-api-key", "foo").build();
        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(updateRequest, TEST_IDENTITY, authProperties);
        var authHeader = signedRequest.headers().firstValue("x-api-key");
        assertEquals(authHeader, "SCHEME " + API_KEY);
    }

    @Test
    void testApiKeyAuthSignerAddsQueryParam() {
        var authProperties = Context.create();
        authProperties.put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.QUERY);
        authProperties.put(HttpApiKeyAuthScheme.NAME, "apiKey");

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(TEST_REQUEST, TEST_IDENTITY, authProperties);
        var queryParam = signedRequest.uri().getQuery();
        assertNotNull(queryParam);
        assertEquals(queryParam, "apiKey=my-api-key");
    }

    @Test
    void testApiKeyAuthSignerAddsQueryParamIgnoresScheme() {
        var authProperties = Context.create();
        authProperties.put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.QUERY);
        authProperties.put(HttpApiKeyAuthScheme.NAME, "apiKey");
        authProperties.put(HttpApiKeyAuthScheme.SCHEME, "SCHEME");

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(TEST_REQUEST, TEST_IDENTITY, authProperties);
        var queryParam = signedRequest.uri().getQuery();
        assertNotNull(queryParam);
        assertEquals(queryParam, "apiKey=my-api-key");
    }

    @Test
    void testApiKeyAuthSignerAddsQueryParamsAppendsToExisting() {
        var authProperties = Context.create();
        authProperties.put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.QUERY);
        authProperties.put(HttpApiKeyAuthScheme.NAME, "apiKey");

        var updatedRequest = TEST_REQUEST.toBuilder().uri(URI.create("https://www.example.com?x=1")).build();

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(updatedRequest, TEST_IDENTITY, authProperties);
        var queryParam = signedRequest.uri().getQuery();
        assertNotNull(queryParam);
        assertEquals(queryParam, "x=1&apiKey=my-api-key");
    }

    @Test
    void testOverwritesExistingQuery() {
        var authProperties = Context.create();
        authProperties.put(HttpApiKeyAuthScheme.IN, HttpApiKeyAuthTrait.Location.QUERY);
        authProperties.put(HttpApiKeyAuthScheme.NAME, "apiKey");

        var updatedRequest = TEST_REQUEST.toBuilder().uri(URI.create("https://www.example.com?x=1&apiKey=foo")).build();

        var signedRequest = HttpApiKeyAuthSigner.INSTANCE.sign(updatedRequest, TEST_IDENTITY, authProperties);
        var queryParam = signedRequest.uri().getQuery();
        assertNotNull(queryParam);
        assertEquals(queryParam, "x=1&apiKey=my-api-key");
    }
}
