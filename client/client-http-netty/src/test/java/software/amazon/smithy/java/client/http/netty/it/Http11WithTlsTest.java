/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport;
import software.amazon.smithy.java.client.http.netty.it.server.MultiplexingHttp11ClientHandler;
import software.amazon.smithy.java.client.http.netty.it.server.NettyTestServer;
import software.amazon.smithy.java.client.http.netty.it.server.RequestCapturingHttp11ClientHandler;
import software.amazon.smithy.java.client.http.netty.it.server.TestCertificateGenerator;
import software.amazon.smithy.java.client.http.netty.it.server.TextResponseHttp11ClientHandler;
import software.amazon.smithy.java.http.api.HttpVersion;

public class Http11WithTlsTest {
    private static final String RESPONSE_CONTENTS = "Response sent from Http2WithTls";
    private static final String REQUEST_CONTENTS = "Request sent from Http2WithTls";
    private static TestCertificateGenerator.CertificateBundle bundle;
    private NettyTestServer server;
    private RequestCapturingHttp11ClientHandler requestCapturingHandler;

    @BeforeEach
    void setUp() throws Exception {
        requestCapturingHandler = new RequestCapturingHttp11ClientHandler();
        var multiplexer = new MultiplexingHttp11ClientHandler(requestCapturingHandler,
                new TextResponseHttp11ClientHandler(RESPONSE_CONTENTS));
        server = NettyTestServer.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory((ctx) -> multiplexer)
                .sslContext(createServerSslContext())
                .build();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @BeforeAll
    static void beforeAll() throws Exception {
        bundle = TestCertificateGenerator.generateCertificates();
    }

    @Test
    public void canSendRequestAndReadResponse() throws Exception {
        // -- Arrange
        var client = NettyHttpClientTransport.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
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

    public static SslContext createServerSslContext() throws Exception {
        return SslContextBuilder
                .forServer(bundle.serverPrivateKey, bundle.serverCertificate)
                .build();
    }
}
