/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.TestCertificateGenerator;
import software.amazon.smithy.java.http.client.it.server.h1.MultiplexingHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h1.RequestCapturingHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h1.TextResponseHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h2.MultiplexingHttp2ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h2.RequestCapturingHttp2ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h2.TextResponseHttp2ClientHandler;

/**
 * Parameterized test for basic request/response across all transport configurations.
 */
public class RequestResponseTest {

    private static final String RESPONSE_CONTENTS = "Test response body";
    private static final String REQUEST_CONTENTS = "Test request body";

    private static TestCertificateGenerator.CertificateBundle certBundle;
    private static SSLContext clientSslContext;

    private NettyTestServer server;
    private HttpClient client;
    private RequestCapturingHttp2ClientHandler h2RequestHandler;
    private RequestCapturingHttp11ClientHandler h1RequestHandler;

    @BeforeAll
    static void beforeAll() throws Exception {
        certBundle = TestCertificateGenerator.generateCertificates();
        clientSslContext = TestUtils.createClientSslContext(certBundle);
    }

    @BeforeEach
    void setUp(TestInfo testInfo) {
        // Setup is done in the test method based on config
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
        var serverBuilder = NettyTestServer.builder().httpVersion(config.httpVersion());

        if (config.isHttp2()) {
            h2RequestHandler = new RequestCapturingHttp2ClientHandler();
            serverBuilder.h2ConnectionMode(config.h2Mode())
                    .http2HandlerFactory(ctx -> new MultiplexingHttp2ClientHandler(
                            h2RequestHandler,
                            new TextResponseHttp2ClientHandler(RESPONSE_CONTENTS)));
        } else {
            h1RequestHandler = new RequestCapturingHttp11ClientHandler();
            serverBuilder.http11HandlerFactory(ctx -> new MultiplexingHttp11ClientHandler(
                    h1RequestHandler,
                    new TextResponseHttp11ClientHandler(RESPONSE_CONTENTS)));
        }

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
    @EnumSource(TransportConfig.class)
    void canSendRequestAndReadResponse(TransportConfig config) throws Exception {
        setupForConfig(config);

        var request = TestUtils.plainTextRequest(config.httpVersion(), uri(config), REQUEST_CONTENTS);
        var response = client.send(request);
        var responseBody = readBody(response);

        String capturedBody;
        if (config.isHttp2()) {
            h2RequestHandler.streamCompleted().join();
            capturedBody = h2RequestHandler.capturedBody().toString(StandardCharsets.UTF_8);
        } else {
            capturedBody = h1RequestHandler.capturedBody().toString(StandardCharsets.UTF_8);
        }

        assertEquals(REQUEST_CONTENTS, capturedBody);
        assertEquals(RESPONSE_CONTENTS, responseBody);
    }
}
