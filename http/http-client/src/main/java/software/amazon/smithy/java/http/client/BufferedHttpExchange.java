/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;

/**
 * HttpExchange implementation backed by a buffered response.
 *
 * <p>Used when an interceptor short-circuits the request via {@code handleRequest()}
 * or {@code onError()}, returning a pre-existing response (e.g., from cache).
 *
 * <p>The request body is a no-op since the request was never actually sent.
 */
final class BufferedHttpExchange implements HttpExchange {
    private final HttpRequest request;
    private final HttpResponse response;
    private final OutputStream noopRequestBody = OutputStream.nullOutputStream();

    BufferedHttpExchange(HttpRequest request, HttpResponse response) {
        this.request = request;
        this.response = response;
    }

    @Override
    public HttpRequest request() {
        return request;
    }

    @Override
    public OutputStream requestBody() {
        // No-op - request was never sent (short-circuited)
        return noopRequestBody;
    }

    @Override
    public InputStream responseBody() {
        return response.body().asInputStream();
    }

    @Override
    public HttpHeaders responseHeaders() {
        return response.headers();
    }

    @Override
    public int responseStatusCode() {
        return response.statusCode();
    }

    @Override
    public HttpVersion responseVersion() throws IOException {
        return response.httpVersion();
    }

    @Override
    public void close() throws IOException {
        // Nothing to close - no real connection
        // Response body will be closed when user closes it
    }

    @Override
    public boolean supportsBidirectionalStreaming() {
        // Buffered response - no real connection, no streaming
        return false;
    }
}
