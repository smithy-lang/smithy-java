/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.MessageExchange;
import software.amazon.smithy.java.client.http.HttpMessageExchange;
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;

/**
 * The real HTTP transport for the Feb-2026 baseline: {@code java.net.http.HttpClient} wrapped by
 * {@link JavaHttpClientTransport}, instrumented. The smithy-java native HTTP client and the BoringSSL engine do not
 * exist in this SDK yet, so the JDK client (which did) is the one real transport the baseline can drive. Requests
 * are HTTP/1.1, which is what the generated clients emit and what the fixture server speaks. Unlike the native
 * client's BoringSSL provider, the JDK TLS stack honours a trust store, so {@code --trust-cert} verifies the
 * fixture server's self-signed certificate instead of disabling verification.
 */
final class NetworkTransport implements CountingTransport {

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
        var builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5));
        String tls = "plaintext";
        if (mode == TransportMode.HTTPS) {
            builder.sslContext(trustAll());
            tls = "TLS (local fixture trusted unconditionally)";
        }
        return new NetworkTransport(
                new JavaHttpClientTransport(builder.build()),
                "java.net.http.HttpClient (JavaHttpClientTransport), HTTP/1.1, " + tls);
    }

    /** How server certificates are checked on the given transport, as recorded in results metadata. */
    static String tlsVerification(TransportMode mode) {
        return mode == TransportMode.HTTPS ? "disabled (trust-all: local self-signed fixture)" : "n/a";
    }

    /**
     * An SSL context that trusts any server certificate. The fixture server is a local throwaway with a
     * self-signed certificate, so there is nothing to verify and nothing to configure; this mirrors the current
     * harness's BoringSSL client, which also trusts the fixture unconditionally. Benchmark-only, never production.
     */
    static SSLContext trustAll() {
        try {
            var context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to build a trust-all SSL context", e);
        }
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
}
