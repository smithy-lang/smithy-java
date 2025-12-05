/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.it.server.MultiplexingHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.RequestCapturingHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.TextResponseHttp11ClientHandler;

public class RequestResponseHttp11ClearTest {
    private static final String RESPONSE_CONTENTS = "Response sent from Http11ClearTest";
    private static final String REQUEST_CONTENTS = "Request sent from Http11ClearTest";
    private RequestCapturingHttp11ClientHandler requestCapturingHandler;
    private NettyTestServer server;
    private HttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        requestCapturingHandler = new RequestCapturingHttp11ClientHandler();
        var multiplexer = new MultiplexingHttp11ClientHandler(requestCapturingHandler,
                new TextResponseHttp11ClientHandler(RESPONSE_CONTENTS));
        server = NettyTestServer.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory((ctx) -> multiplexer)
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
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                        .dnsResolver(staticDns)
                        .build())
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop();
        client.close();
    }

    @Test
    void canSendRequestAndReadResponse() throws Exception {
        // -- Arrange
        var request = TestUtils.plainTextHttp11Request("http://localhost:" + server.getPort(), REQUEST_CONTENTS);

        // -- Act
        var response = client.send(request);
        var bodyByteBuf = response.body().asByteBuffer();
        var bytes = new byte[bodyByteBuf.remaining()];
        bodyByteBuf.get(bytes);
        var responseBody = new String(bytes, StandardCharsets.UTF_8);

        // -- Assert
        var capturedRequestBody = requestCapturingHandler.capturedBody().toString(StandardCharsets.UTF_8);
        assertEquals(REQUEST_CONTENTS, capturedRequestBody);
        assertEquals(RESPONSE_CONTENTS, responseBody);
    }
}
