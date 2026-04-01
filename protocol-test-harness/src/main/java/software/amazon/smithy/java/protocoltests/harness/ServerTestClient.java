/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

final class ServerTestClient {
    private static final ConcurrentHashMap<URI, ServerTestClient> CLIENTS = new ConcurrentHashMap<>();

    private final URI endpoint;
    private final HttpClient httpClient;

    private ServerTestClient(URI endpoint) {
        this.endpoint = endpoint;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    }

    public static ServerTestClient get(URI endpoint) {
        return CLIENTS.computeIfAbsent(endpoint, ServerTestClient::new);
    }

    HttpResponse sendRequest(HttpRequest request) {

        var bodyPublisher = java.net.http.HttpRequest.BodyPublishers.fromPublisher(request.body());

        java.net.http.HttpRequest.Builder httpRequestBuilder = java.net.http.HttpRequest.newBuilder()
                .version(switch (request.httpVersion()) {
                    case HTTP_1_1 -> HttpClient.Version.HTTP_1_1;
                    case HTTP_2 -> HttpClient.Version.HTTP_2;
                    default -> throw new UnsupportedOperationException(request.httpVersion() + " is not supported");
                })
                .method(request.method(), bodyPublisher)
                .uri(request.uri().toURI());

        request.headers().forEachEntry(httpRequestBuilder, (b, name, value) -> {
            // Header names in HttpHeaders from Smithy are always canonicalized, so check by reference
            if (!name.equals(HeaderName.CONTENT_LENGTH.name())) {
                b.header(name, value);
            }
        });

        try {
            var response = httpClient.send(
                    httpRequestBuilder.build(),
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            return HttpResponse.create()
                    .setStatusCode(response.statusCode())
                    .setBody(DataStream.ofBytes(response.body()))
                    .setHeaders(HttpHeaders.of(response.headers().map()))
                    .toUnmodifiable();

        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
