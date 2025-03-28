/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.codegen.client.util;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.utils.SmithyInternalApi;

/**
 * Simple test server that echo requests back to sender.
 *
 * <p>This server will wait for a single call, then close.
 */
@SmithyInternalApi
public final class EchoServer {
    private static final InternalLogger LOGGER = InternalLogger.getLogger(EchoServer.class);

    private HttpServer server;

    public void start(int port) {
        try {
            server = HttpServer.create();
            server.bind(new InetSocketAddress(port), 0);
            server.createContext("/echo", new EchoHandler());
            server.start();
            LOGGER.debug("EchoServer started on port {}", port);
        } catch (IOException ex) {
            stop();
            throw new RuntimeException("Echo server encountered exception", ex);
        }
    }

    public void stop() {
        LOGGER.debug("Stopping EchoServer");
        if (server != null) {
            server.stop(0);
        }
    }

    private static class EchoHandler implements HttpHandler {
        private static final Set<String> STANDARD_HEADERS = Set.of(
                "user-agent",
                "content-type",
                "content-length",
                "accept",
                "host");

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            Headers responseHeaders = httpExchange.getResponseHeaders();
            // Add any non-standard, custom headers to response
            for (var reqHeader : httpExchange.getRequestHeaders().entrySet()) {
                if (!STANDARD_HEADERS.contains(reqHeader.getKey())) {
                    responseHeaders.set(reqHeader.getKey(), String.valueOf(reqHeader.getValue()));
                }
            }
            responseHeaders.set("content-type", "application/json");

            try (
                    var bos = new ByteArrayOutputStream();
                    var writer = new BufferedWriter(
                            new OutputStreamWriter(bos, StandardCharsets.UTF_8));
                    var responseBody = httpExchange.getResponseBody()) {
                var body = httpExchange.getRequestBody().readAllBytes();
                httpExchange.sendResponseHeaders(200, body.length);
                responseBody.write(body);
            }
        }
    }
}
