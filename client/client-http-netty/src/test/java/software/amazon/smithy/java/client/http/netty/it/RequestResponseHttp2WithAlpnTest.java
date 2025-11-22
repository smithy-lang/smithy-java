/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static software.amazon.smithy.java.client.http.netty.it.TestUtils.createServerSslContextBuilder;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport;
import software.amazon.smithy.java.client.http.netty.it.server.MultiplexingHttp2ClientHandler;
import software.amazon.smithy.java.client.http.netty.it.server.NettyTestServer;
import software.amazon.smithy.java.client.http.netty.it.server.RequestCapturingHttp2ClientHandler;
import software.amazon.smithy.java.client.http.netty.it.server.TestCertificateGenerator;
import software.amazon.smithy.java.client.http.netty.it.server.TextResponseHttp2ClientHandler;
import software.amazon.smithy.java.http.api.HttpVersion;

public class RequestResponseHttp2WithAlpnTest {
    private static final String RESPONSE_CONTENTS = "Response sent from Http2WithTls";
    private static final String REQUEST_CONTENTS = "Request sent from Http2WithTls";
    private static TestCertificateGenerator.CertificateBundle bundle;
    private NettyTestServer server;
    private RequestCapturingHttp2ClientHandler requestCapturingHandler;

    @BeforeAll
    static void beforeAll() throws Exception {
        bundle = TestCertificateGenerator.generateCertificates();
    }

    @BeforeEach
    void setUp() throws Exception {
        requestCapturingHandler = new RequestCapturingHttp2ClientHandler();
        var multiplexer = new MultiplexingHttp2ClientHandler(requestCapturingHandler,
                new TextResponseHttp2ClientHandler(RESPONSE_CONTENTS));
        server = NettyTestServer.builder()
                .httpVersion(HttpVersion.HTTP_2)
                .h2ConnectionMode(NettyHttpClientTransport.H2ConnectionMode.ALPN)
                .http2HandlerFactory((ctx) -> multiplexer)
                .sslContextBuilder(createServerSslContextBuilder(bundle))
                .build();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    public void canSendRequestAndReadResponse() {
        // -- Arrange
        var client = NettyHttpClientTransport.builder()
                .httpVersion(HttpVersion.HTTP_2)
                .configureH2Settings(c -> c.connectionMode(NettyHttpClientTransport.H2ConnectionMode.ALPN))
                .sslContextModifier(b -> b.trustManager(bundle.caCertificate))
                .build();
        var request = TestUtils.plainTextHttp2Request("https://localhost:" + server.getPort(), REQUEST_CONTENTS);

        // -- Act
        var response = client.send(null, request);
        var bodyByteBuf = response.body().asByteBuffer();
        var bytes = new byte[bodyByteBuf.remaining()];
        bodyByteBuf.get(bytes);
        var responseBody = new String(bytes, StandardCharsets.UTF_8);
        client.close();

        // -- Assert
        var capturedRequestBody = requestCapturingHandler.capturedBody().toString(StandardCharsets.UTF_8);
        assertEquals(REQUEST_CONTENTS, capturedRequestBody);
        assertEquals(RESPONSE_CONTENTS, responseBody);
    }
}
