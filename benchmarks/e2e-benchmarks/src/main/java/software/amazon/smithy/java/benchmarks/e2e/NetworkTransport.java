/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.io.IOException;
import java.time.Duration;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.client.http.boringssl.BoringSslTlsProvider;
import software.amazon.smithy.java.client.http.smithy.SmithyHttpClientTransport;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;

/**
 * smithy-java's own HTTP client as the benchmark transport, instrumented. Counts requests and request body bytes,
 * and records the status, declared length and HTTP version of the last response so the harness can check that the
 * fixture server is serving the expected fixture. The request body is produced and consumed by the real transport
 * exactly as in production.
 *
 * <p>The client is built the way a customer would build it, with two exceptions that the results record. Requests
 * are pinned to HTTP/1.1, which is what the generated clients emit by default and what the fixture servers speak.
 * For {@code https} the TLS engine is BoringSSL, the engine smithy-java recommends for throughput; its provider has
 * no trust-store hook, and the fixture servers use a self-signed certificate, so server certificates are not
 * verified. Verification happens once per connection, outside the per-request cost this transport exists to show.
 */
final class NetworkTransport implements CountingTransport {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final String CLIENT = "smithy-java HttpClient (SmithyHttpClientTransport), HTTP/1.1, ";

    private final ClientTransport<HttpRequest, HttpResponse> delegate;
    private final String description;
    private HttpRequest lastRequest;
    private long requests;
    private long requestBodyBytes;
    private int lastStatus = -1;
    private long lastResponseLength = -1;
    private String lastHttpVersion = "unknown";

    private NetworkTransport(ClientTransport<HttpRequest, HttpResponse> delegate, String description) {
        this.delegate = delegate;
        this.description = description;
    }

    static NetworkTransport create(TransportMode mode) {
        if (!mode.isNetwork()) {
            throw new IllegalArgumentException(mode + " is not a network transport");
        }
        var builder = HttpClient.builder()
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .connectTimeout(CONNECT_TIMEOUT);
        String tls = "plaintext";
        if (mode == TransportMode.HTTPS) {
            if (!BoringSslTlsProvider.available()) {
                throw new IllegalStateException("BoringSSL (netty-tcnative) is not available on this host");
            }
            builder.tlsProvider(BoringSslTlsProvider.create(true));
            tls = "BoringSSL TLS engine, " + tlsVerification(mode);
        }
        return new NetworkTransport(new SmithyHttpClientTransport(builder.build()), CLIENT + tls);
    }

    /** How server certificates are checked on the given transport, as recorded in results metadata. */
    static String tlsVerification(TransportMode mode) {
        return mode == TransportMode.HTTPS
                ? "disabled (trust-all: the BoringSSL provider has no trust-store hook and the fixture certificate "
                        + "is self-signed)"
                : "n/a";
    }

    @Override
    public void prepare(BenchmarkCase benchmarkCase) {
        // Nothing to install: the fixture server decides the response. validateLast() checks it afterwards.
    }

    @Override
    public void resetCounters() {
        requests = 0;
        requestBodyBytes = 0;
    }

    @Override
    public long requests() {
        return requests;
    }

    @Override
    public long requestBodyBytes() {
        return requestBodyBytes;
    }

    @Override
    public HttpRequest lastRequest() {
        return lastRequest;
    }

    @Override
    public int lastResponseStatus() {
        return lastStatus;
    }

    @Override
    public long lastResponseLength() {
        return lastResponseLength;
    }

    @Override
    public String lastHttpVersion() {
        return lastHttpVersion;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public MessageExchange<HttpRequest, HttpResponse> messageExchange() {
        return HttpMessageExchange.INSTANCE;
    }

    @Override
    public HttpResponse send(Context context, HttpRequest request) {
        requests++;
        lastRequest = request;
        var body = request.body();
        if (body != null && body.contentLength() > 0) {
            requestBodyBytes += body.contentLength();
        }
        HttpResponse response = delegate.send(context, request);
        lastStatus = response.statusCode();
        lastResponseLength = response.body() == null ? -1 : response.body().contentLength();
        lastHttpVersion = response.httpVersion().toString();
        return response;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
