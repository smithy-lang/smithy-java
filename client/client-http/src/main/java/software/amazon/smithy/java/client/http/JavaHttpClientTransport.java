/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import static java.net.http.HttpRequest.BodyPublishers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
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
 */
public final class JavaHttpClientTransport implements ClientTransport<HttpRequest, HttpResponse> {

    private static final URI DUMMY_URI = URI.create("http://localhost");
    private static final int SMALL_RESPONSE_BODY_FAST_PATH_THRESHOLD = 64 * 1024;

    private static final InternalLogger LOGGER = InternalLogger.getLogger(JavaHttpClientTransport.class);
    private final HttpClient client;
    private final Duration defaultRequestTimeout;

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
        try {
            java.net.http.HttpRequest.newBuilder()
                    .uri(DUMMY_URI)
                    .setHeader("host", "localhost")
                    .build();
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Unable to add host header. "
                    + "This means that the HttpClient was initialized before we could allowlist it. "
                    + "You need to explicitly set allow `host` via the system property `jdk.httpclient.allowRestrictedHeaders`",
                    e);
        }
    }

    public JavaHttpClientTransport() {
        this(HttpClient.newHttpClient(), null);
    }

    /**
     * @param client Java client to use.
     */
    public JavaHttpClientTransport(HttpClient client) {
        this(client, null);
    }

    /**
     * @param client Java client to use.
     * @param defaultRequestTimeout Default per-request timeout. Used when {@link HttpContext#HTTP_REQUEST_TIMEOUT}
     *                              is not set in the request context. Null means no default.
     */
    public JavaHttpClientTransport(HttpClient client, Duration defaultRequestTimeout) {
        this.client = client;
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

    private java.net.http.HttpRequest createJavaRequest(Context context, HttpRequest request) {
        DataStream requestBody = request.body();
        java.net.http.HttpRequest.BodyPublisher bodyPublisher;
        ByteBuffer replayableRequestBody = toReplayableBodyBuffer(requestBody);
        if (replayableRequestBody != null) {
            bodyPublisher = !replayableRequestBody.hasRemaining()
                    ? BodyPublishers.noBody()
                    : new ReplayableByteBufferPublisher(replayableRequestBody);
        } else if (requestBody.hasKnownLength()) {
            bodyPublisher = requestBody.contentLength() == 0
                    ? BodyPublishers.noBody()
                    : BodyPublishers.fromPublisher(requestBody, requestBody.contentLength());
        } else {
            bodyPublisher = BodyPublishers.fromPublisher(requestBody);
        }

        var requestVersion = request.httpVersion();
        var clientVersion = client.version();
        var httpRequestBuilder = java.net.http.HttpRequest.newBuilder()
                .method(request.method(), bodyPublisher)
                .uri(request.uri().toURI());
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

        // Any explicitly set headers overwrite existing headers, they do not merge.
        request.headers().forEachEntry(httpRequestBuilder, (b, name, value) -> {
            // Skip restricted headers; Header names in HttpHeaders are always canonicalized, so check by reference.
            if (name != HeaderName.CONTENT_LENGTH.name()) {
                b.setHeader(name, value);
            }
        });

        return httpRequestBuilder.build();
    }

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
        client.close();
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
            var config = new HttpTransportConfig().fromDocument(pluginSettings.asStringMap()
                    .getOrDefault("httpConfig", Document.EMPTY_MAP));
            config.fromDocument(node);
            var builder = HttpClient.newBuilder();
            if (config.httpVersion() != null) {
                builder.version(smithyToHttpVersion(config.httpVersion()));
            }
            if (config.connectTimeout() != null) {
                builder.connectTimeout(config.connectTimeout());
            }
            return new JavaHttpClientTransport(builder.build(), config.requestTimeout());
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }

    private static final class ResponseBodyHandler implements java.net.http.HttpResponse.BodyHandler<DataStream> {
        @Override
        public java.net.http.HttpResponse.BodySubscriber<DataStream> apply(
                java.net.http.HttpResponse.ResponseInfo responseInfo
        ) {
            long contentLength = responseInfo.headers().firstValueAsLong("content-length").orElse(-1L);
            if (contentLength >= 0 && contentLength <= SMALL_RESPONSE_BODY_FAST_PATH_THRESHOLD) {
                return new SmallBodySubscriber(responseInfo.headers(), (int) contentLength);
            }
            return new ResponseBodySubscriber(responseInfo.headers());
        }
    }

    private static final class ReplayableByteBufferPublisher implements java.net.http.HttpRequest.BodyPublisher {
        private final ByteBuffer body;

        private ReplayableByteBufferPublisher(ByteBuffer body) {
            this.body = body.asReadOnlyBuffer();
        }

        @Override
        public long contentLength() {
            return body.remaining();
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            subscriber.onSubscribe(new Flow.Subscription() {
                private final AtomicBoolean completed = new AtomicBoolean();

                @Override
                public void request(long n) {
                    if (n <= 0 || !completed.compareAndSet(false, true)) {
                        return;
                    }

                    subscriber.onNext(body.duplicate());
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    completed.set(true);
                }
            });
        }
    }

    private static final class SmallBodySubscriber implements java.net.http.HttpResponse.BodySubscriber<DataStream> {
        private final String contentType;
        private final byte[] bytes;
        private int position;
        private final CompletableFuture<DataStream> body = new CompletableFuture<>();

        private SmallBodySubscriber(java.net.http.HttpHeaders headers, int contentLength) {
            this.contentType = headers.firstValue("content-type").orElse(null);
            this.bytes = new byte[Math.max(contentLength, 0)];
        }

        @Override
        public CompletionStage<DataStream> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            for (ByteBuffer buffer : item) {
                int remaining = buffer.remaining();
                buffer.get(bytes, position, remaining);
                position += remaining;
            }
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            byte[] result = position == bytes.length ? bytes : Arrays.copyOf(bytes, position);
            body.complete(DataStream.ofBytes(result, contentType));
        }
    }

    private static final class ResponseBodySubscriber implements java.net.http.HttpResponse.BodySubscriber<DataStream> {
        private final StreamingDataStream stream;
        private final CompletionStage<DataStream> body;

        private ResponseBodySubscriber(java.net.http.HttpHeaders headers) {
            this.stream = new StreamingDataStream(
                    headers.firstValue("content-type").orElse(null),
                    headers.firstValueAsLong("content-length").orElse(-1L));
            this.body = CompletableFuture.completedFuture(stream);
        }

        @Override
        public CompletionStage<DataStream> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            stream.setSubscription(subscription);
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            for (ByteBuffer buffer : item) {
                stream.enqueue(buffer);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            stream.fail(throwable);
        }

        @Override
        public void onComplete() {
            stream.complete();
        }
    }

    private static final class StreamingDataStream implements DataStream {
        private static final Object EOF = new Object();

        private final BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        private final String contentType;
        private final long contentLength;
        private volatile Flow.Subscription subscription;
        private volatile boolean consumed;
        private volatile boolean closed;

        private StreamingDataStream(String contentType, long contentLength) {
            this.contentType = contentType;
            this.contentLength = contentLength;
        }

        @Override
        public long contentLength() {
            return contentLength;
        }

        @Override
        public String contentType() {
            return contentType;
        }

        @Override
        public boolean isReplayable() {
            return false;
        }

        @Override
        public boolean isAvailable() {
            return !consumed;
        }

        @Override
        public InputStream asInputStream() {
            if (consumed) {
                throw new IllegalStateException("Response body is not replayable and has already been consumed");
            }
            consumed = true;
            return new StreamingInputStream(this);
        }

        @Override
        public void close() {
            closed = true;
            var current = subscription;
            if (current != null) {
                current.cancel();
            }
            queue.offer(EOF);
        }

        private void setSubscription(Flow.Subscription subscription) {
            this.subscription = subscription;
            if (closed) {
                subscription.cancel();
            }
        }

        private void enqueue(ByteBuffer buffer) {
            if (closed || !buffer.hasRemaining()) {
                return;
            }
            queue.offer(buffer.asReadOnlyBuffer());
        }

        private void fail(Throwable throwable) {
            queue.offer(throwable);
        }

        private void complete() {
            queue.offer(EOF);
        }

        private Object takeNext() throws IOException {
            try {
                return queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted waiting for response body data", e);
            }
        }
    }

    private static final class StreamingInputStream extends InputStream {
        private final StreamingDataStream stream;
        private ByteBuffer current;

        private StreamingInputStream(StreamingDataStream stream) {
            this.stream = stream;
        }

        @Override
        public int read() throws IOException {
            byte[] buffer = new byte[1];
            int read = read(buffer, 0, 1);
            return read < 0 ? -1 : buffer[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }

            while (current == null || !current.hasRemaining()) {
                Object next = stream.takeNext();
                if (next == StreamingDataStream.EOF) {
                    return -1;
                }
                if (next instanceof Throwable throwable) {
                    throw new IOException("Failed receiving response body", throwable);
                }
                current = (ByteBuffer) next;
            }

            int toRead = Math.min(len, current.remaining());
            current.get(b, off, toRead);
            return toRead;
        }

        @Override
        public void close() {
            stream.close();
        }
    }
}
