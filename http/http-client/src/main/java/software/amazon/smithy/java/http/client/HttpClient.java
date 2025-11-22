/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Main HTTP client. Thread-safe.
 */
public final class HttpClient implements AutoCloseable {

    private final ConnectionPool connectionPool;
    private final List<HttpInterceptor> interceptors;
    private final boolean followRedirects;
    private final int maxRedirects;
    private final Duration requestTimeout;

    private HttpClient(Builder builder) {
        this.connectionPool = builder.connectionPool;
        this.interceptors = List.copyOf(builder.interceptors);
        this.followRedirects = builder.followRedirects;
        this.maxRedirects = builder.maxRedirects;
        this.requestTimeout = builder.requestTimeout;
    }

    /**
     * Send a simple request/response exchange.
     * Applies all interceptors and follows redirects if enabled.
     *
     * <p>This method uses {@link #exchange(HttpRequest)} internally,
     * writes the request body, reads and buffers the response, then closes the exchange.
     */
    public HttpResponse send(HttpRequest request) throws IOException {
        if (requestTimeout != null) {
            return withTimeout(() -> sendInternal(request), request);
        }
        return sendInternal(request);
    }

    private HttpResponse sendInternal(HttpRequest request) throws IOException {
        try (var exchange = exchange(request)) {
            // Write request body if present
            if (request.body() != null) {
                try (OutputStream out = exchange.requestBody()) {
                    request.body().asInputStream().transferTo(out);
                }
            } else {
                exchange.requestBody().close();
            }

            // Read and buffer response
            int statusCode = exchange.statusCode();
            HttpHeaders headers = exchange.responseHeaders();

            // Buffer the entire response body in memory
            byte[] bodyBytes;
            try (InputStream in = exchange.responseBody()) {
                bodyBytes = in.readAllBytes();
            }

            // Return buffered response
            return HttpResponse.builder()
                    .statusCode(statusCode)
                    .headers(headers)
                    .body(DataStream.ofInputStream(new ByteArrayInputStream(bodyBytes)))
                    .build();
        }
    }

    /**
     * Create a bidirectional streaming exchange.
     */
    public HttpExchange exchange(HttpRequest request) throws IOException {
        // 1. Apply beforeRequest
        HttpRequest modifiedRequest = applyBeforeRequest(request);

        // 2. Check handleRequest - can short-circuit
        HttpResponse handled = applyHandleRequest(modifiedRequest);
        if (handled != null) {
            applyAfterResponse(modifiedRequest, handled);
            return new BufferedHttpExchange(handled);
        }

        // 3. Create initial exchange
        HttpConnection conn = connectionPool.acquire(modifiedRequest.uri());
        try {
            HttpExchange baseExchange = conn.newExchange(modifiedRequest);

            // Wrap with redirect support if enabled
            HttpExchange exchange;

            if (followRedirects) {
                exchange = new RedirectingHttpExchange(
                        baseExchange,
                        conn,
                        modifiedRequest,
                        maxRedirects,
                        connectionPool);
            } else {
                exchange = new PooledHttpExchange(
                        baseExchange,
                        conn,
                        connectionPool,
                        modifiedRequest.uri());
            }

            // Wrap with observable (for afterResponse hook)
            return new ObservableHttpExchange(
                    exchange,
                    modifiedRequest,
                    interceptors);

        } catch (IOException e) {
            // 4. Apply onError
            HttpResponse recovery = applyOnError(modifiedRequest, e);
            if (recovery != null) {
                applyAfterResponse(modifiedRequest, recovery);
                return new BufferedHttpExchange(recovery);
            }

            connectionPool.evict(conn, modifiedRequest.uri());
            throw e;
        }
    }

    /**
     * Apply beforeRequest interceptors.
     */
    private HttpRequest applyBeforeRequest(HttpRequest request) {
        HttpRequest modified = request;
        for (HttpInterceptor interceptor : interceptors) {
            modified = interceptor.beforeRequest(modified, this);
        }
        return modified;
    }

    /**
     * Apply handleRequest interceptors.
     */
    private HttpResponse applyHandleRequest(HttpRequest request) throws IOException {
        for (HttpInterceptor interceptor : interceptors) {
            HttpResponse response = interceptor.handleRequest(request, this);
            if (response != null) {
                return response;
            }
        }
        return null;
    }

    /**
     * Apply afterResponse interceptors (observation only).
     */
    private void applyAfterResponse(HttpRequest request, HttpResponse response) throws IOException {
        // Apply in reverse order
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            interceptors.get(i).afterResponse(request, response);
        }
    }

    /**
     * Apply onError interceptors.
     */
    private HttpResponse applyOnError(HttpRequest request, IOException exception) throws IOException {
        // Apply in reverse order
        for (int i = interceptors.size() - 1; i >= 0; i--) {
            HttpResponse recovery = interceptors.get(i).onError(request, exception, this);
            if (recovery != null) {
                return recovery;
            }
        }
        return null;
    }

    /**
     * Execute an operation with a timeout using virtual threads.
     * Both the worker and timeout threads are virtual threads.
     */
    private <T> T withTimeout(IOSupplier<T> operation, HttpRequest request) throws IOException {
        var resultHolder = new Object() {
            volatile T result;
            volatile IOException exception;
            volatile boolean completed = false;
        };

        // Worker thread (virtual) - executes the HTTP request
        Thread worker = Thread.ofVirtual().start(() -> {
            try {
                resultHolder.result = operation.get();
            } catch (IOException e) {
                resultHolder.exception = e;
            } finally {
                resultHolder.completed = true;
            }
        });

        // Timeout thread (virtual) to interrupt worker if timeout exceeded
        Thread timeoutThread = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(requestTimeout.toMillis());
                if (!resultHolder.completed) {
                    worker.interrupt(); // Timeout occurred
                }
            } catch (InterruptedException ignored) {
                // Worker completed before timeout. This is expected.
            }
        });

        try {
            // Wait for worker to complete and cancel timeout thread if request finished
            worker.join();
            timeoutThread.interrupt();
            if (resultHolder.exception != null) {
                throw resultHolder.exception;
            } else if (!resultHolder.completed) {
                throw new IOException("Request to " + request.uri() + " exceeded timeout of "
                        + requestTimeout.toSeconds() + " seconds");
            } else {
                return resultHolder.result;
            }
        } catch (InterruptedException e) {
            worker.interrupt();
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    @FunctionalInterface
    private interface IOSupplier<T> {
        T get() throws IOException;
    }

    @Override
    public void close() throws IOException {
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
        private int maxConnectionsPerHost = 5;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private Duration requestTimeout; // null = no timeout
        private SSLContext sslContext;
        private final List<HttpInterceptor> interceptors = new ArrayList<>();
        private boolean followRedirects = true;
        private int maxRedirects = 10;

        public Builder addInterceptor(HttpInterceptor interceptor) {
            interceptors.add(interceptor);
            return this;
        }

        public Builder connectionPool(ConnectionPool pool) {
            this.connectionPool = pool;
            return this;
        }

        public Builder maxConnectionsPerHost(int max) {
            this.maxConnectionsPerHost = max;
            return this;
        }

        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        public Builder requestTimeout(Duration timeout) {
            this.requestTimeout = timeout;
            return this;
        }

        public Builder sslContext(SSLContext context) {
            this.sslContext = context;
            return this;
        }

        public Builder followRedirects(boolean follow) {
            this.followRedirects = follow;
            return this;
        }

        public Builder maxRedirects(int max) {
            this.maxRedirects = max;
            return this;
        }

        public HttpClient build() {
            if (connectionPool == null) {
                connectionPool = HostBasedConnectionPool.builder()
                        .maxConnectionsPerHost(maxConnectionsPerHost)
                        .connectTimeout(connectTimeout)
                        .readTimeout(readTimeout)
                        .sslContext(sslContext)
                        .build();
            }

            return new HttpClient(this);
        }
    }
}
