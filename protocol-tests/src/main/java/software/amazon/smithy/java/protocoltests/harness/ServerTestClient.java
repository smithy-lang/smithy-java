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
            })
            .method(request.method(), bodyPublisher)
            .uri(request.uri());

        for (var entry : request.headers()) {
            for (var value : entry.getValue()) {
                if (!entry.getKey().equals("content-length")) {
                    httpRequestBuilder.header(entry.getKey(), value);
                }
            }
        }

        try {
            var response = httpClient.send(
                httpRequestBuilder.build(),
                java.net.http.HttpResponse.BodyHandlers.ofByteArray()
            );
            return HttpResponse.builder()
                .statusCode(response.statusCode())
                .body(DataStream.ofBytes(response.body()))
                .headers(HttpHeaders.of(response.headers().map()))
                .build();

        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
