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
 * <p>The interceptResponse() hook is called lazily when the response is first accessed (via statusCode(),
 * responseHeaders(), or responseBody()). This ensures interceptors see the response even for streaming exchanges.
 *
 * <p><b>Important:</b> If interceptors read the response body from the provided HttpResponse, they MUST provide a
 * replacement response with a new body. Otherwise, the body stream will be consumed and unavailable to the caller.
 *
 * <h2>Thread Safety</h2>
 *
 * <p>This class is NOT thread-safe.
 */
final class ManagedHttpExchange implements HttpExchange {

    // No need to allocate or track closed with a volatile like the built-in version does.
    private static final OutputStream VERY_NULL_OUTPUT_STREAM = new OutputStream() {
        @Override
        public void write(int b) {}
    };

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
    private InputStream responseIn; // wrapper returned to caller
    private InputStream underlyingResponseBody; // actual body stream to drain on close

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
    public HttpRequest request() {
        return request;
    }

    @Override
    public OutputStream requestBody() {
        return delegate.requestBody();
    }

    @Override
    public InputStream responseBody() throws IOException {
        if (responseIn != null) {
            return responseIn;
        }

        try {
            ensureIntercepted();
            InputStream body;
            if (interceptedResponse != null) {
                // Interceptor replaced response - use replacement body
                body = interceptedResponse.body().asInputStream();
            } else if (underlyingResponseBody != null) {
                // Interceptors ran but didn't replace - use captured original
                body = underlyingResponseBody;
            } else {
                // No interceptors - get body directly and track for draining
                body = delegate.responseBody();
                underlyingResponseBody = body;
            }
            // Wrap so closing the response body releases the connection to the pool
            responseIn = new DelegatedClosingInputStream(body, in -> close());
            return responseIn;
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
    public HttpHeaders responseTrailerHeaders() {
        return delegate.responseTrailerHeaders();
    }

    @Override
    public int responseStatusCode() throws IOException {
        try {
            ensureIntercepted();
            return interceptedResponse != null ? interceptedResponse.statusCode() : delegate.responseStatusCode();
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
    public void setRequestTrailers(HttpHeaders trailers) {
        delegate.setRequestTrailers(trailers);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        // Drain the underlying response body before releasing connection (required for HTTP/1.1 reuse).
        // We drain underlyingResponseBody directly, not responseIn, to avoid circular calls since
        // responseIn's close() callback invokes this method.
        try {
            if (underlyingResponseBody != null) {
                underlyingResponseBody.transferTo(VERY_NULL_OUTPUT_STREAM);
            }
        } catch (IOException ignored) {
            // Drain failed, so the connection cannot be reused safely
            errored = true;
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
     *
     * <p>If an interceptor throws an IOException, the error is passed to onError
     * interceptors for potential recovery.
     */
    private void ensureIntercepted() throws IOException {
        if (intercepted) {
            return;
        }
        intercepted = true;

        if (interceptors.isEmpty()) {
            return;
        }

        // Capture original body stream - needs to be drained on close for connection reuse
        underlyingResponseBody = delegate.responseBody();

        HttpResponse currentResponse = HttpResponse.builder()
                .statusCode(delegate.responseStatusCode())
                .headers(delegate.responseHeaders())
                .body(DataStream.ofInputStream(underlyingResponseBody))
                .build();

        HttpResponse replacement;
        try {
            replacement = DefaultHttpClient.applyInterceptResponse(
                    client,
                    interceptors,
                    request,
                    context,
                    currentResponse);
        } catch (IOException e) {
            HttpResponse recovery = DefaultHttpClient.applyOnError(client, interceptors, request, context, e);
            if (recovery != null) {
                interceptedResponse = recovery;
                return;
            }
            throw e;
        }

        if (replacement != null) {
            interceptedResponse = replacement;
        }
    }
}
