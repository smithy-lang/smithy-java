/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import static java.net.http.HttpRequest.BodyPublisher;
import static java.net.http.HttpRequest.BodyPublishers;
import static java.net.http.HttpResponse.BodyHandler;
import static java.net.http.HttpResponse.BodySubscriber;
import static java.net.http.HttpResponse.BodySubscribers;
import static java.net.http.HttpResponse.ResponseInfo;
import static software.amazon.smithy.java.http.api.HttpHeaders.HeaderWithValueConsumer;

import java.io.IOException;
import java.net.URI;
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

    private static final URI DUMMY_URI = URI.create("http://localhost");

    private static final int SMALL_RESPONSE_BODY_FAST_PATH_THRESHOLD = 64 * 1024;

    private static final HeaderWithValueConsumer<java.net.http.HttpRequest.Builder> VALUE_CONSUMER = (b, n, v) -> {
        if (n != HeaderName.CONTENT_LENGTH.name()) {
            b.setHeader(n, v);
        }
    };

    private static final InternalLogger LOGGER = InternalLogger.getLogger(JavaHttpClientTransport.class);
    private final HttpClient client;
    private final Duration defaultRequestTimeout;
    private final ExecutorService ownedExecutor;

    static {
        setHostProperties();
        setDefaultTuningProperties();
    }

    private static void setHostProperties() {
        var currentValues = System.getProperty("jdk.httpclient.allowRestrictedHeaders");
        if (currentValues == null || currentValues.isEmpty()) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
        } else if (!containsHost(currentValues)) {
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", currentValues + ",host");
        }
        try {
            java.net.http.HttpRequest.newBuilder()
                    .uri(DUMMY_URI)
                    .setHeader("host", "localhost")
                    .build();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unable to add host header. "
                    + "This means that the HttpClient was initialized before we could allowlist it. "
                    + "You need to explicitly set allow `host` via the system property "
                    + "`jdk.httpclient.allowRestrictedHeaders`",
                    e);
        }
    }

    /**
     * Set JVM-global JDK HttpClient tuning defaults when the application has not already set
     * them.
     */
    private static void setDefaultTuningProperties() {
        setIfUnset("jdk.httpclient.maxframesize", "65536");
        setIfUnset("jdk.httpclient.maxstreams", "1024");
        setIfUnset("jdk.httpclient.bufsize", "65536");
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

    private static boolean containsHost(String currentValues) {
        int length = currentValues.length();
        for (int i = 0; i < length; i++) {
            char c = currentValues.charAt(i);
            if ((c == 'h' || c == 'H') && i + 3 < length
                    && (currentValues.charAt(i + 1) == 'o')
                    && (currentValues.charAt(i + 2) == 's')
                    && (currentValues.charAt(i + 3) == 't')) {
                if (i + 4 == length || currentValues.charAt(i + 4) == ',') {
                    return true;
                }
            }
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
    public HttpResponse send(Context context, HttpRequest request) {
        return sendRequest(createJavaRequest(context, request));
    }

    /**
     * Convert a Smithy {@link HttpRequest} into a JDK {@link java.net.http.HttpRequest}.
     *
     * <p>Known-empty bodies use {@link BodyPublishers#noBody()}, replayable in-memory
     * {@link ByteBuffer} bodies use {@link JavaHttpClientReplayableByteBufferPublisher}, and
     * everything else falls back to {@link DataStream#bodyPublisher()}.
     *
     * <p>Request timeout precedence: context value takes priority over the transport-level
     * {@link #defaultRequestTimeout}; if neither is set the JDK applies no timeout.
     */
    private java.net.http.HttpRequest createJavaRequest(Context context, HttpRequest request) {
        DataStream requestBody = request.body();
        BodyPublisher bodyPublisher;
        ByteBuffer replayableRequestBody = toReplayableBodyBuffer(requestBody);

        if (replayableRequestBody != null) {
            bodyPublisher = !replayableRequestBody.hasRemaining()
                    ? BodyPublishers.noBody()
                    : new JavaHttpClientReplayableByteBufferPublisher(replayableRequestBody);
        } else if (requestBody.hasKnownLength()) {
            bodyPublisher = requestBody.contentLength() == 0
                    ? BodyPublishers.noBody()
                    : BodyPublishers.fromPublisher(requestBody, requestBody.contentLength());
        } else {
            bodyPublisher = BodyPublishers.fromPublisher(requestBody);
        }

        var httpRequestBuilder = java.net.http.HttpRequest.newBuilder()
                .version(smithyToHttpVersion(request.httpVersion()))
                .method(request.method(), bodyPublisher)
                .uri(request.uri().toURI());

        Duration requestTimeout = context.get(HttpContext.HTTP_REQUEST_TIMEOUT);
        if (requestTimeout == null) {
            requestTimeout = defaultRequestTimeout;
        }
        if (requestTimeout != null) {
            httpRequestBuilder.timeout(requestTimeout);
        }

        request.headers().forEachEntry(httpRequestBuilder, VALUE_CONSUMER);
        return httpRequestBuilder.build();
    }

    /**
     * Return the body as a {@link ByteBuffer} when it can be published directly; otherwise
     * return {@code null}.
     */
    private static ByteBuffer toReplayableBodyBuffer(DataStream requestBody) {
        if (!requestBody.isReplayable() || !requestBody.hasKnownLength() || !requestBody.hasByteBuffer()) {
            return null;
        }

        try {
            return requestBody.asByteBuffer().asReadOnlyBuffer();
        } catch (UnsupportedOperationException | IllegalStateException e) {
            return null;
        }
    }

    private HttpResponse sendRequest(java.net.http.HttpRequest request) {
        java.net.http.HttpResponse<DataStream> res = null;
        try {
            res = client.send(request, new ResponseBodyHandler());
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
        var headers = new JavaHttpHeaders(response.headers());
        LOGGER.trace("Got response: {}; headers: {}", response, response.headers().map());

        var length = headers.contentLength();
        var adaptedLength = length == null ? -1 : length;
        var contentType = headers.contentType();
        var body = DataStream.withMetadata(response.body(), contentType, adaptedLength, response.body().isReplayable());

        return new JavaHttpResponse(javaToSmithyVersion(response.version()), response.statusCode(), headers, body);
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
     * <p>Small-body fast path ({@link JavaHttpClientSmallBodySubscriber}): when
     * {@code Content-Length} is present and within {@link #SMALL_RESPONSE_BODY_FAST_PATH_THRESHOLD}
     * bytes, we pre-size a {@code byte[]} and accumulate the response fully before handing it
     * back. The result is a replayable {@link DataStream} with known length, avoiding the producer/consumer hand-off
     * of the streaming path.
     *
     * <p>If the fast path is not taken, the transport falls back to the JDK's built-in
     * {@code ofInputStream()} body subscriber and wraps the returned stream as a Smithy
     * {@link DataStream}.
     */
    private static final class ResponseBodyHandler implements BodyHandler<DataStream> {
        @Override
        public BodySubscriber<DataStream> apply(ResponseInfo responseInfo) {
            long contentLength = responseInfo.headers().firstValueAsLong("content-length").orElse(-1L);

            if (contentLength >= 0 && contentLength <= SMALL_RESPONSE_BODY_FAST_PATH_THRESHOLD) {
                return new JavaHttpClientSmallBodySubscriber(responseInfo.headers(), (int) contentLength);
            }

            String contentType = responseInfo.headers().firstValue("content-type").orElse(null);
            return BodySubscribers.mapping(
                    BodySubscribers.ofInputStream(),
                    inputStream -> DataStream.ofInputStream(inputStream, contentType, contentLength));
        }
    }
}
