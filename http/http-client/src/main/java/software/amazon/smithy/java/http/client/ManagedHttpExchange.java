/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.ConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * HttpExchange wrapper that manages connection pooling and interceptor hooks.
 *
 * <h2>Connection Management</h2>
 *
 * <p>The wrapper tracks errors that occur during the exchange:
 * <ul>
 *   <li>On successful close: connection is released back to pool for reuse</li>
 *   <li>On error during exchange: connection is evicted (not reused)</li>
 *   <li>On error during close: connection is evicted</li>
 * </ul>
 *
 * <h2>Interceptor Behavior</h2>
 *
 * <p>The interceptResponse() hook is called lazily when the response is first
 * accessed (via statusCode(), responseHeaders(), or responseBody()). This ensures
 * interceptors see the response even for streaming exchanges.
 *
 * <p><b>Important:</b> If interceptors read the response body from the provided
 * HttpResponse, they MUST provide a replacement response with a new body.
 * Otherwise, the body stream will be consumed and unavailable to the caller.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class is NOT thread-safe. It is designed for single-threaded use
 * consistent with HTTP/1.1 sequential request/response patterns.
 */
final class ManagedHttpExchange implements HttpExchange {

    // Connection management
    private final HttpExchange delegate;
    private final HttpConnection connection;
    private final ConnectionPool pool;

    // Interceptor support
    private final HttpRequest request;
    private final Context context;
    private final List<HttpInterceptor> interceptors;
    private final HttpClient client;

    // State
    private boolean closed;
    private boolean connectionHandled; // true after pool.release() or pool.evict() called
    private boolean errored;
    private boolean intercepted;
    private HttpResponse interceptedResponse;

    ManagedHttpExchange(
            HttpExchange delegate,
            HttpConnection connection,
            ConnectionPool pool,
            HttpRequest request,
            Context context,
            List<HttpInterceptor> interceptors,
            HttpClient client
    ) {
        this.delegate = delegate;
        this.connection = connection;
        this.pool = pool;
        this.request = request;
        this.context = context;
        this.interceptors = interceptors;
        this.client = client;
    }

    @Override
    public OutputStream requestBody() {
        return delegate.requestBody();
    }

    @Override
    public InputStream responseBody() throws IOException {
        try {
            ensureIntercepted();
            return interceptedResponse != null ? interceptedResponse.body().asInputStream() : delegate.responseBody();
        } catch (IOException e) {
            errored = true;
            throw e;
        }
    }

    @Override
    public HttpHeaders responseHeaders() throws IOException {
        try {
            ensureIntercepted();
            return interceptedResponse != null ? interceptedResponse.headers() : delegate.responseHeaders();
        } catch (IOException e) {
            errored = true;
            throw e;
        }
    }

    @Override
    public int statusCode() throws IOException {
        try {
            ensureIntercepted();
            return interceptedResponse != null ? interceptedResponse.statusCode() : delegate.statusCode();
        } catch (IOException e) {
            errored = true;
            throw e;
        }
    }

    @Override
    public HttpVersion responseVersion() throws IOException {
        try {
            ensureIntercepted();
            return interceptedResponse != null ? interceptedResponse.httpVersion() : delegate.responseVersion();
        } catch (IOException e) {
            errored = true;
            throw e;
        }
    }

    @Override
    public boolean supportsBidirectionalStreaming() {
        return delegate.supportsBidirectionalStreaming();
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        // Drain intercepted response body if present (best effort)
        if (interceptedResponse != null && interceptedResponse.body() != null) {
            try (InputStream body = interceptedResponse.body().asInputStream()) {
                body.transferTo(OutputStream.nullOutputStream());
            } catch (IOException ignored) {
                // Best effort drain - continue with close
                errored = true;
            }
        }

        try {
            delegate.close();
        } catch (IOException e) {
            errored = true;
            throw e;
        } finally {
            // Ensure connection is returned to pool exactly once
            if (!connectionHandled) {
                connectionHandled = true;
                if (errored) {
                    pool.evict(connection, true);
                } else {
                    pool.release(connection);
                }
            }
        }
    }

    /**
     * Call interceptResponse() once, when response is first accessed.
     *
     * <p>This method eagerly reads status code, headers, and obtains the body stream
     * from the delegate to build an HttpResponse for interceptors. If interceptors
     * replace the response, subsequent calls use the replacement.
     *
     * <p>The intercepted flag is set before calling delegate methods. If delegate
     * methods throw, subsequent calls will skip interception and call delegate
     * directly, allowing partial recovery.
     */
    private void ensureIntercepted() throws IOException {
        if (intercepted) {
            return;
        }
        intercepted = true;

        if (interceptors.isEmpty()) {
            return;
        }

        HttpResponse currentResponse = HttpResponse.builder()
                .statusCode(delegate.statusCode())
                .headers(delegate.responseHeaders())
                .body(DataStream.ofInputStream(delegate.responseBody()))
                .build();

        HttpResponse replacement = client.applyInterceptResponse(
                interceptors,
                request,
                context,
                currentResponse);

        if (replacement != null) {
            interceptedResponse = replacement;
        }
    }
}
