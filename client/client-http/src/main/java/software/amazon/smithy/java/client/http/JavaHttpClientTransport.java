/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.ClientTransportFactory;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.core.error.ConnectTimeoutException;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * A client transport that uses Java's built-in {@link HttpClient} to send {@link HttpRequest} and return
 * {@link HttpResponse}.
 */
public class JavaHttpClientTransport implements ClientTransport<HttpRequest, HttpResponse> {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(JavaHttpClientTransport.class);
    private final HttpClient client;

    static {
        // For some reason, this can't just be done in the constructor to always take effect.
        setHostProperties();
    }

    private static void setHostProperties() {
        // Allow clients to set Host header. This has to be done using a system property and can't be done per/client.
        var currentValues = System.getProperty("jdk.httpclient.allowRestrictedHeaders");
        if (currentValues == null || currentValues.isEmpty()) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
        } else if (!containsHost(currentValues)) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", currentValues + ",host");
        }
    }

    public JavaHttpClientTransport() {
        this(HttpClient.newHttpClient());
    }

    /**
     * @param client Java client to use.
     */
    public JavaHttpClientTransport(HttpClient client) {
        this.client = client;
        setHostProperties();
    }

    private static boolean containsHost(String currentValues) {
        int length = currentValues.length();
        for (int i = 0; i < length; i++) {
            char c = currentValues.charAt(i);
            // Check if "host" starts at the current position.
            if ((c == 'h' || c == 'H') && i + 3 < length
                    && (currentValues.charAt(i + 1) == 'o')
                    && (currentValues.charAt(i + 2) == 's')
                    && (currentValues.charAt(i + 3) == 't')) {
                // Ensure "t" is at the end or followed by a comma.
                if (i + 4 == length || currentValues.charAt(i + 4) == ',') {
                    return true;
                }
            }
            // Skip to the next comma or end of string.
            while (i < length && currentValues.charAt(i) != ',') {
                i++;
            }
        }
        return false;
    }

    @Override
    public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
        return HttpMessageExchange.INSTANCE;
    }

    @Override
    public CompletableFuture<HttpResponse> send(Context context, HttpRequest request) {
        return sendRequest(createJavaRequest(context, request));
    }

    private java.net.http.HttpRequest createJavaRequest(Context context, HttpRequest request) {
        java.net.http.HttpRequest.BodyPublisher bodyPublisher;
        if (request.body().hasKnownLength()) {
            if (request.body().contentLength() == 0) {
                bodyPublisher = java.net.http.HttpRequest.BodyPublishers.noBody();
            } else {
                bodyPublisher = java.net.http.HttpRequest.BodyPublishers.ofByteArray(
                        ByteBufferUtils.getBytes(request.body().waitForByteBuffer()));
            }
        } else {
            bodyPublisher = java.net.http.HttpRequest.BodyPublishers.fromPublisher(request.body());
        }

        java.net.http.HttpRequest.Builder httpRequestBuilder = java.net.http.HttpRequest.newBuilder()
                .version(smithyToHttpVersion(request.httpVersion()))
                .method(request.method(), bodyPublisher)
                .uri(request.uri());

        Duration requestTimeout = context.get(HttpContext.HTTP_REQUEST_TIMEOUT);

        if (requestTimeout != null) {
            httpRequestBuilder.timeout(requestTimeout);
        }

        // Any explicitly set headers overwrite existing headers, they do not merge.
        for (var entry : request.headers().map().entrySet()) {
            for (var value : entry.getValue()) {
                httpRequestBuilder.setHeader(entry.getKey(), value);
            }
        }

        return httpRequestBuilder.build();
    }

    private CompletableFuture<HttpResponse> sendRequest(java.net.http.HttpRequest request) {
        return client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofPublisher())
                .thenApply(this::createSmithyResponse)
                .exceptionally(e -> {
                    if (e instanceof HttpConnectTimeoutException) {
                        throw new ConnectTimeoutException(e);
                    }
                    // The client pipeline also does this remapping, but to adhere to the required contract of
                    // ClientTransport, we remap here too if needed.
                    throw ClientTransport.remapExceptions(e);
                });
    }

    private HttpResponse createSmithyResponse(java.net.http.HttpResponse<Flow.Publisher<List<ByteBuffer>>> response) {
        var headerMap = response.headers().map();
        LOGGER.trace("Got response: {}; headers: {}", response, headerMap);

        var headers = HttpHeaders.of(headerMap);
        var length = headers.contentLength();
        long adaptedLength = length == null ? -1 : length;

        return HttpResponse.builder()
                .httpVersion(javaToSmithyVersion(response.version()))
                .statusCode(response.statusCode())
                .headers(headers)
                .body(new HttpClientDataStream(response.body(), adaptedLength, headers.contentType()))
                .build();
    }

    private static HttpClient.Version smithyToHttpVersion(HttpVersion version) {
        return switch (version) {
            case HTTP_1_1 -> HttpClient.Version.HTTP_1_1;
            case HTTP_2 -> HttpClient.Version.HTTP_2;
            default -> throw new UnsupportedOperationException("Unsupported HTTP version: " + version);
        };
    }

    private static HttpVersion javaToSmithyVersion(HttpClient.Version version) {
        return switch (version) {
            case HTTP_1_1 -> HttpVersion.HTTP_1_1;
            case HTTP_2 -> HttpVersion.HTTP_2;
            default -> throw new UnsupportedOperationException("Unsupported HTTP version: " + version);
        };
    }

    public static final class Factory implements ClientTransportFactory<HttpRequest, HttpResponse> {
        @Override
        public String name() {
            return "http-java";
        }

        // TODO: Determine what configuration is actually needed.
        @Override
        public JavaHttpClientTransport createTransport(Document node) {
            return new JavaHttpClientTransport();
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }
}
