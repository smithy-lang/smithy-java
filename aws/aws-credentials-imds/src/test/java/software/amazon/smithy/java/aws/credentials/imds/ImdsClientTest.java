/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.aws.credentials.imds;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;

class ImdsClientTest {

    private static final URI ENDPOINT = URI.create("http://169.254.169.254");

    @Test
    void fetchesCredentialsSuccessfully() throws Exception {
        var client = new ImdsClient(ENDPOINT,
                mockClient(
                        response(200, "mock-token"), // PUT token
                        response(200, "my-role"), // GET discovery
                        response(200,
                                "{\"Code\":\"Success\",\"AccessKeyId\":\"AK\",\"SecretAccessKey\":\"SK\",\"Token\":\"T\",\"Expiration\":\"2099-01-01T00:00:00Z\",\"AccountId\":\"123\"}")));
        String json = client.fetchCredentials(null);
        assertNotNull(json);
        assertTrue(json.contains("AK"));
        assertTrue(json.contains("123"));
    }

    @Test
    void usesProvidedProfileName() throws Exception {
        var client = new ImdsClient(ENDPOINT,
                mockClient(
                        response(200, "mock-token"),
                        response(200,
                                "{\"Code\":\"Success\",\"AccessKeyId\":\"AK2\",\"SecretAccessKey\":\"SK2\",\"Token\":\"T\",\"Expiration\":\"2099-01-01T00:00:00Z\"}")));
        String json = client.fetchCredentials("custom-role");
        assertNotNull(json);
        assertTrue(json.contains("AK2"));
    }

    @Test
    void fallsBackToLegacyPathOn404() throws Exception {
        var client = new ImdsClient(ENDPOINT,
                mockClient(
                        response(200, "mock-token"),
                        response(404, ""), // extended discovery 404
                        response(200, "legacy-role"), // legacy discovery
                        response(200,
                                "{\"Code\":\"Success\",\"AccessKeyId\":\"LEG\",\"SecretAccessKey\":\"SK\",\"Token\":\"T\",\"Expiration\":\"2099-01-01T00:00:00Z\"}")));
        String json = client.fetchCredentials(null);
        assertNotNull(json);
        assertTrue(json.contains("LEG"));
    }

    @Test
    void throwsWhenTokenFails() {
        var client = new ImdsClient(ENDPOINT,
                mockClient(
                        response(500, "error"),
                        response(500, "error"),
                        response(500, "error"),
                        response(500, "error") // all retries fail
                ));
        assertThrows(IOException.class, () -> client.fetchCredentials(null));
    }

    private static ImdsClient.SimpleHttpClient mockClient(MockResponse... responses) {
        Deque<MockResponse> queue = new ArrayDeque<>();
        for (MockResponse r : responses) {
            queue.add(r);
        }
        return request -> {
            MockResponse r = queue.poll();
            if (r == null) {
                return response(404, "").toHttpResponse(request);
            }
            return r.toHttpResponse(request);
        };
    }

    private static MockResponse response(int status, String body) {
        return new MockResponse(status, body);
    }

    private record MockResponse(int status, String body) {
        HttpResponse<String> toHttpResponse(HttpRequest request) {
            return new HttpResponse<>() {
                @Override
                public int statusCode() {
                    return status;
                }

                @Override
                public String body() {
                    return body;
                }

                @Override
                public HttpRequest request() {
                    return request;
                }

                @Override
                public Optional<HttpResponse<String>> previousResponse() {
                    return Optional.empty();
                }

                @Override
                public HttpHeaders headers() {
                    return HttpHeaders.of(Map.of(), (a, b) -> true);
                }

                @Override
                public Optional<SSLSession> sslSession() {
                    return Optional.empty();
                }

                @Override
                public URI uri() {
                    return request.uri();
                }

                @Override
                public HttpClient.Version version() {
                    return HttpClient.Version.HTTP_1_1;
                }
            };
        }
    }
}
