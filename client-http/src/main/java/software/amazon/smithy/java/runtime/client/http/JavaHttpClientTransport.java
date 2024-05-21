/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.runtime.client.core.ClientCall;
import software.amazon.smithy.java.runtime.client.core.ClientProtocol;
import software.amazon.smithy.java.runtime.client.core.ClientTransport;
import software.amazon.smithy.java.runtime.client.core.SraPipeline;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.schema.SerializableStruct;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpVersion;

/**
 * A client transport that uses Java's built-in {@link HttpClient} and protocols that use {@link SmithyHttpRequest}
 * and {@link SmithyHttpResponse}.
 */
public class JavaHttpClientTransport implements ClientTransport, ClientTransport.SraCompliant {

    private static final System.Logger LOGGER = System.getLogger(JavaHttpClientTransport.class.getName());

    private final HttpClient client;
    private final ClientProtocol<SmithyHttpRequest, SmithyHttpResponse> protocol;

    /**
     * @param client   Java client to use.
     * @param protocol Underlying protocol to handle serialization.
     */
    public JavaHttpClientTransport(HttpClient client, ClientProtocol<SmithyHttpRequest, SmithyHttpResponse> protocol) {
        this.client = client;
        this.protocol = protocol;
    }

    @Override
    public <I extends SerializableStruct, O extends SerializableStruct> CompletableFuture<O> send(
        ClientCall<I, O> call
    ) {
        return SraPipeline.send(call, protocol, request -> {
            LOGGER.log(System.Logger.Level.TRACE, "Sending HTTP request: %s", request.startLine());
            var javaRequest = createJavaRequest(call.context(), request);
            return sendRequest(javaRequest).thenApply(response -> {
                LOGGER.log(System.Logger.Level.TRACE, "Got HTTP response: %s", response.startLine());
                // TODO: Should this use protocol.responseKey()?
                // TODO: Why is this put in context here?
                // TODO: Why not in SraPipeline?
                // TODO: HTTP_REQUEST is not put in context anywhere it seems?
                call.context().put(HttpContext.HTTP_RESPONSE, response);
                return response;
            });
        });
    }

    private HttpRequest createJavaRequest(Context context, SmithyHttpRequest request) {
        var bodyPublisher = HttpRequest.BodyPublishers.ofInputStream(request.body()::inputStream);

        HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder()
            .version(smithyToHttpVersion(request.httpVersion()))
            .method(request.method(), bodyPublisher)
            .uri(request.uri());

        Duration requestTimeout = context.get(HttpContext.HTTP_REQUEST_TIMEOUT);

        if (requestTimeout != null) {
            httpRequestBuilder.timeout(requestTimeout);
        }

        for (var entry : request.headers().map().entrySet()) {
            for (var value : entry.getValue()) {
                httpRequestBuilder.header(entry.getKey(), value);
            }
        }

        return httpRequestBuilder.build();
    }

    private CompletableFuture<SmithyHttpResponse> sendRequest(HttpRequest request) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
            .thenApply(this::createSmithyResponse);
    }

    private SmithyHttpResponse createSmithyResponse(HttpResponse<InputStream> response) {
        LOGGER.log(
            System.Logger.Level.TRACE,
            "Got response: %s; headers: %s",
            response,
            response.headers().map()
        );
        return SmithyHttpResponse.builder()
            .httpVersion(javaToSmithyVersion(response.version()))
            .statusCode(response.statusCode())
            .headers(response.headers())
            .body(response.body())
            .build();
    }

    private static HttpClient.Version smithyToHttpVersion(SmithyHttpVersion version) {
        return switch (version) {
            case HTTP_1_1 -> HttpClient.Version.HTTP_1_1;
            case HTTP_2 -> HttpClient.Version.HTTP_2;
            default -> throw new UnsupportedOperationException("Unsupported HTTP version: " + version);
        };
    }

    private static SmithyHttpVersion javaToSmithyVersion(HttpClient.Version version) {
        return switch (version) {
            case HTTP_1_1 -> SmithyHttpVersion.HTTP_1_1;
            case HTTP_2 -> SmithyHttpVersion.HTTP_2;
            default -> throw new UnsupportedOperationException("Unsupported HTTP version: " + version);
        };
    }
}
