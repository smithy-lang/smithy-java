/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.http;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.runtime.client.core.ClientTransport;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpRequest;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpResponse;
import software.amazon.smithy.java.runtime.http.api.SmithyHttpVersion;

/**
 * A client transport that uses Java's built-in {@link HttpClient} to send {@link SmithyHttpRequest} and return
 * {@link SmithyHttpResponse}.
 */
public class JavaHttpClientTransport implements ClientTransport<SmithyHttpRequest, SmithyHttpResponse> {

    private static final System.Logger LOGGER = System.getLogger(JavaHttpClientTransport.class.getName());
    private final HttpClient client;

    public JavaHttpClientTransport() {
        this(HttpClient.newHttpClient());
    }

    /**
     * @param client Java client to use.
     */
    public JavaHttpClientTransport(HttpClient client) {
        this.client = client;
    }

    @Override
    public Class<SmithyHttpRequest> requestClass() {
        return SmithyHttpRequest.class;
    }

    @Override
    public Class<SmithyHttpResponse> responseClass() {
        return SmithyHttpResponse.class;
    }

    @Override
    public CompletableFuture<SmithyHttpResponse> send(Context context, SmithyHttpRequest request) {
        return sendRequest(createJavaRequest(context, request));
    }

    private HttpRequest createJavaRequest(Context context, SmithyHttpRequest request) {
        var bodyPublisher = HttpRequest.BodyPublishers.fromPublisher(request.body());

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
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofPublisher())
            .thenApply(this::createSmithyResponse);
    }

    private SmithyHttpResponse createSmithyResponse(HttpResponse<Flow.Publisher<List<ByteBuffer>>> response) {
        LOGGER.log(
            System.Logger.Level.TRACE,
            () -> "Got response: " + response + "; headers: " + response.headers().map()
        );
        return SmithyHttpResponse.builder()
            .httpVersion(javaToSmithyVersion(response.version()))
            .statusCode(response.statusCode())
            .headers(response.headers())
            .body(new ListByteBufferToByteBuffer(response.body())) // Flatten the List<ByteBuffer> to ByteBuffer.
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

    private record ListByteBufferToByteBuffer(Flow.Publisher<List<ByteBuffer>> originalPublisher)
        implements Flow.Publisher<ByteBuffer> {
        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            originalPublisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscriber.onSubscribe(subscription);
                }

                @Override
                public void onNext(List<ByteBuffer> item) {
                    // TODO: subscriber.onNext should only be called as many times as requested by the subscription
                    item.forEach(subscriber::onNext);
                }

                @Override
                public void onError(Throwable throwable) {
                    subscriber.onError(throwable);
                }

                @Override
                public void onComplete() {
                    subscriber.onComplete();
                }
            });
        }
    }
}
