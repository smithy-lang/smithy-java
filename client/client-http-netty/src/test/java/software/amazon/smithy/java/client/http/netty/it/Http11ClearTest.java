/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.http.netty.NettyHttpClientTransport;
import software.amazon.smithy.java.client.http.netty.it.server.MultiplexingHttp11ClientHandler;
import software.amazon.smithy.java.client.http.netty.it.server.NettyTestServer;
import software.amazon.smithy.java.client.http.netty.it.server.RequestCapturingHttp11ClientHandler;
import software.amazon.smithy.java.client.http.netty.it.server.TextResponseHttp11ClientHandler;
import software.amazon.smithy.java.http.api.HttpVersion;

public class Http11ClearTest {
    private static final String RESPONSE_CONTENTS = "Response sent from Http11ClearTest";
    private static final String REQUEST_CONTENTS = "Request sent from Http11ClearTest";
    private RequestCapturingHttp11ClientHandler requestCapturingHandler;
    private NettyTestServer server;

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
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void canSendRequestAndReadResponse() throws Exception {
        // -- Arrange
        var client = NettyHttpClientTransport.builder().build();
        var request = TestUtils.plainTextHttp11Request("http://localhost:" + server.getPort(), REQUEST_CONTENTS);

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
