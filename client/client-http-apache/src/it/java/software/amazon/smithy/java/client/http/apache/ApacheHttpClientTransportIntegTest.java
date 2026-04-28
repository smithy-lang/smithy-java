/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.apache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;

class ApacheHttpClientTransportIntegTest {
    private static final byte[] MB = new byte[1024 * 1024];

    static {
        for (int i = 0; i < MB.length; i++) {
            MB[i] = (byte) ('a' + (i % 26));
        }
    }

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void canUploadAndDownloadLargeBodies() throws Exception {
        AtomicInteger uploadedBytes = new AtomicInteger();
        startServer(exchange -> {
            if ("/putmb".equals(exchange.getRequestURI().getPath())) {
                uploadedBytes.set(readAll(exchange.getRequestBody()).length);
                byte[] response = Integer.toString(uploadedBytes.get()).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } else if ("/getmb".equals(exchange.getRequestURI().getPath())) {
                drain(exchange);
                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, MB.length);
                exchange.getResponseBody().write(MB);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        });

        try (var transport = newTransport()) {
            var put = HttpRequest.create()
                    .setUri(uri("/putmb"))
                    .setMethod("PUT")
                    .setBody(DataStream.ofBytes(MB))
                    .toUnmodifiable();
            try (HttpResponse response = transport.send(Context.create(), put)) {
                assertEquals(200, response.statusCode());
                assertEquals(Integer.toString(MB.length),
                        new String(response.body().asInputStream().readAllBytes(), StandardCharsets.UTF_8));
            }

            var get = HttpRequest.create()
                    .setUri(uri("/getmb"))
                    .setMethod("GET")
                    .toUnmodifiable();
            try (HttpResponse response = transport.send(Context.create(), get)) {
                assertEquals(200, response.statusCode());
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                response.body().asInputStream().transferTo(out);
                assertEquals(MB.length, out.size());
            }
        }

        assertEquals(MB.length, uploadedBytes.get());
    }

    private ApacheHttpClientTransport newTransport() {
        var config = new ApacheHttpTransportConfig();
        config.httpVersion(software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_1);
        config.requestTimeout(Duration.ofSeconds(10));
        config.maxConnectionsPerHost(4);
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

    private java.net.URI uri(String path) {
        return java.net.URI.create("http://localhost:" + server.getAddress().getPort() + path);
    }

    private static void drain(HttpExchange exchange) throws IOException {
        readAll(exchange.getRequestBody());
    }

    private static byte[] readAll(java.io.InputStream body) throws IOException {
        try (body; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            body.transferTo(out);
            return out.toByteArray();
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
