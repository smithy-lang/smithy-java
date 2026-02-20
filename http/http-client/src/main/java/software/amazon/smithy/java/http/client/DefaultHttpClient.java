/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.connection.ConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Default {@link HttpClient} implementation.
 */
final class DefaultHttpClient implements HttpClient {

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
        // exchange() handles beforeRequest, preemptRequest, onError, and creates ManagedHttpExchange
        // which applies interceptResponse lazily when response is accessed.
        HttpExchange exchange = newExchange(request, options);

        // Write request body using the effective request
        HttpRequest effectiveRequest = exchange.request();
        try {
            DataStream requestBody = effectiveRequest.body();
            if (requestBody != null && requestBody.contentLength() != 0) {
                try (OutputStream out = exchange.requestBody()) {
                    requestBody.writeTo(out);
                }
            } else {
                exchange.requestBody().close();
            }
        } catch (IOException e) {
            exchange.close();
            throw e;
        }

        // Build streaming response. The response body stream auto-closes the exchange when closed.
        return HttpResponse.builder()
                .statusCode(exchange.responseStatusCode())
                .headers(exchange.responseHeaders())
                .body(DataStream.ofInputStream(exchange.responseBody()))
                .build();
    }

    @Override
    public HttpExchange newExchange(HttpRequest request, RequestOptions options) throws IOException {
        var resolvedInterceptors = options.resolveInterceptors(interceptors);

        // Allow interceptors to modify the request preflight (add headers, query string, change body, etc).
        HttpRequest modifiedRequest = applyBeforeRequest(resolvedInterceptors, request, options.context());

        // Allow interceptors to completely intercept the request and provide a specific response.
        HttpResponse preempted = applyPreemptRequest(resolvedInterceptors, modifiedRequest, options.context());
        if (preempted != null) {
            try {
                HttpResponse intercepted = applyInterceptResponse(
                        this,
                        resolvedInterceptors,
                        modifiedRequest,
                        options.context(),
                        preempted);
                if (intercepted != null) {
                    preempted = intercepted;
                }
                return HttpExchange.newBufferedExchange(modifiedRequest, preempted);
            } catch (IOException e) {
                // IOE during preemption can be recovered from using onError.
                HttpResponse recovery = applyOnError(this, resolvedInterceptors, modifiedRequest, options.context(), e);
                if (recovery != null) {
                    return HttpExchange.newBufferedExchange(modifiedRequest, recovery);
                }
                throw e;
            }
        }

        try {
            return createManagedExchange(modifiedRequest, options.context(), resolvedInterceptors);
        } catch (IOException e) {
            HttpResponse recovery = applyOnError(this, resolvedInterceptors, modifiedRequest, options.context(), e);
            if (recovery != null) {
                return HttpExchange.newBufferedExchange(modifiedRequest, recovery);
            }
            throw e;
        }
    }

    private HttpExchange createManagedExchange(
            HttpRequest request,
            Context context,
            List<HttpInterceptor> resolvedInterceptors
    ) throws IOException {
        URI target = request.uri();
        List<ProxyConfiguration> proxies = proxySelector.select(target, context);

        if (proxies.isEmpty()) {
            return createManagedExchangeForRoute(request, context, resolvedInterceptors, Route.from(target, null));
        }

        IOException last = null;
        for (ProxyConfiguration proxy : proxies) {
            Route route = Route.from(target, proxy);
            try {
                return createManagedExchangeForRoute(request, context, resolvedInterceptors, route);
            } catch (IOException e) {
                last = e;
                proxySelector.connectFailed(target, context, proxy, e);
            }
        }

        throw last;
    }

    private HttpExchange createManagedExchangeForRoute(
            HttpRequest request,
            Context context,
            List<HttpInterceptor> resolvedInterceptors,
            Route route
    ) throws IOException {
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
        } catch (Exception e) {
            connectionPool.evict(conn, true);
            if (e instanceof IOException ioe) {
                throw ioe;
            }
            throw new IOException("Failed to create exchange", e);
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

    static HttpResponse applyInterceptResponse(
            HttpClient client,
            List<HttpInterceptor> resolved,
            HttpRequest request,
            Context context,
            HttpResponse response
    ) throws IOException {
        HttpResponse current = response;
        // iterate backward
        for (int i = resolved.size() - 1; i >= 0; i--) {
            HttpResponse replacement = resolved.get(i).interceptResponse(client, request, context, current);
            if (replacement != null) {
                current = replacement;
            }
        }
        return current == response ? null : current;
    }

    static HttpResponse applyOnError(
            HttpClient client,
            List<HttpInterceptor> resolved,
            HttpRequest request,
            Context context,
            IOException exception
    ) throws IOException {
        // iterate backward
        for (int i = resolved.size() - 1; i >= 0; i--) {
            HttpResponse recovery = resolved.get(i).onError(client, request, context, exception);
            if (recovery != null) {
                return recovery;
            }
        }
        return null;
    }

    private HttpResponse sendWithTimeout(HttpRequest request, RequestOptions options, Duration timeout)
            throws IOException {
        // Run the blocking operation in its own virtual thread
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
            // The calling thread was interrupted while waiting
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
