/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.connection.ConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;

/**
 * Blocking, virtual-thread-friendly HTTP client.
 *
 * <p>This client supports both simple ({@link #send(HttpRequest)}) and bidirectional streaming
 * ({@link #newExchange(HttpRequest)}) request/response patterns. Both return streaming responses.
 *
 * <p>The client is intentionally minimal. Behavior can be layered on top of the client via {@link HttpInterceptor}s.
 */
public interface HttpClient extends AutoCloseable {
    /**
     * Sends a request and returns a streaming response.
     *
     * <p>This is a convenience method that:
     * <ol>
     *   <li>Creates an {@link HttpExchange}</li>
     *   <li>Writes the request body (if present)</li>
     *   <li>Returns an {@link HttpResponse} with a streaming body</li>
     * </ol>
     *
     * <p>The response body streams directly from the socket. The caller must close the response body stream when
     * done to release the connection back to the pool.
     *
     * <p>Interceptors can modify the request, short-circuit execution, retry on errors, or replace the response.
     *
     * @param request the HTTP request to send
     * @return the HTTP response with streaming body
     * @throws IOException if the request fails
     */
    default HttpResponse send(HttpRequest request) throws IOException {
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
    HttpResponse send(HttpRequest request, RequestOptions options) throws IOException;

    /**
     * Create a streaming exchange.
     *
     * <p>This is a low-level API that gives full control over request/response streams.
     * The caller is responsible for:
     * <ul>
     *   <li>Writing the request body via {@link HttpExchange#requestBody()} and closing it</li>
     *   <li>Reading the response body via {@link HttpExchange#responseBody()}</li>
     *   <li>Closing the exchange when done (or relying on auto-close when both streams close)</li>
     * </ul>
     *
     * <p><b>IMPORTANT:</b> Any body set on the {@link HttpRequest} is NOT automatically written. You must write the
     * request body manually via {@link HttpExchange#requestBody()}. However, the Content-Length header, if present,
     * on the request _is_ sent as a header automatically, so you must write the same number of bytes.
     * Use {@link #send(HttpRequest)} if you want automatic request body handling.
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
    default HttpExchange newExchange(HttpRequest request) throws IOException {
        return newExchange(request, RequestOptions.defaults());
    }

    /**
     * Create a streaming exchange with options.
     *
     * <p><b>IMPORTANT:</b> Any body set on the {@link HttpRequest} is NOT automatically written. You must write the
     * request body manually via {@link HttpExchange#requestBody()}.
     *
     * @param request the HTTP request
     * @param options options to apply
     * @return a streaming exchange
     * @throws IOException if the exchange cannot be created
     * @see #newExchange(HttpRequest)
     */
    HttpExchange newExchange(HttpRequest request, RequestOptions options) throws IOException;

    /**
     * Closes the client and its underlying connection pool.
     *
     * <p>Active connections are closed immediately. Pending requests may fail with an IOException.
     *
     * @throws IOException if an I/O error occurs while closing
     */
    @Override
    void close() throws IOException;

    /**
     * Gracefully shuts down the client, waiting for in-flight requests to complete.
     *
     * <p>No new requests are accepted after this method is called. Existing requests
     * are allowed to complete until the timeout expires, after which connections are
     * forcibly closed.
     *
     * @param timeout maximum time to wait for in-flight requests to complete
     */
    void shutdown(Duration timeout);

    /**
     * Builder to create a new default HTTP client.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Builder used to create a default HTTP client implementation.
     */
    final class Builder {
        ConnectionPool connectionPool;
        Duration requestTimeout;
        final Deque<HttpInterceptor> interceptors = new ArrayDeque<>();
        ProxySelector proxySelector = ProxySelector.direct();

        private Builder() {}

        /**
         * Add an interceptor to customize request/response handling.
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
         * (buffered requests). Streaming {@link HttpClient#newExchange} calls are not
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
         * <p>When configured, all HTTP requests will be routed through the proxy unless the target host matches
         * one of the non-proxy hosts.
         *
         * <p>For HTTPS requests, the client establishes a CONNECT tunnel through the proxy, then performs TLS
         * handshake through the tunnel.
         *
         * <p>For HTTP requests, the client connects to the proxy and sends requests with absolute URIs.
         *
         * @param proxy the proxy configuration, or null for direct connections
         * @return this builder
         * @see ProxyConfiguration
         */
        public Builder proxy(ProxyConfiguration proxy) {
            return proxySelector(proxy != null ? ProxySelector.of(proxy) : ProxySelector.direct());
        }

        /**
         * Set a custom proxy selector for dynamic proxy selection.
         *
         * <p>The selector is called for each request and can return multiple proxies to try in order.
         * If a proxy fails, the next one is attempted.
         *
         * @param selector the proxy selector to use
         * @return this builder
         */
        public Builder proxySelector(ProxySelector selector) {
            this.proxySelector = Objects.requireNonNull(selector, "proxySelector");
            return this;
        }

        /**
         * Build the HTTP client.
         *
         * @return a new HTTP client instance
         */
        public HttpClient build() {
            if (connectionPool == null) {
                connectionPool = HttpConnectionPool.builder().build();
            }
            return new DefaultHttpClient(this);
        }
    }
}
