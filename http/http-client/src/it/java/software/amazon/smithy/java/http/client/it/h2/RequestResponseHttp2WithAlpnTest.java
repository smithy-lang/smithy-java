/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.TestUtils;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.TestCertificateGenerator;
import software.amazon.smithy.java.http.client.it.server.h2.MultiplexingHttp2ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h2.RequestCapturingHttp2ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h2.TextResponseHttp2ClientHandler;

/**
 * Tests basic HTTP/2 request/response over TLS with ALPN negotiation.
 */
public class RequestResponseHttp2WithAlpnTest extends BaseHttpClientIntegTest {

    private static TestCertificateGenerator.CertificateBundle bundle;
    private RequestCapturingHttp2ClientHandler requestCapturingHandler;

    @BeforeAll
    static void beforeAll() throws Exception {
        bundle = TestCertificateGenerator.generateCertificates();
    }

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        requestCapturingHandler = new RequestCapturingHttp2ClientHandler();
        try {
            return builder
                    .httpVersion(HttpVersion.HTTP_2)
                    .h2ConnectionMode(NettyTestServer.H2ConnectionMode.ALPN)
                    .http2HandlerFactory(ctx -> new MultiplexingHttp2ClientHandler(
                            requestCapturingHandler,
                            new TextResponseHttp2ClientHandler(RESPONSE_CONTENTS)))
                    .sslContextBuilder(TestUtils.createServerSslContextBuilder(bundle));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        try {
            return builder
                    .httpVersionPolicy(HttpVersionPolicy.AUTOMATIC)
                    .sslContext(TestUtils.createClientSslContext(bundle));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String uri() {
        return "https://localhost:" + server.getPort();
    }

    @Test
    void canSendRequestAndReadResponse() throws Exception {
        var request = plainTextRequest(HttpVersion.HTTP_2, REQUEST_CONTENTS);

        var response = client.send(request);
        var responseBody = readBody(response);

        // Wait for server to receive all request data
        requestCapturingHandler.streamCompleted().join();

        assertEquals(REQUEST_CONTENTS, requestCapturingHandler.capturedBody().toString(StandardCharsets.UTF_8));
        assertEquals(RESPONSE_CONTENTS, responseBody);
    }
}
