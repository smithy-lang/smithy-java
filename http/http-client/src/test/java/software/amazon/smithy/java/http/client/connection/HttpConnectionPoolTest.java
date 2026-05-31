/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.client.dns.DnsResolver;

class HttpConnectionPoolTest {

    @Test
    void h1IdleConnectionsCountTowardMaxTotalConnections() throws IOException {
        var socketCreates = new AtomicInteger();
        var dns = DnsResolver.staticMapping(Map.of(
                "one.example.com",
                List.of(InetAddress.getByName("127.0.0.1")),
                "two.example.com",
                List.of(InetAddress.getByName("127.0.0.1"))));
        try (var pool = HttpConnectionPool.builder()
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .maxTotalConnections(1)
                .maxConnectionsPerRoute(1)
                .acquireTimeout(Duration.ZERO)
                .dnsResolver(dns)
                .socketFactory((route, endpoints) -> {
                    socketCreates.incrementAndGet();
                    return new FakeSocket();
                })
                .build()) {

            var first = pool.acquire(Route.direct("http", "one.example.com", 80));
            pool.release(first);

            var ex = assertThrows(
                    IOException.class,
                    () -> pool.acquire(Route.direct("http", "two.example.com", 80)));
            assertEquals("Connection pool exhausted: 1 connections in use (timed out after 0ms)", ex.getMessage());
            assertEquals(1, socketCreates.get(), "The idle first-route socket should still hold the global permit");
        }
    }

    private static final class FakeSocket extends Socket {
        private final InputStream in = new ByteArrayInputStream(new byte[0]);
        private final OutputStream out = new ByteArrayOutputStream();
        private boolean closed;

        @Override
        public void connect(SocketAddress endpoint, int timeout) {}

        @Override
        public InputStream getInputStream() {
            return in;
        }

        @Override
        public OutputStream getOutputStream() {
            return out;
        }

        @Override
        public void setTcpNoDelay(boolean on) {}

        @Override
        public void setKeepAlive(boolean on) {}

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
