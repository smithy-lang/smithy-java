/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.protocoltests.harness;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ConcurrentHashMap;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.io.datastream.DataStream;

final class ServerTestClient {
    private static final ConcurrentHashMap<URI, ServerTestClient> CLIENTS = new ConcurrentHashMap<>();

    private final URI endpoint;
    private final HttpClient httpClient;

    private ServerTestClient(URI endpoint) {
        this.endpoint = endpoint;
        this.httpClient = HttpClient.newHttpClient();
    }

    public static ServerTestClient get(URI endpoint) {
        return CLIENTS.computeIfAbsent(endpoint, ServerTestClient::new);
    }

    SmithyHttpResponse sendRequest(SmithyHttpRequest request) {

        var bodyPublisher = HttpRequest.BodyPublishers.fromPublisher(request.body());

        HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
            .version(switch (request.httpVersion()) {
                case HTTP_1_1 -> HttpClient.Version.HTTP_1_1;
                case HTTP_2 -> HttpClient.Version.HTTP_2;
            })
            .method(request.method(), bodyPublisher)
            .uri(request.uri());

        for (var entry : request.headers().map().entrySet()) {
            for (var value : entry.getValue()) {
                if (!entry.getKey().equals("Content-Length")) {
                    httpRequestBuilder.header(entry.getKey(), value);
                }
            }
        }

        try {
            var response = httpClient.send(httpRequestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return SmithyHttpResponse.builder()
                .statusCode(response.statusCode())
                .body(DataStream.ofBytes(response.body()))
                .headers(response.headers())
                .build();

        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
