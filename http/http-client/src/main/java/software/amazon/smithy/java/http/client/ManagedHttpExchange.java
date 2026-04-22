/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
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

    private static final OutputStream NULL_OUTPUT_STREAM = new OutputStream() {
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
    private boolean connectionHandled;
    private boolean errored;
    private boolean intercepted;
    private HttpResponse interceptedResponse;
    private HttpVersion cachedVersion; // cached for use in close()
    private InputStream responseIn; // wrapper returned to caller
    private InputStream underlyingResponseBody; // actual body stream to drain on close
    private InputStream interceptorReplacementBody; // body from interceptor, needs closing

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
                body = interceptedResponse.body().asInputStream();
                interceptorReplacementBody = body;
            } else if (underlyingResponseBody != null) {
                body = underlyingResponseBody;
            } else {
                cacheDelegateVersionBestEffort();
                body = delegate.responseBody();
                underlyingResponseBody = body;
            }
            responseIn = new DelegatedClosingInputStream(body, in -> close());
            return responseIn;
        } catch (IOException e) {
            errored = true;
            throw e;
        }
    }

    @Override
    public ReadableByteChannel responseBodyChannel() throws IOException {
        // If stream path was already used, wrap it
        if (responseIn != null) {
            return Channels.newChannel(responseIn);
        }

        try {
            ensureIntercepted();

            if (interceptedResponse != null) {
                // Interceptor replaced response — fall back to stream-wrapped channel
                InputStream body = interceptedResponse.body().asInputStream();
                interceptorReplacementBody = body;
                responseIn = new DelegatedClosingInputStream(body, in -> close());
                return Channels.newChannel(responseIn);
            }

            // No replacement — delegate to native channel (preserves H2 zero-copy path)
            cacheDelegateVersionBestEffort();
            ReadableByteChannel channel = delegate.responseBodyChannel();

            return new ReadableByteChannel() {
                @Override
                public int read(ByteBuffer dst) throws IOException {
                    return channel.read(dst);
                }

                @Override
                public boolean isOpen() {
                    return channel.isOpen();
                }

                @Override
                public void close() throws IOException {
                    try {
                        channel.close();
                    } finally {
                        ManagedHttpExchange.this.close();
                    }
                }
            };
        } catch (IOException e) {
            errored = true;
            throw e;
        }
    }

    @Override
    public HttpHeaders responseHeaders() throws IOException {
        try {
            ensureIntercepted();
            if (interceptedResponse == null) {
                cacheDelegateVersionBestEffort();
            }
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
            if (interceptedResponse == null) {
                cacheDelegateVersionBestEffort();
            }
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
            HttpVersion v = interceptedResponse != null
                    ? interceptedResponse.httpVersion()
                    : delegate.responseVersion();
            cachedVersion = v;
            return v;
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

        // Only drain for HTTP/1.1 where the connection can't be reused until body is consumed.
        // For HTTP/2, delegate.close() sends RST_STREAM, releases buffers, and frees the stream.
        boolean shouldDrain = cachedVersion == null || cachedVersion == HttpVersion.HTTP_1_1;

        if (shouldDrain && underlyingResponseBody != null) {
            try {
                underlyingResponseBody.transferTo(NULL_OUTPUT_STREAM);
            } catch (IOException ignored) {
                errored = true;
            }
        }

        if (interceptorReplacementBody != null) {
            try {
                interceptorReplacementBody.close();
            } catch (IOException ignored) {
                // Best effort close
            }
        }

        try {
            delegate.close();
        } catch (IOException e) {
            errored = true;
            throw e;
        } finally {
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

    private void cacheDelegateVersionBestEffort() {
        if (cachedVersion != null) {
            return;
        }
        try {
            cachedVersion = delegate.responseVersion();
        } catch (IOException ignored) {
            // If version is not available, close() defaults to drain to preserve H1 reuse safety.
        }
    }

    private void ensureIntercepted() throws IOException {
        if (intercepted) {
            return;
        }
        intercepted = true;

        if (interceptors.isEmpty()) {
            return;
        }

        underlyingResponseBody = delegate.responseBody();

        cacheDelegateVersionBestEffort();

        HttpResponse currentResponse = HttpResponse.create()
                .setStatusCode(delegate.responseStatusCode())
                .setHeaders(delegate.responseHeaders())
                .setBody(DataStream.ofInputStream(underlyingResponseBody));

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
