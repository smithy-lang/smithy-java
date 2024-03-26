/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import software.amazon.smithy.java.runtime.http.api.HttpClientCall;
import software.amazon.smithy.java.runtime.http.api.HttpProperty;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpClient;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpVersion;

/**
 * Provides a SmithyHttpClient implementation using Java's {@link HttpClient}.
 */
public final class JavaHttpClient implements SmithyHttpClient {

    private static final System.Logger LOGGER = System.getLogger(JavaHttpClient.class.getName());
    private final HttpClient httpClient;

    /**
     * @param httpClient HTTP client to wrap.
     */
    public JavaHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public SmithyHttpResponse send(HttpClientCall call) {
        var request = call.request();
        var properties = call.properties();
        var bodyPublisher = HttpRequest.BodyPublishers.ofInputStream(request.body()::inputStream);

        HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
                .version(smithyToHttpVersion(request.httpVersion()))
                .method(request.method(), bodyPublisher)
                .uri(request.uri());

        Duration requestTimeout = properties.get(HttpProperty.REQUEST_TIMEOUT);

        if (requestTimeout != null) {
            httpRequestBuilder.timeout(requestTimeout);
        }

        for (var entry : request.headers().map().entrySet()) {
            for (var value : entry.getValue()) {
                httpRequestBuilder.header(entry.getKey(), value);
            }
        }
        HttpRequest httpRequest = httpRequestBuilder.build();

        LOGGER.log(System.Logger.Level.TRACE, () -> "Sending request " + httpRequest + " " + httpRequest.headers());

        try {
            var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            LOGGER.log(System.Logger.Level.TRACE,
                    () -> "Got response: " + response + "; headers: " + response.headers().map());
            return SmithyHttpResponse.builder()
                    .httpVersion(javaToSmithyVersion(response.version()))
                    .statusCode(response.statusCode())
                    .headers(response.headers())
                    .body(response.body())
                    .build();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e); // todo
        }
    }

    private HttpClient.Version smithyToHttpVersion(SmithyHttpVersion version) {
        return switch (version) {
            case HTTP_1_1 -> HttpClient.Version.HTTP_1_1;
            case HTTP_2 -> HttpClient.Version.HTTP_2;
            default -> throw new UnsupportedOperationException("Unsupported HTTP version: " + version);
        };
    }

    private SmithyHttpVersion javaToSmithyVersion(HttpClient.Version version) {
        return switch (version) {
            case HTTP_1_1 -> SmithyHttpVersion.HTTP_1_1;
            case HTTP_2 -> SmithyHttpVersion.HTTP_2;
            default -> throw new UnsupportedOperationException("Unsupported HTTP version: " + version);
        };
    }
}
