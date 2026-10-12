/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;

/**
 * Returns the canned response in process and consumes request bodies.
 * Serves one request at a time on one thread, like the benchmark loop.
 */
final class MockHttpTransport implements ClientTransport<HttpRequest, HttpResponse>, Target {

    static final String DESCRIPTION = "In-process ClientTransport returning one pre-built canned HTTP response per "
            + "benchmark. No sockets and no localhost server. The request body is fully consumed as a real transport "
            + "would; each call gets a new zero-copy view over the same response bytes.";

    private CannedResponse response;
    private boolean checkNextRequest;
    private HttpRequest lastRequest;
    private long lastRequestBodyBytes;

    @Override
    public ClientTransport<HttpRequest, HttpResponse> transport() {
        return this;
    }

    @Override
    public String endpoint() {
        return BenchmarkProtocol.ENDPOINT;
    }

    @Override
    public void respondWith(CannedResponse response) {
        this.response = response;
        this.checkNextRequest = true;
    }

    @Override
    public String description() {
        return DESCRIPTION;
    }

    @Override
    public void close() {}

    HttpRequest lastRequest() {
        return lastRequest;
    }

    long lastRequestBodyBytes() {
        return lastRequestBodyBytes;
    }

    @Override
    public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
        return HttpMessageExchange.INSTANCE;
    }

    @Override
    public HttpResponse send(Context context, HttpRequest request) {
        lastRequest = request;
        lastRequestBodyBytes = Bodies.drain(request.body());
        if (checkNextRequest) {
            // Validate request framing on the first call, before warmup.
            checkNextRequest = false;
            Long declared = request.headers().contentLength();
            if (declared != null && declared != lastRequestBodyBytes) {
                throw new IllegalStateException(
                        "The request declares Content-Length " + declared + " but its body held "
                                + lastRequestBodyBytes + " bytes; check how the case's payload parameter is decoded.");
            }
        }
        return response.newHttpResponse();
    }
}
