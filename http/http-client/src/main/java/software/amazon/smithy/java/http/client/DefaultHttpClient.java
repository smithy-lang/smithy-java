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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
    private final List<HttpClientListener> listeners;
    private final boolean hasListeners;
    private final AtomicLong nextExchangeId = new AtomicLong();
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    DefaultHttpClient(Builder builder) {
        this.connectionPool = builder.connectionPool;
        this.proxySelector = builder.proxySelector;
        this.requestTimeout = builder.requestTimeout;
        this.listeners = builder.resolvedConnectionConfig.listeners();
        this.hasListeners = !listeners.isEmpty();
    }

    @Override
    public HttpResponse send(HttpRequest request, RequestOptions options) throws IOException {
        Duration timeout = options.requestTimeout() != null ? options.requestTimeout() : requestTimeout;
        // The exchange id and the request-ended latch are owned here so the terminal onRequestEnd
        // fires exactly once regardless of which path completes the request:
        //   - success  → ManagedResponseBody fires it when the response body is closed
        //   - failure  → the catch below fires it (single owner of final failure, including a
        //                timeout observed on this caller thread rather than the worker thread)
        // A per-route attempt failure inside the proxy loop must NOT end the exchange, since a later
        // proxy may still succeed; sendForRoute therefore no longer fires onRequestEnd.
        long exchangeId = nextExchangeId.incrementAndGet();
        AtomicBoolean requestEnded = new AtomicBoolean();
        notifyRequestStart(exchangeId, request);
        try {
            return timeout != null
                    ? sendWithTimeout(request, options, timeout, exchangeId, requestEnded)
                    : sendInternal(request, options, exchangeId, requestEnded);
        } catch (IOException | RuntimeException e) {
            notifyRequestEnd(exchangeId, requestEnded, e);
            throw e;
        }
    }

    private HttpResponse sendInternal(
            HttpRequest request,
            RequestOptions options,
            long exchangeId,
            AtomicBoolean requestEnded
    ) throws IOException {
        var target = request.uri();
        List<ProxyConfiguration> proxies = proxySelector.select(target);
        if (proxies.isEmpty()) {
            return sendForRoute(request, options, Route.from(target, null), exchangeId, requestEnded);
        }

        IOException last = null;
        for (ProxyConfiguration proxy : proxies) {
            Route route = Route.from(target, proxy);
            try {
                return sendForRoute(request, options, route, exchangeId, requestEnded);
            } catch (IOException e) {
                last = e;
                proxySelector.connectFailed(target, proxy, e);
            }
        }
        throw last;
    }

    private HttpResponse sendForRoute(
            HttpRequest request,
            RequestOptions options,
            Route route,
            long exchangeId,
            AtomicBoolean requestEnded
    ) throws IOException {
        HttpConnection conn = connectionPool.acquire(route, exchangeId, options);
        HttpExchange exchange;
        try {
            exchange = conn.newExchange(request, options);
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
                // No body, so close request stream to send END_STREAM
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
            DataStream managedBody = new ManagedResponseBody(
                    exchange,
                    conn,
                    isH2,
                    contentType,
                    contentLength,
                    exchangeId,
                    requestEnded);

            return HttpResponse.create()
                    .setStatusCode(statusCode)
                    .setHeaders(headers)
                    .setHttpVersion(version)
                    .setBody(managedBody);
        } catch (IOException e) {
            try {
                exchange.close();
            } catch (IOException closeError) {
                LOGGER.debug("Error closing exchange after request failure: {}", closeError.getMessage());
            }
            connectionPool.evict(conn, true);
            // Do not fire onRequestEnd here: a per-route attempt failure may be retried on the next
            // proxy, and send() owns the single terminal failure event.
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
        private final long exchangeId;
        private final AtomicBoolean requestEnded;
        // close()/discard() release or evict the pooled connection; the stream/channel wrappers returned to
        // the caller can be closed from a different thread than the one that built the response, so the
        // one-shot guard must be atomic to prevent two closers both releasing the same connection.
        private final AtomicBoolean closed = new AtomicBoolean();
        private boolean consumed;
        // Written by the consuming thread (asInputStream/asChannel/writeTo) and read by close()/discard(),
        // which may run on a different thread; volatile so the drain path sees the correct wrapper rather
        // than a stale null (which would pick the wrong drain branch and corrupt a reused H1 connection).
        private volatile InputStream wrappedStream;
        private volatile ReadableByteChannel wrappedChannel;

        ManagedResponseBody(
                HttpExchange exchange,
                HttpConnection conn,
                boolean isH2,
                String contentType,
                long contentLength,
                long exchangeId,
                AtomicBoolean requestEnded
        ) {
            this.exchange = exchange;
            this.conn = conn;
            this.isH2 = isH2;
            this.contentType = contentType;
            this.contentLength = contentLength;
            this.exchangeId = exchangeId;
            this.requestEnded = requestEnded;
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
            return !closed.get() && !consumed;
        }

        @Override
        public InputStream asInputStream() {
            markConsumed();
            try {
                InputStream inner = exchange.responseBody();
                wrappedStream = inner;
                return new ManagedResponseInputStream(
                        inner,
                        contentLength,
                        ManagedResponseBody.this::close,
                        ManagedResponseBody.this::fail);
            } catch (IOException e) {
                fail(e);
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
                        try {
                            int n = inner.read(dst);
                            if (n == -1) {
                                ManagedResponseBody.this.close();
                            }
                            return n;
                        } catch (IOException e) {
                            ManagedResponseBody.this.fail(e);
                            throw e;
                        }
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
                fail(e);
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
            } catch (IOException e) {
                fail(e);
                throw e;
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
            } catch (IOException e) {
                fail(e);
                throw e;
            } finally {
                close();
            }
        }

        @Override
        public void discard() throws IOException {
            if (!closed.compareAndSet(false, true)) {
                return;
            }

            boolean errored = false;
            Throwable error = null;
            try {
                drainOrDiscardBody();
            } catch (IOException e) {
                errored = true;
                error = e;
                throw e;
            } finally {
                finishExchange(errored, error);
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }

            boolean errored = false;
            Throwable error = null;

            // H1: drain body for connection reuse. H2: skip — exchange.close() sends RST_STREAM.
            // The body may not have been read at all (wrappedStream == null) — e.g. when the
            // SDK calls discard() without first opening the stream. In that case we still need
            // to drain through the exchange so the H1 keepalive contract is honored; reusing the
            // connection without consuming the response body would corrupt the next exchange.
            if (!isH2) {
                try {
                    drainOrDiscardBody();
                } catch (IOException e) {
                    errored = true;
                    error = e;
                }
            }

            finishExchange(errored, error);
        }

        /**
         * Release the response body according to how the caller opened it: drain an opened stream (so an
         * H1 keepalive connection is left clean for reuse), close an opened channel, or — if the body was
         * never opened — discard it at the exchange level.
         */
        private void drainOrDiscardBody() throws IOException {
            if (wrappedStream != null) {
                wrappedStream.transferTo(OutputStream.nullOutputStream());
            } else if (wrappedChannel != null) {
                wrappedChannel.close();
            } else {
                exchange.discardResponseBody();
            }
        }

        // Close the exchange, then release the connection to the pool (or evict it on error) and fire the
        // terminal onRequestEnd. An exception from {@link HttpExchange#close()} marks the exchange errored,
        // preserving any earlier error as the reported cause.
        private void finishExchange(boolean errored, Throwable error) {
            try {
                exchange.close();
            } catch (Exception e) {
                errored = true;
                error = error == null ? e : error;
            }

            if (errored) {
                connectionPool.evict(conn, true);
            } else {
                connectionPool.release(conn);
            }

            end(error);
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

        private void end(Throwable error) {
            notifyRequestEnd(exchangeId, requestEnded, error);
        }

        /**
         * Terminal for a failed body read (e.g. an interrupted or errored stream read): close the exchange
         * and fire {@code onRequestEnd} with the failure rather than reporting a clean close.
         *
         * <p>This evicts the physical connection for both H1 and H2. For H1 that is required — a connection
         * abandoned mid-response can't be reused. For H2 it is conservative: a single failed stream only
         * strictly needs a RST_STREAM with the connection kept for other/future streams, but we currently
         * evict the whole connection rather than implement stream-only recovery. One-shot via the same
         * {@code closed} latch as {@link #close()}.
         */
        private void fail(Throwable error) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                exchange.close();
            } catch (Exception ignored) {
                // already failing; the original error is the one worth reporting
            }
            connectionPool.evict(conn, true);
            end(error);
        }
    }

    private HttpResponse sendWithTimeout(
            HttpRequest request,
            RequestOptions options,
            Duration timeout,
            long exchangeId,
            AtomicBoolean requestEnded
    ) throws IOException {
        Future<HttpResponse> future =
                executorService.submit(() -> sendInternal(request, options, exchangeId, requestEnded));

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

    private void notifyRequestStart(long exchangeId, HttpRequest request) {
        if (hasListeners) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onRequestStart(exchangeId, request);
                } catch (Throwable e) {
                    listenerFailed("onRequestStart", e);
                }
            }
        }
    }

    private void notifyRequestEnd(long exchangeId, AtomicBoolean requestEnded, Throwable error) {
        if (hasListeners && requestEnded.compareAndSet(false, true)) {
            for (HttpClientListener listener : listeners) {
                try {
                    listener.onRequestEnd(exchangeId, error);
                } catch (Throwable e) {
                    listenerFailed("onRequestEnd", e);
                }
            }
        }
    }

    private static void listenerFailed(String event, Throwable error) {
        if (error instanceof VirtualMachineError) {
            throw (VirtualMachineError) error;
        }
        LOGGER.warn("HTTP client listener failed in {}", event, error);
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
        } catch (IOException e) {
            LOGGER.debug("Error shutting down connection pool: {}", e.getMessage());
        }
    }
}
