/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.api.TrailerSupport;
import software.amazon.smithy.java.http.client.connection.ConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Default {@link HttpClient} implementation.
 *
 * <p>Handles connection pooling, interceptors, protocol selection (H1/H2),
 * and bidirectional streaming internally. The caller only sees
 * {@code send(request) → response}.
 */
final class DefaultHttpClient implements HttpClient {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(DefaultHttpClient.class);
    private static final OutputStream NULL_OUTPUT_STREAM = OutputStream.nullOutputStream();
    private final ConnectionPool connectionPool;
    private final ProxySelector proxySelector;
    private final List<HttpInterceptor> interceptors;
    private final Duration requestTimeout;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    DefaultHttpClient(Builder builder) {
        this.connectionPool = builder.connectionPool;
        this.interceptors = List.copyOf(builder.interceptors);
        this.proxySelector = builder.proxySelector;
        this.requestTimeout = builder.requestTimeout;
    }

    @Override
    public HttpResponse send(HttpRequest request, RequestOptions options) throws IOException {
        Duration timeout = options.requestTimeout() != null ? options.requestTimeout() : requestTimeout;
        return timeout != null ? sendWithTimeout(request, options, timeout) : sendInternal(request, options);
    }

    private HttpResponse sendInternal(HttpRequest request, RequestOptions options) throws IOException {
        var resolvedInterceptors = options.resolveInterceptors(interceptors);
        Context context = options.context();

        // 1. beforeRequest interceptors
        request = applyBeforeRequest(resolvedInterceptors, request, context);

        // 2. preemptRequest interceptors
        HttpResponse preempted = applyPreemptRequest(resolvedInterceptors, request, context);
        if (preempted != null) {
            try {
                return applyResponseInterceptors(resolvedInterceptors, request, context, preempted);
            } catch (IOException e) {
                HttpResponse recovery = applyOnError(resolvedInterceptors, request, context, e);
                if (recovery != null) {
                    return recovery;
                }
                throw e;
            }
        }

        // 3. Acquire connection and open stream
        AcquiredStream acquired;
        try {
            acquired = acquireAndOpenStream(request, context);
        } catch (IOException e) {
            HttpResponse recovery = applyOnError(resolvedInterceptors, request, context, e);
            if (recovery != null) {
                return recovery;
            }
            throw e;
        }

        HttpConnection conn = acquired.conn();
        HttpExchange exchange = acquired.exchange();

        boolean errored = false;
        try {
            // 5. Write request body
            DataStream requestBody = request.body();
            boolean hasBody = requestBody != null && requestBody.contentLength() != 0;

            // Set request trailers before writing body so the exchange knows to defer END_STREAM
            if (hasBody && requestBody instanceof TrailerSupport ts) {
                exchange.setRequestTrailers(ts.trailerHeaders());
            }

            if (hasBody && exchange.supportsBidirectionalStreaming() && !shouldWriteH2BodyInline(requestBody)) {
                // H2: write body on background VT for full duplex
                final DataStream body = requestBody;
                Thread.startVirtualThread(() -> {
                    try {
                        exchange.writeRequestBody(body);
                    } catch (IOException e) {
                        LOGGER.debug("Error writing request body: {}", e.getMessage());
                    }
                });
            } else if (hasBody) {
                // H1, or replayable bounded H2 bodies: write inline
                exchange.writeRequestBody(requestBody);
            } else {
                // No body — close request stream to send END_STREAM
                exchange.requestBody().close();
            }

            // 6. Build response
            int statusCode = exchange.responseStatusCode();
            HttpHeaders headers = exchange.responseHeaders();
            HttpVersion version = exchange.responseVersion();

            // Determine close behavior based on protocol
            boolean isH2 = version == HttpVersion.HTTP_2;

            DataStream responseBody = DataStream.ofStreamOrChannel(
                    exchange::responseBody,
                    exchange::responseBodyChannel,
                    headers.contentType(),
                    headers.contentLength() != null ? headers.contentLength() : -1);

            // Wrap body so close releases connection
            DataStream managedBody = new ManagedResponseBody(responseBody, exchange, conn, isH2);

            HttpResponse response = HttpResponse.create()
                    .setStatusCode(statusCode)
                    .setHeaders(headers)
                    .setHttpVersion(version)
                    .setBody(managedBody);

            // 7. interceptResponse
            response = applyResponseInterceptors(resolvedInterceptors, request, context, response);

            return response;

        } catch (IOException e) {
            errored = true;
            try {
                exchange.close();
            } catch (IOException ignored) {}
            connectionPool.evict(conn, true);

            HttpResponse recovery = applyOnError(resolvedInterceptors, request, context, e);
            if (recovery != null) {
                return recovery;
            }
            throw e;
        }
    }

    private static boolean shouldWriteH2BodyInline(DataStream body) {
        return body.isReplayable() && body.hasKnownLength();
    }

    /**
     * Wraps the response body DataStream to handle connection lifecycle on close.
     */
    private final class ManagedResponseBody implements DataStream, TrailerSupport {
        private final DataStream delegate;
        private final HttpExchange exchange;
        private final HttpConnection conn;
        private final boolean isH2;
        private boolean closed;
        private InputStream wrappedStream;

        ManagedResponseBody(DataStream delegate, HttpExchange exchange, HttpConnection conn, boolean isH2) {
            this.delegate = delegate;
            this.exchange = exchange;
            this.conn = conn;
            this.isH2 = isH2;
        }

        @Override
        public long contentLength() {
            return delegate.contentLength();
        }

        @Override
        public String contentType() {
            return delegate.contentType();
        }

        @Override
        public boolean isReplayable() {
            return false;
        }

        @Override
        public boolean isAvailable() {
            return !closed;
        }

        @Override
        public InputStream asInputStream() {
            InputStream inner = delegate.asInputStream();
            wrappedStream = inner;
            return new java.io.FilterInputStream(inner) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        ManagedResponseBody.this.close();
                    }
                }
            };
        }

        @Override
        public java.nio.channels.ReadableByteChannel asChannel() {
            java.nio.channels.ReadableByteChannel inner = delegate.asChannel();
            return new java.nio.channels.ReadableByteChannel() {
                @Override
                public int read(java.nio.ByteBuffer dst) throws IOException {
                    return inner.read(dst);
                }

                @Override
                public boolean isOpen() {
                    return inner.isOpen();
                }

                @Override
                public void close() throws IOException {
                    try {
                        inner.close();
                    } finally {
                        ManagedResponseBody.this.close();
                    }
                }
            };
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            delegate.writeTo(out);
        }

        @Override
        public void writeTo(java.nio.channels.WritableByteChannel ch) throws IOException {
            delegate.writeTo(ch);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;

            boolean errored = false;

            // H1: drain body for connection reuse. H2: skip — exchange.close() sends RST_STREAM.
            if (!isH2) {
                try {
                    if (wrappedStream != null) {
                        wrappedStream.transferTo(NULL_OUTPUT_STREAM);
                    }
                } catch (IOException ignored) {
                    errored = true;
                }
            }

            try {
                delegate.close();
            } catch (Exception ignored) {}

            try {
                exchange.close();
            } catch (Exception e) {
                errored = true;
            }

            if (errored) {
                connectionPool.evict(conn, true);
            } else {
                connectionPool.release(conn);
            }
        }

        @Override
        public HttpHeaders trailerHeaders() {
            return exchange.responseTrailerHeaders();
        }
    }

    private record AcquiredStream(HttpConnection conn, HttpExchange exchange) {}

    private AcquiredStream acquireAndOpenStream(HttpRequest request, Context context) throws IOException {
        var target = request.uri();
        List<ProxyConfiguration> proxies = proxySelector.select(target, context);

        if (proxies.isEmpty()) {
            return acquireForRoute(request, Route.from(target, null));
        }

        IOException last = null;
        for (ProxyConfiguration proxy : proxies) {
            Route route = Route.from(target, proxy);
            try {
                return acquireForRoute(request, route);
            } catch (IOException e) {
                last = e;
                proxySelector.connectFailed(target, context, proxy, e);
            }
        }
        throw last;
    }

    private AcquiredStream acquireForRoute(HttpRequest request, Route route) throws IOException {
        HttpConnection conn = connectionPool.acquire(route);
        try {
            HttpExchange exchange = conn.newExchange(request);
            return new AcquiredStream(conn, exchange);
        } catch (Exception e) {
            connectionPool.evict(conn, true);
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException("Failed to create exchange", e);
        }
    }

    private HttpResponse applyResponseInterceptors(
            List<HttpInterceptor> resolved,
            HttpRequest request,
            Context context,
            HttpResponse response
    ) throws IOException {
        HttpResponse current = response;
        for (int i = resolved.size() - 1; i >= 0; i--) {
            HttpResponse replacement = resolved.get(i).interceptResponse(this, request, context, current);
            if (replacement != null) {
                current = replacement;
            }
        }
        return current;
    }

    private HttpRequest applyBeforeRequest(List<HttpInterceptor> resolved, HttpRequest request, Context context)
            throws IOException {
        HttpRequest modified = request;
        for (HttpInterceptor interceptor : resolved) {
            modified = interceptor.beforeRequest(this, modified, context);
        }
        return modified;
    }

    private HttpResponse applyPreemptRequest(List<HttpInterceptor> resolved, HttpRequest request, Context context)
            throws IOException {
        for (HttpInterceptor interceptor : resolved) {
            HttpResponse response = interceptor.preemptRequest(this, request, context);
            if (response != null) {
                return response;
            }
        }
        return null;
    }

    private HttpResponse applyOnError(
            List<HttpInterceptor> resolved,
            HttpRequest request,
            Context context,
            IOException exception
    ) throws IOException {
        for (int i = resolved.size() - 1; i >= 0; i--) {
            HttpResponse recovery = resolved.get(i).onError(this, request, context, exception);
            if (recovery != null) {
                return recovery;
            }
        }
        return null;
    }

    private HttpResponse sendWithTimeout(HttpRequest request, RequestOptions options, Duration timeout)
            throws IOException {
        Future<HttpResponse> future = executorService.submit(() -> sendInternal(request, options));

        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new IOException(String.format(
                    "Request to `%s` exceeded request timeout of %s seconds",
                    request.uri().getHost(),
                    timeout.toSeconds()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for HTTP request to complete to `"
                    + request.uri().getHost() + '`', e);
        } catch (ExecutionException e) {
            throw unwrap(e);
        }
    }

    private static IOException unwrap(ExecutionException e) throws IOException {
        var cause = e.getCause();
        return switch (cause) {
            case IOException io -> throw io;
            case RuntimeException re -> throw re;
            case Error err -> throw err;
            case null -> new IOException("Unexpected exception", e);
            default -> new IOException("Unexpected exception", cause);
        };
    }

    @Override
    public void close() throws IOException {
        executorService.close();
        connectionPool.close();
    }

    @Override
    public void shutdown(Duration timeout) {
        executorService.shutdown();
        try {
            executorService.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executorService.shutdownNow();
        try {
            connectionPool.shutdown(timeout);
        } catch (IOException ignored) {}
    }
}
