/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import static java.net.http.HttpRequest.BodyPublisher;
import static java.net.http.HttpRequest.BodyPublishers;
import static java.net.http.HttpResponse.BodyHandler;
import static java.net.http.HttpResponse.BodySubscriber;
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
 * A client transport that uses Java's built-in {@link HttpClient} to send {@link HttpRequest} and return
 * {@link HttpResponse}.
 *
 * <h2>How the pieces fit together</h2>
 * <p>A request flows through the following (see {@link #createJavaRequest}):
 * <ol>
 *   <li>The Smithy {@link DataStream} body is mapped to a {@link java.net.http.HttpRequest.BodyPublisher}
 *       by one of three strategies, preferring the zero-copy {@link JavaHttpClientReplayableByteBufferPublisher}
 *       for in-memory replayable bodies.</li>
 *   <li>Headers, timeout, and HTTP version are applied to the JDK request builder.</li>
 *   <li>The JDK client dispatches the request; response bytes are delivered to a
 *       {@link java.net.http.HttpResponse.BodySubscriber} chosen by {@link ResponseBodyHandler}:
 *       {@link JavaHttpClientSmallBodySubscriber} for small known-length bodies, or
 *       {@link JavaHttpClientResponseBodySubscriber} + {@link JavaHttpClientStreamingDataStream}
 *       for streaming bodies.</li>
 * </ol>
 *
 * <h2>Executor lifecycle</h2>
 * <p>When a caller provides an {@link HttpClient}, its executor (default JDK cached pool or
 * user-supplied) is owned by the caller. When this transport constructs its own client (no-arg
 * constructor and {@link Factory}), it attaches a virtual-thread-per-task executor and shuts
 * that executor down in {@link #close()}; the JDK's {@code client.close()} does NOT shut down
 * a non-default executor.
 *
 * <h2>System properties</h2>
 * <p>{@link #setDefaultTuningProperties} sets modest improvements to the JDK defaults for
 * {@code jdk.httpclient.maxframesize}, {@code maxstreams}, and {@code bufsize} at class init.
 * {@link #setHostProperties} allowlists the {@code Host} header so consumer plugins can
 * override it.
 */
public final class JavaHttpClientTransport implements ClientTransport<HttpRequest, HttpResponse> {

    private static final URI DUMMY_URI = URI.create("http://localhost");

    // Responses with a known Content-Length at or below this threshold use the small-body
    // fast path (accumulate into a byte[] before returning). Above this, we switch to the
    // streaming path. 64 KB is a tradeoff between memory footprint for the aggregated array
    // and per-request overhead of the streaming producer/consumer machinery; it also matches
    // the default JDK receive buffer tuning.
    private static final int SMALL_RESPONSE_BODY_FAST_PATH_THRESHOLD = 64 * 1024;

    // Applies Smithy headers to the JDK request builder. Content-Length is filtered out because
    // the JDK client sets it itself based on the selected BodyPublisher's declared length.
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
        // For some reason, this can't just be done in the constructor to always take effect.
        setHostProperties();
        setDefaultTuningProperties();
    }

    private static void setHostProperties() {
        // Allow clients to set Host header. This has to be done using a system property and can't be done per/client.
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
     * Set JDK HttpClient tuning properties to values more suited for service-to-service traffic.
     *
     * <p>These are read once at class init of {@code java.net.http.*}, so we set them here before
     * the first {@code HttpClient.newBuilder()} call. Callers that want different values can set
     * the corresponding {@code -Djdk.httpclient.*} property on the JVM command line; this method
     * only sets a value if the user hasn't already set one.
     *
     * <p>The chosen values:
     * <ul>
     *   <li>{@code jdk.httpclient.maxframesize=32768}: larger H2 frames amortize per-frame
     *       overhead on streaming bodies (JDK default is 16 KB).</li>
     *   <li>{@code jdk.httpclient.maxstreams=256}: allow more concurrent H2 streams per
     *       connection (JDK default is 100).</li>
     *   <li>{@code jdk.httpclient.bufsize=32768}: larger internal I/O buffer reduces read
     *       iterations for medium-to-large bodies (JDK default is 16 KB).</li>
     * </ul>
     */
    private static void setDefaultTuningProperties() {
        setIfUnset("jdk.httpclient.maxframesize", "32768");
        setIfUnset("jdk.httpclient.maxstreams", "256");
        setIfUnset("jdk.httpclient.bufsize", "32768");
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

    /**
     * @param client Java client to use. The caller is responsible for closing it and any
     *               executor it was built with.
     */
    public JavaHttpClientTransport(HttpClient client) {
        this(client, null);
    }

    /**
     * @param client Java client to use. The caller is responsible for closing it and any
     *               executor it was built with.
     * @param defaultRequestTimeout Default per-request timeout. Used when {@link HttpContext#HTTP_REQUEST_TIMEOUT}
     *                              is not set in the request context. Null means no default.
     */
    public JavaHttpClientTransport(HttpClient client, Duration defaultRequestTimeout) {
        this.client = client;
        this.ownedExecutor = null;
        this.defaultRequestTimeout = defaultRequestTimeout;
        setHostProperties();
    }

    // Factory-internal constructor: factory owns both client and executor.
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
     * Convert a Smithy {@link HttpRequest} into a {@link java.net.http.HttpRequest} for the JDK
     * {@link HttpClient}.
     *
     * <p>The interesting decision here is how to expose the request body to the JDK client.
     * Three cases are handled, in order of preference:
     * <ol>
     *   <li><b>Replayable + in-memory</b>: If the body is already backed by a {@link ByteBuffer}
     *       and can be replayed (e.g., {@link DataStream#ofBytes(byte[])}), wrap it in a
     *       {@link JavaHttpClientReplayableByteBufferPublisher}. This is the cheapest path —
     *       every subscribe hands out a {@code duplicate()} of the shared backing bytes, so
     *       retries cost nothing and no intermediate buffers are allocated.</li>
     *   <li><b>Known length, not in-memory</b>: Use the JDK's
     *       {@link BodyPublishers#fromPublisher(java.util.concurrent.Flow.Publisher, long)}
     *       overload that takes the content length, so the JDK can set {@code Content-Length}
     *       and avoid chunked encoding over HTTP/1.1.</li>
     *   <li><b>Unknown length</b>: Use {@link BodyPublishers#fromPublisher(java.util.concurrent.Flow.Publisher)}
     *       and let the JDK fall back to chunked transfer encoding.</li>
     * </ol>
     *
     * <p>Version pinning: if the caller explicitly requested HTTP/1.1 but the underlying JDK
     * client is configured for HTTP/2, we intentionally do NOT call {@code builder.version()}.
     * Setting the version to HTTP/1.1 here would force the JDK to open a new connection rather
     * than reuse an existing HTTP/2 connection, which is wasteful when the server advertises
     * HTTP/2 via ALPN. For any other combination we pass the caller's requested version through.
     *
     * <p>Request timeout precedence: context value takes priority over the transport-level
     * {@link #defaultRequestTimeout}; if neither is set the JDK applies no timeout.
     *
     * <p>Headers: Smithy's canonical header names are applied via {@link #VALUE_CONSUMER}. The
     * JDK rejects some restricted headers by default ({@code Host}, etc.); the static
     * initializer sets {@code jdk.httpclient.allowRestrictedHeaders=host} to allow our
     * consumer plugins to override it when necessary.
     */
    private java.net.http.HttpRequest createJavaRequest(Context context, HttpRequest request) {
        DataStream requestBody = request.body();
        BodyPublisher bodyPublisher;
        ByteBuffer replayableRequestBody = toReplayableBodyBuffer(requestBody);

        if (replayableRequestBody != null) {
            // Fast path: zero-copy publishing of an already-in-memory body.
            bodyPublisher = !replayableRequestBody.hasRemaining()
                    ? BodyPublishers.noBody()
                    : new JavaHttpClientReplayableByteBufferPublisher(replayableRequestBody);
        } else if (requestBody.hasKnownLength()) {
            // Streaming with Content-Length: JDK can emit a content-length header and skip
            // chunked encoding over HTTP/1.1.
            bodyPublisher = requestBody.contentLength() == 0
                    ? BodyPublishers.noBody()
                    : BodyPublishers.fromPublisher(requestBody, requestBody.contentLength());
        } else {
            // Unknown length: JDK will use chunked transfer encoding on HTTP/1.1 or DATA frames on HTTP/2.
            bodyPublisher = BodyPublishers.fromPublisher(requestBody);
        }

        var requestVersion = request.httpVersion();
        var clientVersion = client.version();
        var httpRequestBuilder = java.net.http.HttpRequest.newBuilder()
                .method(request.method(), bodyPublisher)
                .uri(request.uri().toURI());
        // Skip pinning to HTTP/1.1 when the client is configured for HTTP/2; forcing the
        // version would prevent reusing an existing H2 connection.
        if (!(requestVersion == HttpVersion.HTTP_1_1 && clientVersion == HttpClient.Version.HTTP_2)) {
            httpRequestBuilder.version(smithyToHttpVersion(requestVersion));
        }

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
     * Return the request body as a {@link ByteBuffer} if it can be published as-is without any copy or
     * re-materialization; {@code null} otherwise.
     *
     * <p>Requires all of: replayable (retries are safe), known length (JDK can set Content-Length), and backed by
     * a {@link ByteBuffer} (no codec work needed to materialize it). The {@link UnsupportedOperationException}
     * / {@link IllegalStateException} catches defensively handle DataStream implementations that don't actually
     * support {@link DataStream#asByteBuffer()} despite reporting {@code hasByteBuffer()}.
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
        var body = DataStream.withMetadata(response.body(), contentType, adaptedLength, false);

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
     * <p>If the fastpath isn't taken, a streaming subscriber is used that reads bytes as they arrive.
     */
    private static final class ResponseBodyHandler implements BodyHandler<DataStream> {
        @Override
        public BodySubscriber<DataStream> apply(ResponseInfo responseInfo) {
            long contentLength = responseInfo.headers().firstValueAsLong("content-length").orElse(-1L);

            if (contentLength >= 0 && contentLength <= SMALL_RESPONSE_BODY_FAST_PATH_THRESHOLD) {
                return new JavaHttpClientSmallBodySubscriber(responseInfo.headers(), (int) contentLength);
            }

            return new JavaHttpClientResponseBodySubscriber(responseInfo.headers());
        }
    }
}
