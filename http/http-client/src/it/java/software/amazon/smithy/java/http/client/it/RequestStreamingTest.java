/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static software.amazon.smithy.java.http.client.it.TestUtils.IPSUM_LOREM;
import static software.amazon.smithy.java.http.client.it.TestUtils.streamingBody;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.TestCertificateGenerator;
import software.amazon.smithy.java.http.client.it.server.h2.MultiplexingHttp2ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h2.RequestCapturingHttp2ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h2.TextResponseHttp2ClientHandler;

/**
 * Parameterized test for streaming request body across HTTP/2 transport configurations.
 */
public class RequestStreamingTest {

    private static final String RESPONSE_CONTENTS = "Test response body";

    private static TestCertificateGenerator.CertificateBundle certBundle;
    private static SSLContext clientSslContext;

    private NettyTestServer server;
    private HttpClient client;
    private RequestCapturingHttp2ClientHandler requestHandler;

    @BeforeAll
    static void beforeAll() throws Exception {
        certBundle = TestCertificateGenerator.generateCertificates();
        clientSslContext = TestUtils.createClientSslContext(certBundle);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    private void setupForConfig(TransportConfig config) throws Exception {
        requestHandler = new RequestCapturingHttp2ClientHandler();

        var serverBuilder = NettyTestServer.builder()
                .httpVersion(config.httpVersion())
                .h2ConnectionMode(config.h2Mode())
                .http2HandlerFactory(ctx -> new MultiplexingHttp2ClientHandler(
                        requestHandler,
                        new TextResponseHttp2ClientHandler(RESPONSE_CONTENTS)));

        if (config.useTls()) {
            serverBuilder.sslContextBuilder(TestUtils.createServerSslContextBuilder(certBundle));
        }

        server = serverBuilder.build();
        server.start();

        var poolBuilder = HttpConnectionPool.builder()
                .maxConnectionsPerRoute(10)
                .maxTotalConnections(10)
                .maxIdleTime(Duration.ofMinutes(1))
                .dnsResolver(DnsResolver.staticMapping(Map.of("localhost", List.of(InetAddress.getLoopbackAddress()))))
                .httpVersionPolicy(config.versionPolicy());

        if (config.useTls()) {
            poolBuilder.sslContext(clientSslContext);
        }

        client = HttpClient.builder().connectionPool(poolBuilder.build()).build();
    }

    private String uri(TransportConfig config) {
        String scheme = config.useTls() ? "https" : "http";
        return scheme + "://localhost:" + server.getPort();
    }

    private String readBody(HttpResponse response) throws IOException {
        try (var body = response.body().asInputStream()) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = TransportConfig.class, names = {"H2C", "H2_TLS", "H2_ALPN"})
    void canSendStreamingRequestAndReadResponse(TransportConfig config) throws Exception {
        setupForConfig(config);

        var request = TestUtils.request(config.httpVersion(), uri(config), streamingBody(IPSUM_LOREM));
        var response = client.send(request);

        requestHandler.streamCompleted().join();

        assertEquals(String.join("", IPSUM_LOREM), requestHandler.capturedBody().toString(StandardCharsets.UTF_8));
        assertEquals(RESPONSE_CONTENTS, readBody(response));
    }
}
