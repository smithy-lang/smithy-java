/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

    private final ConnectionPool connectionPool;
    private final ProxySelector proxySelector;
    private final Duration requestTimeout;
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    DefaultHttpClient(Builder builder) {
        this.connectionPool = builder.connectionPool;
        this.proxySelector = builder.proxySelector;
        this.requestTimeout = builder.requestTimeout;
    }

    @Override
    public HttpResponse send(HttpRequest request, RequestOptions options) throws IOException {
        Duration timeout = options.requestTimeout() != null ? options.requestTimeout() : requestTimeout;
        return timeout != null ? sendWithTimeout(request, options, timeout) : sendInternal(request);
    }

    private HttpResponse sendInternal(HttpRequest request) throws IOException {
        var target = request.uri();
        List<ProxyConfiguration> proxies = proxySelector.select(target);

        if (proxies.isEmpty()) {
            return sendForRoute(request, Route.from(target, null));
        }

        IOException last = null;
        for (ProxyConfiguration proxy : proxies) {
            Route route = Route.from(target, proxy);
            try {
                return sendForRoute(request, route);
            } catch (IOException e) {
                last = e;
                proxySelector.connectFailed(target, proxy, e);
            }
        }
        throw last;
    }

    private HttpResponse sendForRoute(HttpRequest request, Route route) throws IOException {
        HttpConnection conn = connectionPool.acquire(route);
        HttpExchange exchange;
        try {
            exchange = conn.newExchange(request);
        } catch (Exception e) {
            connectionPool.evict(conn, true);
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException("Failed to create exchange", e);
        }

        try {
            // Write request body
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
                exchange.writeRequestBody(null);
            }

            // Build response
            int statusCode = exchange.responseStatusCode();
            HttpHeaders headers = exchange.responseHeaders();
            HttpVersion version = exchange.responseVersion();
            boolean isH2 = version == HttpVersion.HTTP_2;
            String contentType = exchange.responseContentType();
            long contentLength = exchange.responseContentLength();

            // Wrap body so close releases connection
            DataStream managedBody = new ManagedResponseBody(exchange, conn, isH2, contentType, contentLength);

            return HttpResponse.create()
                    .setStatusCode(statusCode)
                    .setHeaders(headers)
                    .setHttpVersion(version)
                    .setBody(managedBody);
        } catch (IOException e) {
            try {
                exchange.close();
            } catch (IOException ignored) {}
            connectionPool.evict(conn, true);
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
        private final HttpExchange exchange;
        private final HttpConnection conn;
        private final boolean isH2;
        private final String contentType;
        private final long contentLength;
        private boolean consumed;
        private boolean closed;
        private InputStream wrappedStream;
        private ReadableByteChannel wrappedChannel;

        ManagedResponseBody(
                HttpExchange exchange,
                HttpConnection conn,
                boolean isH2,
                String contentType,
                long contentLength
        ) {
            this.exchange = exchange;
            this.conn = conn;
            this.isH2 = isH2;
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
            return !closed && !consumed;
        }

        @Override
        public InputStream asInputStream() {
            markConsumed();
            try {
                InputStream inner = exchange.responseBody();
                wrappedStream = inner;
                return new ManagedResponseInputStream(inner, contentLength, ManagedResponseBody.this::close);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public ReadableByteChannel asChannel() {
            markConsumed();
            try {
                ReadableByteChannel inner = exchange.responseBodyChannel();
                wrappedChannel = inner;
                return new ReadableByteChannel() {
                    @Override
                    public int read(ByteBuffer dst) throws IOException {
                        int n = inner.read(dst);
                        if (n == -1) {
                            ManagedResponseBody.this.close();
                        }
                        return n;
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
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void writeTo(OutputStream out) throws IOException {
            markConsumed();
            InputStream inner = exchange.responseBody();
            wrappedStream = inner;
            try {
                inner.transferTo(out);
            } finally {
                close();
            }
        }

        @Override
        public void writeTo(WritableByteChannel ch) throws IOException {
            markConsumed();
            InputStream inner = exchange.responseBody();
            wrappedStream = inner;
            try {
                inner.transferTo(Channels.newOutputStream(ch));
            } finally {
                close();
            }
        }

        @Override
        public void discard() throws IOException {
            if (closed) {
                return;
            }
            closed = true;

            boolean errored = false;
            try {
                if (wrappedStream == null) {
                    if (wrappedChannel == null) {
                        exchange.discardResponseBody();
                    } else {
                        wrappedChannel.close();
                    }
                } else {
                    wrappedStream.transferTo(OutputStream.nullOutputStream());
                }
            } catch (IOException e) {
                errored = true;
                throw e;
            } finally {
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
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;

            boolean errored = false;

            // H1: drain body for connection reuse. H2: skip — exchange.close() sends RST_STREAM.
            // The body may not have been read at all (wrappedStream == null) — e.g. when the
            // SDK calls discard() without first opening the stream. In that case we still need
            // to drain through the exchange so the H1 keepalive contract is honored; reusing the
            // connection without consuming the response body would corrupt the next exchange.
            //
            // Use a 64 KiB drain buffer rather than InputStream.transferTo's 16 KiB default so
            // a typical 256 KiB body drains in 4 read trips instead of 16.
            if (!isH2) {
                try {
                    if (wrappedStream == null) {
                        if (wrappedChannel == null) {
                            exchange.discardResponseBody();
                        } else {
                            wrappedChannel.close();
                        }
                    } else {
                        wrappedStream.transferTo(OutputStream.nullOutputStream());
                    }
                } catch (IOException ignored) {
                    errored = true;
                }
            }

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

        private void markConsumed() {
            if (consumed) {
                throw new IllegalStateException("DataStream is not replayable and has already been consumed");
            }
            consumed = true;
        }
    }

    private HttpResponse sendWithTimeout(HttpRequest request, RequestOptions options, Duration timeout)
            throws IOException {
        Future<HttpResponse> future = executorService.submit(() -> sendInternal(request));

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
