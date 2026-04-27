/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.crt;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import software.amazon.awssdk.crt.CRT;
import software.amazon.awssdk.crt.CrtRuntimeException;
import software.amazon.awssdk.crt.http.Http2Request;
import software.amazon.awssdk.crt.http.Http2StreamManager;
import software.amazon.awssdk.crt.http.Http2StreamManagerOptions;
import software.amazon.awssdk.crt.http.HttpClientConnection;
import software.amazon.awssdk.crt.http.HttpClientConnectionManager;
import software.amazon.awssdk.crt.http.HttpClientConnectionManagerOptions;
import software.amazon.awssdk.crt.http.HttpHeader;
import software.amazon.awssdk.crt.http.HttpRequestBodyStream;
import software.amazon.awssdk.crt.http.HttpStreamBase;
import software.amazon.awssdk.crt.http.HttpStreamBaseResponseHandler;
import software.amazon.awssdk.crt.http.HttpVersion;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsConnectionOptions;
import software.amazon.awssdk.crt.io.TlsContext;
import software.amazon.awssdk.crt.io.TlsContextOptions;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.ClientTransportFactory;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.http.HttpContext;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * A client transport backed by the AWS Common Runtime (CRT).
 *
 * <p>This is a thin blocking wrapper around CRT's native H1/H2 client managers. The public API remains
 * Smithy's blocking {@link HttpRequest}/{@link HttpResponse} model while the CRT handles connection
 * management, framing, and TLS underneath.
 */
public final class CrtHttpClientTransport implements ClientTransport<HttpRequest, HttpResponse> {

    private final CrtHttpTransportConfig config;
    private final ClientBootstrap bootstrap;
    private final SocketOptions socketOptions;
    private final TlsContext tlsContext;
    private final Map<RouteKey, RoutePool> pools = new ConcurrentHashMap<>();

    public CrtHttpClientTransport() {
        this(new CrtHttpTransportConfig());
    }

    public CrtHttpClientTransport(CrtHttpTransportConfig config) {
        this.config = config;
        this.bootstrap = new ClientBootstrap(null, null);
        this.socketOptions = new SocketOptions();
        if (config.connectTimeout() != null) {
            this.socketOptions.connectTimeoutMs = saturatedMillis(config.connectTimeout());
        }
        var tlsOptions = TlsContextOptions.createDefaultClient().withVerifyPeer();
        this.tlsContext = new TlsContext(tlsOptions);
        tlsOptions.close();
    }

    @Override
    public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
        return HttpMessageExchange.INSTANCE;
    }

    @Override
    public HttpResponse send(Context context, HttpRequest request) {
        try {
            var route = RouteKey.from(request, config);
            var pool = pools.computeIfAbsent(route, this::createPool);
            var timeout = context.get(HttpContext.HTTP_REQUEST_TIMEOUT);
            return pool.execute(request, timeout);
        } catch (Exception e) {
            throw ClientTransport.remapExceptions(e);
        }
    }

    private RoutePool createPool(RouteKey route) {
        return route.version == software.amazon.smithy.java.http.api.HttpVersion.HTTP_2
                ? new H2Pool(route)
                : new H1Pool(route);
    }

    @Override
    public void close() throws IOException {
        IOException thrown = null;
        for (var pool : pools.values()) {
            try {
                pool.close();
            } catch (IOException e) {
                if (thrown == null) {
                    thrown = e;
                } else {
                    thrown.addSuppressed(e);
                }
            }
        }
        pools.clear();
        closeQuietly(tlsContext);
        closeQuietly(socketOptions);
        closeQuietly(bootstrap);
        if (thrown != null) {
            throw thrown;
        }
    }

    private static int saturatedMillis(Duration duration) {
        return duration.toMillis() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) duration.toMillis();
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private static <T> T await(CompletableFuture<T> future, Duration timeout)
            throws ExecutionException, InterruptedException, TimeoutException {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return future.get();
        }
        return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private interface RoutePool extends AutoCloseable {
        HttpResponse execute(HttpRequest request, Duration timeout) throws Exception;

        @Override
        void close() throws IOException;
    }

    private final class H1Pool implements RoutePool {
        private final HttpClientConnectionManager manager;

        private H1Pool(RouteKey route) {
            this.manager = HttpClientConnectionManager.create(baseManagerOptions(route));
        }

        @Override
        public HttpResponse execute(HttpRequest request, Duration timeout) throws Exception {
            HttpClientConnection connection = await(manager.acquireConnection(), effectiveAcquireTimeout(timeout));
            RequestLifetime lifetime = null;
            try {
                var body = CrtRequestBodyAdapter.from(request.body());
                var responseHandler = new CrtResponseHandler(
                        smithyToCrtVersion(RouteKey.from(request, config).version),
                        true);
                var crtRequest = toCrtRequest(request, body, false);
                var stream = connection.makeRequest(crtRequest, responseHandler);
                lifetime = RequestLifetime.forH1(connection, manager, stream, body);
                responseHandler.bind(lifetime);
                stream.activate();
                return await(responseHandler.headersFuture(), timeout);
            } catch (Throwable t) {
                if (lifetime != null) {
                    lifetime.abort();
                } else {
                    shutdownAndRelease(connection, manager);
                }
                throw t;
            }
        }

        @Override
        public void close() throws IOException {
            manager.close();
            try {
                manager.getShutdownCompleteFuture().get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted closing CRT HTTP/1 pool", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IOException("Failed closing CRT HTTP/1 pool", e);
            }
        }
    }

    private final class H2Pool implements RoutePool {
        private final Http2StreamManager manager;

        private H2Pool(RouteKey route) {
            var options = new Http2StreamManagerOptions()
                    .withConnectionManagerOptions(baseManagerOptions(route))
                    .withMaxConcurrentStreamsPerConnection(config.h2StreamsPerConnection())
                    .withIdealConcurrentStreamsPerConnection(config.h2StreamsPerConnection())
                    .withConnectionManualWindowManagement(false);
            if (!route.secure) {
                options.withPriorKnowledge(true);
            }
            this.manager = Http2StreamManager.create(options);
        }

        @Override
        public HttpResponse execute(HttpRequest request, Duration timeout) throws Exception {
            RequestLifetime lifetime = null;
            try {
                var body = CrtRequestBodyAdapter.from(request.body());
                var responseHandler = new CrtResponseHandler(software.amazon.awssdk.crt.http.HttpVersion.HTTP_2, false);
                var streamFuture = manager.acquireStream(toCrtH2Request(request, body), responseHandler);
                var stream = await(streamFuture, effectiveAcquireTimeout(timeout));
                lifetime = RequestLifetime.forH2(stream, body);
                responseHandler.bind(lifetime);
                stream.activate();
                return await(responseHandler.headersFuture(), timeout);
            } catch (Throwable t) {
                if (lifetime != null) {
                    lifetime.abort();
                }
                throw t;
            }
        }

        @Override
        public void close() throws IOException {
            manager.close();
            try {
                manager.getShutdownCompleteFuture().get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted closing CRT HTTP/2 pool", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IOException("Failed closing CRT HTTP/2 pool", e);
            }
        }
    }

    private HttpClientConnectionManagerOptions baseManagerOptions(RouteKey route) {
        var options = new HttpClientConnectionManagerOptions()
                .withClientBootstrap(bootstrap)
                .withSocketOptions(socketOptions)
                .withUri(route.baseUri)
                .withWindowSize(config.readBufferSize())
                .withManualWindowManagement(route.version != software.amazon.smithy.java.http.api.HttpVersion.HTTP_2)
                .withMaxConnections(config.maxConnectionsPerHost())
                .withConnectionAcquisitionTimeoutInMilliseconds(config.acquireTimeout().toMillis())
                .withExpectedHttpVersion(smithyToCrtVersion(route.version));

        if (route.secure) {
            var tlsOptions = new TlsConnectionOptions(tlsContext).withServerName(route.host);
            if (route.version == software.amazon.smithy.java.http.api.HttpVersion.HTTP_2) {
                tlsOptions.withAlpnList("h2");
            }
            options.withTlsConnectionOptions(tlsOptions);
        }

        return options;
    }

    private Duration effectiveAcquireTimeout(Duration requestTimeout) {
        if (requestTimeout == null) {
            return config.acquireTimeout();
        }
        return requestTimeout.compareTo(config.acquireTimeout()) < 0 ? requestTimeout : config.acquireTimeout();
    }

    private static void shutdownAndRelease(HttpClientConnection connection, HttpClientConnectionManager manager) {
        try {
            if (connection.isOpen()) {
                connection.shutdown();
            }
        } catch (Exception ignored) {
        }
        try {
            manager.releaseConnection(connection);
        } catch (Exception ignored) {
        }
    }

    private static software.amazon.awssdk.crt.http.HttpRequest toCrtRequest(
            HttpRequest request,
            CrtRequestBodyAdapter body,
            boolean isH2
    ) {
        HttpHeader[] headers = toCrtHeaders(request, body, isH2);
        if (body.isEmpty()) {
            return new software.amazon.awssdk.crt.http.HttpRequest(request.method(), encodedPath(request), headers, null);
        }
        return new software.amazon.awssdk.crt.http.HttpRequest(request.method(), encodedPath(request), headers, body);
    }

    private static Http2Request toCrtH2Request(HttpRequest request, CrtRequestBodyAdapter body) {
        var uri = request.uri().toURI();
        var authority = uri.getPort() > 0 && uri.getPort() != 80 && uri.getPort() != 443
                ? uri.getHost() + ":" + uri.getPort()
                : uri.getHost();
        var headerList = new ArrayList<HttpHeader>(request.headers().size() + 5);
        headerList.add(new HttpHeader(HeaderName.PSEUDO_METHOD.name(), request.method()));
        headerList.add(new HttpHeader(HeaderName.PSEUDO_PATH.name(), encodedPath(request)));
        headerList.add(new HttpHeader(HeaderName.PSEUDO_SCHEME.name(), uri.getScheme()));
        headerList.add(new HttpHeader(HeaderName.PSEUDO_AUTHORITY.name(), authority));
        request.headers().forEachEntry((name, value) -> {
            if (HeaderName.HOST.name().equals(name)
                    || HeaderName.CONNECTION.name().equals(name)
                    || HeaderName.PSEUDO_METHOD.name().equals(name)
                    || HeaderName.PSEUDO_PATH.name().equals(name)
                    || HeaderName.PSEUDO_SCHEME.name().equals(name)
                    || HeaderName.PSEUDO_AUTHORITY.name().equals(name)) {
                return;
            }
            if (!HeaderName.CONTENT_LENGTH.name().equals(name) || body.length() >= 0) {
                headerList.add(new HttpHeader(name, value));
            }
        });
        if (body.length() >= 0 && request.headers().firstValue(HeaderName.CONTENT_LENGTH) == null) {
            headerList.add(new HttpHeader(HeaderName.CONTENT_LENGTH.name(), Long.toString(body.length())));
        }
        return new Http2Request(headerList.toArray(HttpHeader[]::new), body.isEmpty() ? null : body);
    }

    private static HttpHeader[] toCrtHeaders(HttpRequest request, CrtRequestBodyAdapter body, boolean isH2) {
        var headerList = new ArrayList<HttpHeader>(request.headers().size() + 2);
        if (request.headers().firstValue(HeaderName.HOST) == null) {
            headerList.add(new HttpHeader(HeaderName.HOST.name(), request.uri().toURI().getHost()));
        }
        request.headers().forEachEntry((name, value) -> {
            if (isH2 && HeaderName.CONNECTION.name().equals(name)) {
                return;
            }
            if (!HeaderName.CONTENT_LENGTH.name().equals(name) || body.length() >= 0) {
                headerList.add(new HttpHeader(name, value));
            }
        });
        if (body.length() >= 0 && request.headers().firstValue(HeaderName.CONTENT_LENGTH) == null) {
            headerList.add(new HttpHeader(HeaderName.CONTENT_LENGTH.name(), Long.toString(body.length())));
        }
        return headerList.toArray(HttpHeader[]::new);
    }

    private static String encodedPath(HttpRequest request) {
        URI uri = request.uri().toURI();
        String rawPath = uri.getRawPath();
        if (rawPath == null || rawPath.isEmpty()) {
            rawPath = "/";
        }
        String rawQuery = uri.getRawQuery();
        return rawQuery == null ? rawPath : rawPath + "?" + rawQuery;
    }

    private static HttpVersion smithyToCrtVersion(software.amazon.smithy.java.http.api.HttpVersion version) {
        return switch (version) {
            case HTTP_1_0 -> HttpVersion.HTTP_1_0;
            case HTTP_1_1 -> HttpVersion.HTTP_1_1;
            case HTTP_2 -> HttpVersion.HTTP_2;
            default -> throw new UnsupportedOperationException("Unsupported HTTP version: " + version);
        };
    }

    private static software.amazon.smithy.java.http.api.HttpVersion crtToSmithyVersion(HttpVersion version) {
        return switch (version) {
            case HTTP_1_0 -> software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_0;
            case HTTP_1_1 -> software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_1;
            case HTTP_2 -> software.amazon.smithy.java.http.api.HttpVersion.HTTP_2;
            default -> throw new UnsupportedOperationException("Unsupported CRT HTTP version: " + version);
        };
    }

    private record RouteKey(
            String scheme,
            String host,
            int port,
            boolean secure,
            software.amazon.smithy.java.http.api.HttpVersion version,
            URI baseUri
    ) {
        private static RouteKey from(HttpRequest request, CrtHttpTransportConfig config) {
            var uri = request.uri().toURI();
            int port = uri.getPort();
            boolean secure = "https".equalsIgnoreCase(uri.getScheme());
            if (port <= 0) {
                port = secure ? 443 : 80;
            }
            var version = request.httpVersion();
            if (version == null) {
                version = config.httpVersion();
            }
            if (version == null) {
                version = software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_1;
            }
            URI baseUri = URI.create(uri.getScheme() + "://" + uri.getHost() + ":" + port + "/");
            return new RouteKey(uri.getScheme(), uri.getHost(), port, secure, version, baseUri);
        }
    }

    private static final class RequestLifetime {
        private final HttpStreamBase stream;
        private final CrtRequestBodyAdapter body;
        private final Runnable onSuccess;
        private final Runnable onAbort;
        private boolean finished;

        private RequestLifetime(HttpStreamBase stream, CrtRequestBodyAdapter body, Runnable onSuccess, Runnable onAbort) {
            this.stream = stream;
            this.body = body;
            this.onSuccess = onSuccess;
            this.onAbort = onAbort;
        }

        static RequestLifetime forH1(
                HttpClientConnection connection,
                HttpClientConnectionManager manager,
                HttpStreamBase stream,
                CrtRequestBodyAdapter body
        ) {
            return new RequestLifetime(
                    stream,
                    body,
                    () -> {
                        closeQuietly(body);
                        closeQuietly(stream);
                        manager.releaseConnection(connection);
                    },
                    () -> {
                        closeQuietly(body);
                        try {
                            if (connection.isOpen()) {
                                connection.shutdown();
                            }
                        } catch (Exception ignored) {
                        }
                        closeQuietly(stream);
                        try {
                            manager.releaseConnection(connection);
                        } catch (Exception ignored) {
                        }
                    });
        }

        static RequestLifetime forH2(HttpStreamBase stream, CrtRequestBodyAdapter body) {
            return new RequestLifetime(
                    stream,
                    body,
                    () -> {
                        closeQuietly(body);
                        closeQuietly(stream);
                    },
                    () -> {
                        closeQuietly(body);
                        closeQuietly(stream);
                    });
        }

        synchronized void complete() {
            if (!finished) {
                finished = true;
                onSuccess.run();
            }
        }

        synchronized void abort() {
            if (!finished) {
                finished = true;
                onAbort.run();
            }
        }
    }

    private static final class CrtRequestBodyAdapter implements HttpRequestBodyStream, AutoCloseable {
        private static final int EOF = -1;
        private static final int IN_MEMORY_FAST_PATH_MAX_BYTES = 8 * 1024 * 1024;

        private final DataStream body;
        private final long length;
        private final String contentType;
        private final java.nio.ByteBuffer sourceBuffer;
        private java.nio.channels.ReadableByteChannel channel;

        private CrtRequestBodyAdapter(DataStream body) {
            this.body = body;
            this.length = body.hasKnownLength() ? body.contentLength() : -1;
            this.contentType = body.contentType();
            this.sourceBuffer = shouldUseInMemoryFastPath(body, length) ? body.asByteBuffer() : null;
        }

        static CrtRequestBodyAdapter from(DataStream body) {
            return new CrtRequestBodyAdapter(body);
        }

        boolean isEmpty() {
            return length == 0;
        }

        long length() {
            return length;
        }

        String contentType() {
            return contentType;
        }

        @Override
        public boolean sendRequestBody(java.nio.ByteBuffer bodyBytesOut) {
            try {
                if (sourceBuffer != null) {
                    if (!sourceBuffer.hasRemaining()) {
                        return true;
                    }
                    int toCopy = Math.min(sourceBuffer.remaining(), bodyBytesOut.remaining());
                    if (toCopy == 0) {
                        return false;
                    }
                    int oldLimit = sourceBuffer.limit();
                    sourceBuffer.limit(sourceBuffer.position() + toCopy);
                    bodyBytesOut.put(sourceBuffer);
                    sourceBuffer.limit(oldLimit);
                    return !sourceBuffer.hasRemaining();
                }
                if (channel == null) {
                    channel = body.asChannel();
                }
                while (bodyBytesOut.hasRemaining()) {
                    int read = channel.read(bodyBytesOut);
                    if (read == 0) {
                        break;
                    }
                    if (read == EOF) {
                        close();
                        return true;
                    }
                }
                return false;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean resetPosition() {
            if (!body.isReplayable()) {
                return false;
            }
            if (sourceBuffer != null) {
                sourceBuffer.position(0);
                return true;
            }
            close();
            try {
                channel = body.asChannel();
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        }

        @Override
        public long getLength() {
            return Math.max(length, 0);
        }

        @Override
        public void close() {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException ignored) {
                } finally {
                    channel = null;
                }
            }
        }

        private static boolean shouldUseInMemoryFastPath(DataStream body, long length) {
            return body.isReplayable()
                    && body.isAvailable()
                    && length >= 0
                    && length <= IN_MEMORY_FAST_PATH_MAX_BYTES;
        }
    }

    private static final class CrtResponseHandler implements HttpStreamBaseResponseHandler {
        private final CompletableFuture<HttpResponse> headersFuture = new CompletableFuture<>();
        private final CrtResponseInputStream body = new CrtResponseInputStream();
        private final HttpVersion version;
        private final boolean manualWindowManagement;
        private RequestLifetime lifetime;
        private volatile boolean headersDelivered;

        private CrtResponseHandler(HttpVersion version, boolean manualWindowManagement) {
            this.version = version;
            this.manualWindowManagement = manualWindowManagement;
        }

        CompletableFuture<HttpResponse> headersFuture() {
            return headersFuture;
        }

        void bind(RequestLifetime lifetime) {
            this.lifetime = lifetime;
            this.body.bindLifetime(lifetime, manualWindowManagement);
        }

        @Override
        public void onResponseHeaders(HttpStreamBase stream, int responseStatusCode, int blockType, HttpHeader[] nextHeaders) {
            if (headersDelivered) {
                return;
            }
            headersDelivered = true;
            var headers = HttpHeaders.ofModifiable(nextHeaders.length);
            for (var header : nextHeaders) {
                headers.addHeader(header.getName(), header.getValue());
            }
            long contentLength = headers.contentLength() == null ? -1 : headers.contentLength();
            var response = HttpResponse.create()
                    .setHttpVersion(crtToSmithyVersion(version))
                    .setStatusCode(responseStatusCode)
                    .setHeaders(headers)
                    .setBody(DataStream.ofInputStream(body, headers.contentType(), contentLength))
                    .toUnmodifiable();
            headersFuture.complete(response);
        }

        @Override
        public int onResponseBody(HttpStreamBase stream, byte[] bodyBytesIn) {
            body.publish(stream, bodyBytesIn);
            return 0;
        }

        @Override
        public void onResponseComplete(HttpStreamBase stream, int errorCode) {
            if (errorCode == CRT.AWS_CRT_SUCCESS) {
                if (!headersDelivered) {
                    headersDelivered = true;
                    var response = HttpResponse.create()
                            .setHttpVersion(crtToSmithyVersion(version))
                            .setStatusCode(stream.getResponseStatusCode())
                            .setHeaders(HttpHeaders.ofModifiable())
                            .setBody(DataStream.ofInputStream(body))
                            .toUnmodifiable();
                    headersFuture.complete(response);
                }
                body.complete();
            } else {
                var failure = new IOException(new CrtRuntimeException(errorCode).toString());
                headersFuture.completeExceptionally(failure);
                body.fail(failure);
            }
        }
    }

    private static final class CrtResponseInputStream extends java.io.InputStream {
        private final java.util.ArrayDeque<Chunk> chunks = new java.util.ArrayDeque<>();
        private Chunk current;
        private RequestLifetime lifetime;
        private IOException failure;
        private boolean eof;
        private boolean closed;
        private boolean manualWindowManagement = true;

        void bindLifetime(RequestLifetime lifetime, boolean manualWindowManagement) {
            this.lifetime = Objects.requireNonNull(lifetime);
            this.manualWindowManagement = manualWindowManagement;
        }

        synchronized void publish(HttpStreamBase stream, byte[] bytes) {
            if (closed) {
                if (manualWindowManagement) {
                    stream.incrementWindow(bytes.length);
                }
                return;
            }
            chunks.addLast(new Chunk(stream, bytes));
            notifyAll();
        }

        synchronized void complete() {
            eof = true;
            notifyAll();
        }

        synchronized void fail(IOException failure) {
            this.failure = failure;
            eof = true;
            notifyAll();
            if (lifetime != null) {
                lifetime.abort();
            }
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int read = read(one, 0, 1);
            return read < 0 ? -1 : (one[0] & 0xFF);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            Chunk chunk;
            synchronized (this) {
                ensureOpen();
                while ((chunk = currentReadableChunk()) == null) {
                    if (failure != null) {
                        throw failure;
                    }
                    if (eof) {
                        if (lifetime != null) {
                            lifetime.complete();
                        }
                        return -1;
                    }
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted waiting for CRT response bytes", e);
                    }
                    ensureOpen();
                }
                int copied = chunk.read(b, off, len);
                if (chunk.exhausted()) {
                    current = null;
                    if (manualWindowManagement) {
                        chunk.stream.incrementWindow(chunk.bytes.length);
                    }
                    if (eof && chunks.isEmpty() && lifetime != null) {
                        lifetime.complete();
                    }
                }
                return copied;
            }
        }

        @Override
        public long transferTo(java.io.OutputStream out) throws IOException {
            long transferred = 0;
            while (true) {
                Chunk chunk;
                synchronized (this) {
                    ensureOpen();
                    while ((chunk = currentReadableChunk()) == null) {
                        if (failure != null) {
                            throw failure;
                        }
                        if (eof) {
                            if (lifetime != null) {
                                lifetime.complete();
                            }
                            return transferred;
                        }
                        try {
                            wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted waiting for CRT response bytes", e);
                        }
                        ensureOpen();
                    }
                }

                int remaining = chunk.remaining();
                out.write(chunk.bytes, chunk.position, remaining);
                transferred += remaining;
                synchronized (this) {
                    chunk.position += remaining;
                    if (chunk.exhausted()) {
                        current = null;
                        if (manualWindowManagement) {
                            chunk.stream.incrementWindow(chunk.bytes.length);
                        }
                        if (eof && chunks.isEmpty() && lifetime != null) {
                            lifetime.complete();
                        }
                    }
                }
            }
        }

        @Override
        public void close() throws IOException {
            synchronized (this) {
                if (closed) {
                    return;
                }
                closed = true;
                while (current != null || !chunks.isEmpty()) {
                    Chunk chunk = current != null ? current : chunks.pollFirst();
                    if (chunk != null && manualWindowManagement) {
                        chunk.stream.incrementWindow(chunk.bytes.length);
                    }
                    current = null;
                }
                notifyAll();
            }
            if (lifetime != null) {
                lifetime.abort();
            }
        }

        private void ensureOpen() throws IOException {
            if (closed) {
                throw new IOException("Stream closed");
            }
        }

        private Chunk currentReadableChunk() {
            if (current != null && !current.exhausted()) {
                return current;
            }
            current = chunks.pollFirst();
            return current;
        }

        private static final class Chunk {
            private final HttpStreamBase stream;
            private final byte[] bytes;
            private int position;

            private Chunk(HttpStreamBase stream, byte[] bytes) {
                this.stream = stream;
                this.bytes = bytes;
            }

            private int read(byte[] target, int off, int len) {
                int toCopy = Math.min(len, bytes.length - position);
                System.arraycopy(bytes, position, target, off, toCopy);
                position += toCopy;
                return toCopy;
            }

            private int remaining() {
                return bytes.length - position;
            }

            private boolean exhausted() {
                return position >= bytes.length;
            }
        }
    }

    public static final class Factory implements ClientTransportFactory<HttpRequest, HttpResponse> {
        @Override
        public String name() {
            return "http-crt";
        }

        @Override
        public CrtHttpClientTransport createTransport(Document node, Document pluginSettings) {
            var config = new CrtHttpTransportConfig().fromDocument(pluginSettings.asStringMap()
                    .getOrDefault("httpConfig", Document.EMPTY_MAP));
            config.fromDocument(node);
            return new CrtHttpClientTransport(config);
        }

        @Override
        public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
            return HttpMessageExchange.INSTANCE;
        }
    }
}
