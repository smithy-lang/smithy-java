/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static software.amazon.smithy.java.http.client.it.TestUtils.createServerSslContextBuilder;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.it.server.MultiplexingHttp2ClientHandler;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.RequestCapturingHttp2ClientHandler;
import software.amazon.smithy.java.http.client.it.server.TestCertificateGenerator;
import software.amazon.smithy.java.http.client.it.server.TextResponseHttp2ClientHandler;

public class RequestResponseHttp2WithTlsTest {
    private static final String RESPONSE_CONTENTS = "Response sent from Http2WithTls";
    private static final String REQUEST_CONTENTS = "Request sent from Http2WithTls";
    private static TestCertificateGenerator.CertificateBundle bundle;
    private NettyTestServer server;
    private RequestCapturingHttp2ClientHandler requestCapturingHandler;
    private HttpClient client;

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
                .h2ConnectionMode(NettyTestServer.H2ConnectionMode.PRIOR_KNOWLEDGE)
                .http2HandlerFactory((ctx) -> multiplexer)
                .sslContextBuilder(createServerSslContextBuilder(bundle))
                .build();
        server.start();

        DnsResolver staticDns = DnsResolver.staticMapping(Map.of(
                "localhost",
                List.of(InetAddress.getLoopbackAddress())));
        client = HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .maxConnectionsPerRoute(10)
                        .maxTotalConnections(10)
                        .maxIdleTime(Duration.ofMinutes(1))
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_2)
                        .sslContext(TestUtils.createClientSslContext(bundle))
                        .dnsResolver(staticDns)
                        .build())
                .build();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    public void canSendRequestAndReadResponse() throws Exception {
        // -- Arrange
        var request = TestUtils.plainTextHttp2Request("https://localhost:" + server.getPort(), REQUEST_CONTENTS);

        // -- Act
        var response = client.send(request);
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
