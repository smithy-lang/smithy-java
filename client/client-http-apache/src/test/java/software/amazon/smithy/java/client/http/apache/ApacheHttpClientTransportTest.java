/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.apache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.apache.hc.core5.http.nio.AsyncEntityProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

class ApacheHttpClientTransportTest {
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void usesByteBufferEntityProducerForReplayableInMemoryBodies() {
        AsyncEntityProducer producer =
                ApacheRequestProducerFactory
                        .createEntityProducer(DataStream.ofBytes("abc".getBytes(StandardCharsets.UTF_8)));

        assertInstanceOf(ByteBufferEntityProducer.class, producer);
    }

    @Test
    void usesStreamingProducerForStreamingBodies() {
        DataStream streaming = DataStream.ofInputStream(
                () -> new java.io.ByteArrayInputStream("abc".getBytes(StandardCharsets.UTF_8)),
                "text/plain",
                3);

        AsyncEntityProducer producer = ApacheRequestProducerFactory.createEntityProducer(streaming);

        assertInstanceOf(DataStreamEntityProducer.class, producer);
    }

    @Test
    void buildsExplicitAuthorityAndPathRequestTarget() throws Exception {
        var request = HttpRequest.create()
                .setUri(java.net.URI.create("https://localhost:8443/getmb?x=1&y=2"))
                .setMethod("GET")
                .toUnmodifiable();

        var apacheRequest = ApacheRequestProducerFactory.createRequest(request);

        assertEquals("https", apacheRequest.getScheme());
        assertEquals("localhost", apacheRequest.getAuthority().getHostName());
        assertEquals(8443, apacheRequest.getAuthority().getPort());
        assertEquals("/getmb?x=1&y=2", apacheRequest.getPath());
        assertNull(apacheRequest.getHeader("host"));
    }

    @Test
    void completesZeroLengthResponseBodies() throws Exception {
        startServer(exchange -> {
            drain(exchange);
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });

        try (var transport = newTransport()) {
            var request = HttpRequest.create()
                    .setUri(serverUri("/empty"))
                    .setMethod("POST")
                    .setBody(DataStream.ofBytes("payload".getBytes(StandardCharsets.UTF_8)))
                    .toUnmodifiable();

            try (HttpResponse response = transport.send(Context.create(), request)) {
                assertInstanceOf(ApacheHttpResponse.class, response);
                assertInstanceOf(ApacheHttpHeaders.class, response.headers());
                assertEquals(200, response.statusCode());
                assertEquals(0, response.body().asInputStream().readAllBytes().length);
            }
        }
    }

    @Test
    void streamsLargeResponseBodies() throws Exception {
        byte[] payload = new byte[256 * 1024];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i & 0x7F);
        }
        startServer(exchange -> {
            drain(exchange);
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, payload.length);
            exchange.getResponseBody().write(payload);
            exchange.close();
        });

        try (var transport = newTransport()) {
            var request = HttpRequest.create()
                    .setUri(serverUri("/body"))
                    .setMethod("GET")
                    .toUnmodifiable();

            try (HttpResponse response = transport.send(Context.create(), request)) {
                assertInstanceOf(ApacheHttpResponse.class, response);
                assertInstanceOf(ApacheHttpHeaders.class, response.headers());
                assertEquals(200, response.statusCode());
                assertArrayEquals(payload, response.body().asInputStream().readAllBytes());
            }
        }
    }

    private ApacheHttpClientTransport newTransport() {
        var config = new ApacheHttpTransportConfig();
        config.httpVersion(software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_1);
        config.requestTimeout(Duration.ofSeconds(5));
        return new ApacheHttpClientTransport(config);
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    private java.net.URI serverUri(String path) {
        return java.net.URI.create("http://localhost:" + server.getAddress().getPort() + path);
    }

    private static void drain(HttpExchange exchange) throws IOException {
        try (var body = exchange.getRequestBody()) {
            body.transferTo(new ByteArrayOutputStream());
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
