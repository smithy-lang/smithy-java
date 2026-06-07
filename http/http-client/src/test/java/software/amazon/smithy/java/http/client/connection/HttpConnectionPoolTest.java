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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.client.HttpClientListener;
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
        try (var pool = new HttpConnectionPool(ConnectionConfig.builder()
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .maxTotalConnections(1)
                .maxConnectionsPerRoute(1)
                .acquireTimeout(Duration.ZERO)
                .dnsResolver(dns)
                .socketFactory((route, endpoints) -> {
                    socketCreates.incrementAndGet();
                    return new FakeSocket();
                })
                .build())) {

            var first = pool.acquire(Route.direct("http", "one.example.com", 80), 1);
            pool.release(first);

            var ex = assertThrows(
                    IOException.class,
                    () -> pool.acquire(Route.direct("http", "two.example.com", 80), 2));
            assertEquals("Connection pool exhausted: 1 connections in use (timed out after 0ms)", ex.getMessage());
            assertEquals(1, socketCreates.get(), "The idle first-route socket should still hold the global permit");
        }
    }

    @Test
    void listenerReceivesExchangeScopedDnsAndConnectEvents() throws IOException {
        var address = InetAddress.getByName("127.0.0.1");
        var events = new ArrayList<String>();
        var dns = DnsResolver.staticMapping(Map.of("example.com", List.of(address)));
        var listener = new HttpClientListener() {
            @Override
            public void onDnsStart(long exchangeId, String host) {
                events.add("dns-start:" + exchangeId + ":" + host);
            }

            @Override
            public void onDnsEnd(long exchangeId, String host, List<InetAddress> addresses, Throwable error) {
                events.add("dns-end:" + exchangeId + ":" + host + ":" + addresses.size() + ":" + (error == null));
            }

            @Override
            public void onConnectStart(long exchangeId, Route route, InetAddress address) {
                events.add("connect-start:" + exchangeId + ":" + address.getHostAddress());
            }

            @Override
            public void onConnectEnd(long exchangeId, Route route, InetAddress address, Throwable error) {
                events.add("connect-end:" + exchangeId + ":" + address.getHostAddress() + ":" + (error == null));
            }

            @Override
            public void onConnectionCreated(HttpConnection connection) {
                events.add("created");
            }

            @Override
            public void onConnectionAcquired(HttpConnection connection, boolean reused) {
                events.add("acquired:" + reused);
            }
        };

        try (var pool = new HttpConnectionPool(ConnectionConfig.builder()
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .dnsResolver(dns)
                .socketFactory((route, endpoints) -> new FakeSocket())
                .addListener(listener)
                .build())) {
            pool.acquire(Route.direct("http", "example.com", 80), 123);
        }

        assertEquals(List.of(
                "dns-start:123:example.com",
                "dns-end:123:example.com:1:true",
                "connect-start:123:127.0.0.1",
                "connect-end:123:127.0.0.1:true",
                "created",
                "acquired:false"), events);
    }

    @Test
    void listenerExceptionOnCloseDoesNotLeakPermit() throws IOException {
        var dns = DnsResolver.staticMapping(Map.of(
                "one.example.com",
                List.of(InetAddress.getByName("127.0.0.1")),
                "two.example.com",
                List.of(InetAddress.getByName("127.0.0.1"))));
        var listener = new HttpClientListener() {
            @Override
            public void onConnectionClosed(HttpConnection connection, CloseReason reason) {
                throw new RuntimeException("boom");
            }
        };

        try (var pool = new HttpConnectionPool(ConnectionConfig.builder()
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .maxTotalConnections(1)
                .maxConnectionsPerRoute(1)
                .acquireTimeout(Duration.ZERO)
                .dnsResolver(dns)
                .socketFactory((route, endpoints) -> new FakeSocket())
                .addListener(listener)
                .build())) {
            var first = pool.acquire(Route.direct("http", "one.example.com", 80), 1);
            pool.evict(first, false);

            var second = pool.acquire(Route.direct("http", "two.example.com", 80), 2);
            assertEquals("two.example.com", second.route().host());
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
