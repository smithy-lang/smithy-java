/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Regression tests for the H1 connection-reuse crash: a pooled HTTP/1.1 connection used to throw
 * {@code IllegalArgumentException: Duplicate handler name: h1-response} on its second request,
 * because the per-request response handler was added with a fixed name and never removed, and the
 * connection was returned to the pool before the response body was drained.
 */
class NettyH1ConnectionReuseTest {

    private HttpServer server;
    private volatile ServerSocket rawServer;

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) {
            server.stop(0);
        }
        if (rawServer != null) {
            rawServer.close();
        }
    }

    private void startEchoServer(AtomicInteger requestCount) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/echo", exchange -> {
            requestCount.incrementAndGet();
            byte[] requestBytes = exchange.getRequestBody().readAllBytes();
            byte[] responseBytes =
                    (exchange.getRequestMethod() + ":" + new String(requestBytes, StandardCharsets.UTF_8))
                            .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("content-type", "text/plain");
            // Keep-alive is the default for HTTP/1.1; the connection should be reused.
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        server.start();
    }

    private static HttpRequest put(String uri, String body) {
        return HttpRequest.create()
                .setMethod("PUT")
                .setUri(URI.create(uri))
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setBody(DataStream.ofString(body, "text/plain"))
                .toUnmodifiable();
    }

    @Test
    void reusesSingleH1ConnectionAcrossSequentialRequests() throws Exception {
        var requestCount = new AtomicInteger();
        startEchoServer(requestCount);

        // Cap at one connection per host so every request after the first MUST reuse the same
        // pooled channel — this is exactly the scenario that previously crashed on request 2.
        var config = new NettyHttpTransportConfig().maxConnectionsPerHost(1);
        var transport = new NettyHttpClientTransport(config);
        try {
            String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/echo";
            for (int i = 0; i < 25; i++) {
                HttpResponse response = transport.send(Context.create(), put(uri, "msg-" + i));
                assertThat(response.statusCode(), equalTo(200));
                try (var body = response.body().asInputStream()) {
                    assertThat(
                            new String(body.readAllBytes(), StandardCharsets.UTF_8),
                            equalTo("PUT:msg-" + i));
                }
            }
            // All requests succeeded against a single-connection pool.
            assertEquals(25, requestCount.get());
        } finally {
            transport.close();
        }
    }

    @Test
    void reusesH1ConnectionsUnderConcurrency() throws Exception {
        var requestCount = new AtomicInteger();
        startEchoServer(requestCount);

        var config = new NettyHttpTransportConfig().maxConnectionsPerHost(4);
        var transport = new NettyHttpClientTransport(config);
        try {
            String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/echo";
            int tasks = 200;
            try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<String>> futures = new ArrayList<>(tasks);
                for (int i = 0; i < tasks; i++) {
                    final int idx = i;
                    futures.add(pool.submit(() -> {
                        HttpResponse response = transport.send(Context.create(), put(uri, "c-" + idx));
                        try (var body = response.body().asInputStream()) {
                            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
                        }
                    }));
                }
                for (int i = 0; i < tasks; i++) {
                    // Any "Duplicate handler name" regression surfaces here as an ExecutionException.
                    assertEquals("PUT:c-" + i, futures.get(i).get());
                }
            }
            assertEquals(tasks, requestCount.get());
        } finally {
            transport.close();
        }
    }

    /**
     * Reproduces a stale keep-alive: the server sends a complete keep-alive response (so the client
     * pools the connection as healthy), then closes that socket. The next request reuses the now-dead
     * pooled connection and the write fails — the transport must transparently retry on a fresh
     * connection rather than surfacing "Channel closed while waiting for writability".
     *
     * <p>A raw socket server is used (not {@link HttpServer}) so the response does NOT carry
     * {@code Connection: close}: the client believes the connection is reusable and pools it, which
     * is exactly the condition that makes reuse race a server-side close.
     */
    @Test
    void retriesWhenReusedConnectionWasClosedByServer() throws Exception {
        int requests = 8;
        var handled = new AtomicInteger();
        rawServer = new ServerSocket(0);
        var serverThread = new Thread(() -> {
            try {
                while (!rawServer.isClosed()) {
                    // Each accepted socket serves exactly ONE keep-alive response, then closes —
                    // so the connection the client pools is dead the next time it is reused.
                    Socket socket = rawServer.accept();
                    serveOneThenClose(socket);
                    handled.incrementAndGet();
                }
            } catch (Exception ignored) {
                // server closed
            }
        }, "raw-h1-test-server");
        serverThread.setDaemon(true);
        serverThread.start();

        // One connection per host forces reuse on every request after the first; reuseIdleTimeout=0
        // disables the proactive idle-age guard so the RETRY path (not eviction) is what recovers.
        var config = new NettyHttpTransportConfig()
                .maxConnectionsPerHost(1)
                .reuseIdleTimeout(java.time.Duration.ZERO);
        var transport = new NettyHttpClientTransport(config);
        try {
            String uri = "http://127.0.0.1:" + rawServer.getLocalPort() + "/echo";
            for (int i = 0; i < requests; i++) {
                HttpResponse response = transport.send(Context.create(), put(uri, "s-" + i));
                assertThat(response.statusCode(), equalTo(200));
                try (var body = response.body().asInputStream()) {
                    assertThat(
                            new String(body.readAllBytes(), StandardCharsets.UTF_8),
                            equalTo("ok"));
                }
            }
            // Every request ultimately succeeded despite each landing first on a dead pooled conn.
            assertEquals(requests, handled.get());
        } finally {
            transport.close();
        }
    }

    /** Read one HTTP/1.1 request (headers + optional body) and write a fixed keep-alive response, then close. */
    private static void serveOneThenClose(Socket socket) throws Exception {
        try (socket; InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
            int contentLength = drainRequestHeadersReturningContentLength(in);
            // Consume the request body so the client's write completes cleanly.
            for (int read = 0; read < contentLength; read++) {
                if (in.read() < 0) {
                    break;
                }
            }
            String body = "ok";
            String resp = "HTTP/1.1 200 OK\r\n"
                    + "content-type: text/plain\r\n"
                    + "content-length: " + body.length() + "\r\n"
                    + "\r\n"
                    + body;
            out.write(resp.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        // try-with-resources closes the socket here: the pooled connection is now dead.
    }

    /** Read request lines until the blank line; return parsed Content-Length (0 if absent). */
    private static int drainRequestHeadersReturningContentLength(InputStream in) throws Exception {
        var line = new StringBuilder();
        int contentLength = 0;
        int prev = -1;
        int cur;
        var headerLine = new StringBuilder();
        while ((cur = in.read()) != -1) {
            line.append((char) cur);
            if (prev == '\r' && cur == '\n') {
                String header = headerLine.toString();
                if (header.isEmpty()) {
                    break; // end of headers
                }
                int colon = header.indexOf(':');
                if (colon > 0 && header.substring(0, colon).trim().equalsIgnoreCase("content-length")) {
                    contentLength = Integer.parseInt(header.substring(colon + 1).trim());
                }
                headerLine.setLength(0);
            } else if (cur != '\r' && cur != '\n') {
                headerLine.append((char) cur);
            }
            prev = cur;
        }
        return contentLength;
    }
}
