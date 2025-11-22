/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.connection.ConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Main HTTP client. Thread-safe.
 *
 * <p>This client supports both buffered ({@link #send(HttpRequest)}) and streaming
 * ({@link #exchange(HttpRequest)}) request/response patterns.
 *
 * <p>Behavior can be customized via {@link HttpInterceptor}s, which provide hooks for:
 * <ul>
 *   <li>Modifying requests before sending</li>
 *   <li>Short-circuiting requests (e.g., caching)</li>
 *   <li>Intercepting responses (e.g., auth retry, redirects)</li>
 *   <li>Handling errors (e.g., fallback responses)</li>
 * </ul>
 */
public final class HttpClient implements AutoCloseable {

    private final ConnectionPool connectionPool;
    private final ProxyConfiguration proxyConfiguration;
    private final List<HttpInterceptor> interceptors;
    private final Duration requestTimeout;
    private final ScheduledExecutorService timeoutScheduler;

    private HttpClient(Builder builder) {
        this.connectionPool = builder.connectionPool;
        this.interceptors = List.copyOf(builder.interceptors);
        this.proxyConfiguration = builder.proxyConfiguration;
        this.requestTimeout = builder.requestTimeout;

        // Single-thread scheduler for timeouts
        this.timeoutScheduler = builder.requestTimeout == null
                ? null
                : Executors.newSingleThreadScheduledExecutor(HttpClient::timeoutWatcher);
    }

    private static Thread timeoutWatcher(Runnable r) {
        Thread t = new Thread(r, "http-client-timeout");
        t.setDaemon(true);
        return t;
    }

    /**
     * Send a buffered request/response exchange.
     *
     * <p>This is a convenience method that:
     * <ol>
     *   <li>Writes the request body (if present)</li>
     *   <li>Reads and buffers the entire response in memory</li>
     *   <li>Closes the exchange</li>
     * </ol>
     *
     * <p>Interceptors can modify the request, short-circuit execution, retry on errors,
     * or replace the response. For example, {@code RedirectInterceptor} can automatically
     * follow redirects.
     *
     * @param request the HTTP request to send
     * @return the HTTP response
     * @throws IOException if the request fails
     */
    public HttpResponse send(HttpRequest request) throws IOException {
        return send(request, RequestOptions.defaults());
    }

    /**
     * Send a request with request options.
     *
     * @param request request to send.
     * @param options options to apply.
     * @return the HTTP response
     * @throws IOException if the request fails
     */
    public HttpResponse send(HttpRequest request, RequestOptions options) throws IOException {
        if (requestTimeout != null) {
            return withTimeout(() -> sendInternal(request, options), request);
        }
        return sendInternal(request, options);
    }

    private HttpResponse sendInternal(HttpRequest request, RequestOptions options) throws IOException {
        var resolvedInterceptors = options.resolveInterceptors(interceptors);

        HttpRequest modifiedRequest = applyBeforeRequest(resolvedInterceptors, request, options.context());

        HttpResponse preempted = applyPreemptRequest(resolvedInterceptors, modifiedRequest, options.context());
        if (preempted != null) {
            HttpResponse intercepted = applyInterceptResponse(
                    resolvedInterceptors,
                    modifiedRequest,
                    options.context(),
                    preempted);
            if (intercepted != null) {
                preempted = intercepted;
            }
            return preempted;
        }

        HttpResponse response;
        try {
            response = executeRequest(modifiedRequest);
        } catch (IOException e) {
            HttpResponse recovery = applyOnError(resolvedInterceptors, modifiedRequest, options.context(), e);
            if (recovery != null) {
                return recovery;
            }
            throw e;
        }

        HttpResponse intercepted = applyInterceptResponse(
                resolvedInterceptors,
                modifiedRequest,
                options.context(),
                response);
        if (intercepted != null) {
            response = intercepted;
        }

        return response;
    }

    /**
     * Execute the actual HTTP request and buffer the response.
     */
    private HttpResponse executeRequest(HttpRequest request) throws IOException {
        Route route = Route.from(request.uri(), proxyConfiguration);
        HttpConnection conn = connectionPool.acquire(route);
        boolean success = false;

        try (HttpExchange exchange = conn.newExchange(request)) {
            if (request.body() != null) {
                try (OutputStream out = exchange.requestBody()) {
                    request.body().asInputStream().transferTo(out);
                }
            } else {
                exchange.requestBody().close();
            }

            int statusCode = exchange.statusCode();
            HttpHeaders headers = exchange.responseHeaders();

            byte[] bodyBytes;
            try (InputStream in = exchange.responseBody()) {
                bodyBytes = in.readAllBytes();
            }

            success = true;
            return HttpResponse.builder()
                    .statusCode(statusCode)
                    .headers(headers)
                    .body(DataStream.ofInputStream(new ByteArrayInputStream(bodyBytes)))
                    .build();
        } finally {
            if (success) {
                connectionPool.release(conn);
            } else {
                connectionPool.evict(conn, true);
            }
        }
    }

    /**
     * Create a bidirectional streaming exchange.
     *
     * <p>This is a low-level API that gives full control over request/response streams.
     * The caller is responsible for:
     * <ul>
     *   <li>Writing the request body and closing it</li>
     *   <li>Reading the response body</li>
     *   <li>Closing the exchange when done</li>
     * </ul>
     *
     * <p>Interceptors work with {@code exchange()}, but with limitations:
     * <ul>
     *   <li>{@code interceptResponse} can see headers/status and replace response, but cannot safely retry</li>
     *   <li>Use {@code context.isModifiable()} to check if retry is safe</li>
     * </ul>
     *
     * @param request the HTTP request
     * @return a streaming exchange
     * @throws IOException if the exchange cannot be created
     */
    public HttpExchange exchange(HttpRequest request) throws IOException {
        return exchange(request, RequestOptions.defaults());
    }

    /**
     * Create a bidirectional streaming exchange with options.
     *
     * @param request the HTTP request
     * @param options options to apply
     * @return a streaming exchange
     * @throws IOException if the exchange cannot be created
     */
    public HttpExchange exchange(HttpRequest request, RequestOptions options) throws IOException {
        var resolvedInterceptors = options.resolveInterceptors(interceptors);

        HttpRequest modifiedRequest = applyBeforeRequest(resolvedInterceptors, request, options.context());

        HttpResponse preempted = applyPreemptRequest(resolvedInterceptors, modifiedRequest, options.context());
        if (preempted != null) {
            HttpResponse intercepted = applyInterceptResponse(
                    resolvedInterceptors,
                    modifiedRequest,
                    options.context(),
                    preempted);
            if (intercepted != null) {
                preempted = intercepted;
            }
            return new BufferedHttpExchange(preempted);
        }

        try {
            return createManagedExchange(modifiedRequest, options.context(), resolvedInterceptors);
        } catch (IOException e) {
            HttpResponse recovery = applyOnError(resolvedInterceptors, modifiedRequest, options.context(), e);
            if (recovery != null) {
                return new BufferedHttpExchange(recovery);
            }
            throw e;
        }
    }

    /**
     * Create a managed exchange with connection pooling and interceptor support.
     *
     * @param request the HTTP request
     * @param context the request context
     * @param resolvedInterceptors interceptors to apply
     * @return managed exchange
     * @throws IOException if connection acquisition or exchange creation fails
     */
    private HttpExchange createManagedExchange(
            HttpRequest request,
            Context context,
            List<HttpInterceptor> resolvedInterceptors
    ) throws IOException {
        Route route = Route.from(request.uri(), proxyConfiguration);
        HttpConnection conn = connectionPool.acquire(route);
        try {
            HttpExchange baseExchange = conn.newExchange(request);
            return new ManagedHttpExchange(baseExchange,
                    conn,
                    connectionPool,
                    request,
                    context,
                    resolvedInterceptors,
                    this);
        } catch (IOException e) {
            connectionPool.evict(conn, true);
            throw e;
        }
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

    HttpResponse applyInterceptResponse(
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
        return current == response ? null : current;
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

    /**
     * Execute an operation with a timeout using scheduled executor.
     */
    private <T> T withTimeout(IOSupplier<T> operation, HttpRequest request) throws IOException {
        Thread workerThread = Thread.currentThread();
        var timedOut = new AtomicBoolean();

        var timeoutFuture = timeoutScheduler.schedule(() -> {
            timedOut.set(true);
            workerThread.interrupt();
        }, requestTimeout.toMillis(), TimeUnit.MILLISECONDS);

        try {
            return operation.get();
        } catch (IOException e) {
            if (timedOut.get()) {
                throw new IOException("Request to " + request.uri() + " exceeded timeout of "
                        + requestTimeout.toSeconds() + " seconds", e);
            }
            throw e;
        } finally {
            timeoutFuture.cancel(false);
            // Only clear interrupt flag if we caused it (avoid swallowing external interrupts)
            if (timedOut.get()) {
                Thread.interrupted();
            }
        }
    }

    @FunctionalInterface
    private interface IOSupplier<T> {
        T get() throws IOException;
    }

    @Override
    public void close() throws IOException {
        if (timeoutScheduler != null) {
            timeoutScheduler.shutdownNow();
        }
        connectionPool.close();
    }

    /**
     * Builder for client configuration.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ConnectionPool connectionPool;
        private Duration requestTimeout;
        private final Deque<HttpInterceptor> interceptors = new ArrayDeque<>();
        private ProxyConfiguration proxyConfiguration;

        private Builder() {}

        /**
         * Add an interceptor to customize request/response handling.
         *
         * <p>Interceptors are applied in the order they are added:
         * <ul>
         *   <li>{@code beforeRequest} - forward order (first added, first called)</li>
         *   <li>{@code preemptRequest} - forward order</li>
         *   <li>{@code interceptResponse} - reverse order (last added, first called)</li>
         *   <li>{@code onError} - reverse order</li>
         * </ul>
         *
         * @param interceptor the interceptor to add
         * @return this builder
         */
        public Builder addInterceptor(HttpInterceptor interceptor) {
            interceptors.add(Objects.requireNonNull(interceptor, "interceptor"));
            return this;
        }

        /**
         * Add an interceptor to the front of the list of interceptors ot apply.
         *
         * @param interceptor the interceptor to add to the front.
         * @return this builder
         * @see #addInterceptor(HttpInterceptor)
         */
        public Builder addInterceptorFirst(HttpInterceptor interceptor) {
            interceptors.addFirst(Objects.requireNonNull(interceptor, "interceptor"));
            return this;
        }

        /**
         * Set a custom connection pool.
         *
         * @param pool the connection pool to use
         * @return this builder
         */
        public Builder connectionPool(ConnectionPool pool) {
            this.connectionPool = pool;
            return this;
        }

        /**
         * Set total request timeout including redirects and retries (default: none).
         *
         * <p>If set, the entire buffered request (including any interceptor retries,
         * redirects, and authentication flows) must complete within this duration,
         * or an {@link IOException} is thrown.
         *
         * <p><b>Scope:</b> This timeout only applies to {@link HttpClient#send} calls
         * (buffered requests). Streaming {@link HttpClient#exchange} calls are not
         * bounded by this timeout since the caller controls when to read/write.
         *
         * <p><b>Implementation:</b> Timeout is enforced via {@link Thread#interrupt()}.
         * Interceptors and underlying I/O must be interruptible for the timeout to be
         * effective. Code that swallows interrupts may delay the actual abort.
         *
         * <p>If not set (null), requests have no overall timeout and are only limited by
         * the connect and read timeouts.
         *
         * @param timeout total request timeout duration, or null for no timeout
         * @return this builder
         * @throws IllegalArgumentException if timeout is negative or zero
         */
        public Builder requestTimeout(Duration timeout) {
            if (timeout != null && (timeout.isNegative() || timeout.isZero())) {
                throw new IllegalArgumentException("requestTimeout must be positive or null: " + timeout);
            }
            this.requestTimeout = timeout;
            return this;
        }

        /**
         * Set proxy configuration for all connections made by this client.
         *
         * <p>When configured, all HTTP requests will be routed through the proxy
         * unless the target host matches one of the non-proxy hosts.
         *
         * <p>For HTTPS requests, the client establishes a CONNECT tunnel through
         * the proxy, then performs TLS handshake through the tunnel.
         *
         * <p>For HTTP requests, the client connects to the proxy and sends
         * requests with absolute URIs.
         *
         * @param proxy the proxy configuration, or null for direct connections
         * @return this builder
         * @see ProxyConfiguration
         */
        public Builder proxy(ProxyConfiguration proxy) {
            this.proxyConfiguration = proxy;
            return this;
        }

        /**
         * Set proxy configuration using a URI string.
         *
         * <p>Convenience method that creates an HTTP proxy configuration from
         * a URI string. For more advanced configuration (authentication,
         * bypass rules, SOCKS proxy), use {@link #proxy(ProxyConfiguration)}.
         *
         * @param proxyUri the proxy URI (e.g., {@code "http://proxy.example.com:8080"})
         * @return this builder
         * @throws IllegalArgumentException if proxyUri is invalid
         */
        public Builder proxy(String proxyUri) {
            if (proxyUri == null) {
                this.proxyConfiguration = null;
            } else {
                this.proxyConfiguration = ProxyConfiguration.builder()
                        .proxyUri(proxyUri)
                        .type(ProxyConfiguration.ProxyType.HTTP)
                        .build();
            }
            return this;
        }

        /**
         * Build the HTTP client.
         *
         * @return a new HTTP client instance
         * @throws IllegalStateException if the configuration is invalid
         */
        public HttpClient build() {
            if (connectionPool == null) {
                connectionPool = HttpConnectionPool.builder().build();
            }
            return new HttpClient(this);
        }
    }
}
