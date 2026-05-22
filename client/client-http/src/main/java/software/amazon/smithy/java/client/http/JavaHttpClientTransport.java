/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import static java.net.http.HttpResponse.BodyHandler;
import static java.net.http.HttpResponse.BodySubscriber;
import static java.net.http.HttpResponse.BodySubscribers;
import static java.net.http.HttpResponse.ResponseInfo;
import static software.amazon.smithy.java.http.api.HttpHeaders.HeaderWithValueConsumer;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.ClientTransportFactory;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.core.error.ConnectTimeoutException;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Smithy HTTP transport backed by Java's built-in {@link HttpClient}.
 *
 * <p>Uses a direct {@link ByteBuffer} request-body fast path for replayable in-memory bodies,
 * a small-body response subscriber for known small responses, and a streaming bridge for larger
 * response bodies. When this transport builds its own {@link HttpClient}, it also owns and
 * closes the attached virtual-thread executor.
 */
public final class JavaHttpClientTransport implements ClientTransport<HttpRequest, HttpResponse> {

    private static final int SMALL_RESPONSE_BODY_FAST_PATH_THRESHOLD = 1024 * 256; // 256 KB

    // Drop content-length
    private static final HeaderWithValueConsumer<java.net.http.HttpRequest.Builder> VALUE_CONSUMER = (b, n, v) -> {
        if (n != HeaderName.CONTENT_LENGTH.name()) {
            b.header(n, v);
        }
    };

    private static final InternalLogger LOGGER = InternalLogger.getLogger(JavaHttpClientTransport.class);
    private final HttpClient client;
    private final Duration defaultRequestTimeout;
    private final ExecutorService ownedExecutor;

    static {
        setHostProperties();
        setIfUnset("jdk.httpclient.maxframesize", "65536");
        setIfUnset("jdk.httpclient.bufsize", "65536");
    }

    private static void setHostProperties() {
        var currentValues = System.getProperty("jdk.httpclient.allowRestrictedHeaders");
        if (currentValues == null || currentValues.isEmpty()) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
        } else if (!containsHost(currentValues)) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", currentValues + ",host");
        }
    }

    private static void setIfUnset(String name, String value) {
        if (System.getProperty(name) == null) {
            System.setProperty(name, value);
        }
    }

    public JavaHttpClientTransport() {
        this.ownedExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.client = HttpClient.newBuilder().executor(ownedExecutor).build();
        this.defaultRequestTimeout = null;
        setHostProperties();
    }

    public JavaHttpClientTransport(HttpClient client) {
        this(client, null);
    }

    public JavaHttpClientTransport(HttpClient client, Duration defaultRequestTimeout) {
        this.client = client;
        this.ownedExecutor = null;
        this.defaultRequestTimeout = defaultRequestTimeout;
        setHostProperties();
    }

    private JavaHttpClientTransport(HttpClient client, ExecutorService ownedExecutor, Duration defaultRequestTimeout) {
        this.client = client;
        this.ownedExecutor = ownedExecutor;
        this.defaultRequestTimeout = defaultRequestTimeout;
        setHostProperties();
    }

    // Case-insensitive scan for "host" in a comma-separated jdk.httpclient.allowRestrictedHeaders list.
    private static boolean containsHost(String value) {
        int start = 0;
        while (start < value.length()) {
            int end = value.indexOf(',', start);
            if (end == -1) {
                end = value.length();
            }

            // Skip leading spaces inside the comma-separated token.
            while (start < end && value.charAt(start) == ' ') {
                start++;
            }

            int len = end - start;
            if (len == 4
                    && ((value.charAt(start) | 0x20) == 'h')
                    && ((value.charAt(start + 1) | 0x20) == 'o')
                    && ((value.charAt(start + 2) | 0x20) == 's')
                    && ((value.charAt(start + 3) | 0x20) == 't')) {
                return true;
            }

            start = end + 1;
        }

        return false;
    }

    @Override
    public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
        return HttpMessageExchange.INSTANCE;
    }

    @Override
    public HttpResponse send(Context context, HttpRequest request) {
        return sendRequest(createJavaRequest(context, request));
    }

    /**
     * Convert a Smithy {@link HttpRequest} into a JDK {@link java.net.http.HttpRequest}.
     */
    private java.net.http.HttpRequest createJavaRequest(Context context, HttpRequest request) {
        var httpRequestBuilder = java.net.http.HttpRequest.newBuilder()
                .version(smithyToHttpVersion(request.httpVersion()))
                .method(request.method(), new DataStreamBodyPublisher(request.body()))
                .uri(request.uri().toURI());

        Duration requestTimeout = context.getOrDefault(HttpContext.HTTP_REQUEST_TIMEOUT, defaultRequestTimeout);
        if (requestTimeout != null) {
            httpRequestBuilder.timeout(requestTimeout);
        }

        request.headers().forEachEntry(httpRequestBuilder, VALUE_CONSUMER);
        return httpRequestBuilder.build();
    }

    private HttpResponse sendRequest(java.net.http.HttpRequest request) {
        java.net.http.HttpResponse<DataStream> res = null;
        try {
            res = client.send(request, ResponseBodyHandler.INSTANCE);
            return createSmithyResponse(res);
        } catch (IOException | InterruptedException | RuntimeException e) {
            if (res != null) {
                try {
                    res.body().close();
                } catch (RuntimeException closeException) {
                    LOGGER.trace("Failed to close response body after error", closeException);
                }
            }

            if (e instanceof HttpConnectTimeoutException) {
                throw new ConnectTimeoutException(e);
            }

            // The client pipeline also does this remapping, but to adhere to the required contract of
            // ClientTransport, we remap here too if needed.
            throw ClientTransport.remapExceptions(e);
        }
    }

    // package-private for testing
    HttpResponse createSmithyResponse(java.net.http.HttpResponse<? extends DataStream> response) {
        LOGGER.trace("Got response: {}", response);
        var headers = new JavaHttpHeaders(response.headers());
        var length = headers.contentLength();
        var adaptedLength = length == null ? -1 : length;
        var contentType = headers.contentType();
        var body = DataStream.withMetadata(response.body(), contentType, adaptedLength, response.body().isReplayable());
        return HttpResponse.of(javaToSmithyVersion(response.version()), response.statusCode(), headers, body);
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

    @Override
    public void close() {
        try {
            client.close();
        } finally {
            if (ownedExecutor != null) {
                ownedExecutor.shutdown();
            }
        }
    }

    public static final class Factory implements ClientTransportFactory<HttpRequest, HttpResponse> {
        @Override
        public String name() {
            return "http-java";
        }

        @Override
        public JavaHttpClientTransport createTransport(Document node, Document pluginSettings) {
            setHostProperties();
            // Start with httpConfig from plugin settings as baseline, then apply transport-specific settings on top.
            var config = new HttpTransportConfig()
                    .fromDocument(pluginSettings.asStringMap().getOrDefault("httpConfig", Document.EMPTY_MAP));
            config.fromDocument(node);

            var executor = Executors.newVirtualThreadPerTaskExecutor();
            var builder = HttpClient.newBuilder().executor(executor);
            if (config.httpVersion() != null) {
                builder.version(smithyToHttpVersion(config.httpVersion()));
            }
            if (config.connectTimeout() != null) {
                builder.connectTimeout(config.connectTimeout());
            }
            return new JavaHttpClientTransport(builder.build(), executor, config.requestTimeout());
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }

    /**
     * Picks a {@link BodySubscriber} implementation based on the advertised response size.
     *
     * <p>Small-body fast path ({@link ZeroCopyBodySubscriber}): when {@code Content-Length} is present and within
     * {@link #SMALL_RESPONSE_BODY_FAST_PATH_THRESHOLD}. Otherwise, falls back to the JDK's built-in
     * {@code ofInputStream()} body subscriber.
     */
    private static final class ResponseBodyHandler implements BodyHandler<DataStream> {
        static final ResponseBodyHandler INSTANCE = new ResponseBodyHandler();

        @Override
        public BodySubscriber<DataStream> apply(ResponseInfo responseInfo) {
            long contentLength = responseInfo.headers().firstValueAsLong("content-length").orElse(-1L);

            if (contentLength == 0) {
                String contentType = responseInfo.headers().firstValue("content-type").orElse(null);
                return BodySubscribers.replacing(DataStream.ofBytes(new byte[0], contentType));
            } else if (contentLength >= 0 && contentLength <= SMALL_RESPONSE_BODY_FAST_PATH_THRESHOLD) {
                return new ZeroCopyBodySubscriber(responseInfo.headers(), contentLength);
            }

            String contentType = responseInfo.headers().firstValue("content-type").orElse(null);
            return BodySubscribers.mapping(
                    BodySubscribers.ofInputStream(),
                    inputStream -> DataStream.ofInputStream(inputStream, contentType, contentLength));
        }
    }
}
