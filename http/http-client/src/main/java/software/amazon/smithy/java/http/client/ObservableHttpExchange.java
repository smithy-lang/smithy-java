/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Exchange wrapper that calls afterResponse() interceptors when response is first accessed.
 *
 * <p>This ensures the afterResponse() hook is called even for streaming exchanges,
 * while keeping it as an observation-only hook (doesn't modify the exchange).
 */
final class ObservableHttpExchange implements HttpExchange {
    private final HttpExchange delegate;
    private final HttpRequest request;
    private final List<HttpInterceptor> interceptors;

    private boolean afterResponseCalled = false;

    ObservableHttpExchange(
            HttpExchange delegate,
            HttpRequest request,
            List<HttpInterceptor> interceptors
    ) {
        this.delegate = delegate;
        this.request = request;
        this.interceptors = interceptors;
    }

    @Override
    public OutputStream requestBody() {
        // Don't call afterResponse yet - user is still writing request
        return delegate.requestBody();
    }

    @Override
    public InputStream responseBody() throws IOException {
        ensureAfterResponseCalled();
        return delegate.responseBody();
    }

    @Override
    public HttpHeaders responseHeaders() throws IOException {
        ensureAfterResponseCalled();
        return delegate.responseHeaders();
    }

    @Override
    public int statusCode() throws IOException {
        ensureAfterResponseCalled();
        return delegate.statusCode();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public boolean supportsBidirectionalStreaming() {
        return delegate.supportsBidirectionalStreaming();
    }

    /**
     * Call afterResponse() interceptors once, when response is first accessed.
     */
    private void ensureAfterResponseCalled() {
        if (afterResponseCalled) {
            return;
        }

        afterResponseCalled = true;

        try {
            // Build HttpResponse object for observation
            HttpResponse response = HttpResponse.builder()
                    .statusCode(delegate.statusCode())
                    .headers(delegate.responseHeaders())
                    .body(DataStream.ofInputStream(delegate.responseBody()))
                    .build();

            // Call interceptors in reverse order
            for (int i = interceptors.size() - 1; i >= 0; i--) {
                interceptors.get(i).afterResponse(request, response);
            }

        } catch (IOException e) {
            // Observation failure shouldn't break the exchange
            // Log but don't throw
            // TODO: Better error handling (maybe a callback?)
        }
    }
}
