/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.ConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.io.uri.SmithyUri;

class DefaultHttpClientTest {

    @Test
    void sendReturnsResponse() throws IOException {
        var pool = new TestConnectionPool();
        try (var client = HttpClient.builder().connectionPool(pool).build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);

            assertEquals(200, response.statusCode(), "Should return status from exchange");
            assertEquals("test-body",
                    new String(response.body().asInputStream().readAllBytes()),
                    "Should return body from exchange");
        }
    }

    @Test
    void sendWritesRequestBody() throws IOException {
        var bodyWritten = new AtomicReference<String>();
        var pool = new TestConnectionPool() {
            @Override
            protected HttpExchange createExchange() {
                return new TestHttpExchange() {
                    @Override
                    public void writeRequestBody(DataStream body) throws IOException {
                        bodyWritten.set(body == null ? "" : new String(body.asInputStream().readAllBytes()));
                    }
                };
            }
        };
        try (var client = HttpClient.builder().connectionPool(pool).build()) {
            var request = HttpRequest.create()
                    .setMethod("POST")
                    .setUri(SmithyUri.of("http://example.com/test"))
                    .setBody(DataStream.ofString("request-body"));

            client.send(request);

            assertEquals("request-body", bodyWritten.get(), "Request body should be written");
        }
    }

    @Test
    void requestTimeoutThrowsOnTimeout() throws IOException {
        var pool = new TestConnectionPool() {
            @Override
            protected HttpExchange createExchange() {
                return new TestHttpExchange() {
                    @Override
                    public int responseStatusCode() throws IOException {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            throw new IOException("interrupted", e);
                        }
                        return 200;
                    }
                };
            }
        };
        try (var client = HttpClient.builder()
                .connectionPool(pool)
                .requestTimeout(Duration.ofMillis(50))
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var ex = assertThrows(IOException.class, () -> client.send(request));
            assertTrue(ex.getMessage().contains("exceeded request timeout"),
                    "Should indicate timeout: " + ex.getMessage());
        }
    }

    @Test
    void requestTimeoutSucceedsWhenFastEnough() throws IOException {
        var pool = new TestConnectionPool();
        try (var client = HttpClient.builder()
                .connectionPool(pool)
                .requestTimeout(Duration.ofSeconds(5))
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);

            assertEquals(200, response.statusCode(), "Should complete within timeout");
        }
    }

    @Test
    void proxySelectorsAreUsed() throws IOException {
        var proxyUsed = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            public HttpConnection acquire(Route route) {
                if (route.usesProxy()) {
                    proxyUsed.set(true);
                }
                return super.acquire(route);
            }
        };
        var proxy = new ProxyConfiguration(SmithyUri.of("http://proxy.example.com:8080"),
                ProxyConfiguration.ProxyType.HTTP);
        try (var client = HttpClient.builder()
                .connectionPool(pool)
                .proxy(proxy)
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            client.send(request);

            assertTrue(proxyUsed.get(), "Proxy should be used");
        }
    }

    @Test
    void proxyFailoverSucceedsOnSecondProxy() throws IOException {
        var attemptedProxies = new AtomicInteger(0);
        var pool = new TestConnectionPool() {
            @Override
            public HttpConnection acquire(Route route) {
                attemptedProxies.incrementAndGet();
                if (route.proxy() != null && route.proxy().port() == 8080) {
                    return new TestConnection() {
                        @Override
                        public HttpExchange newExchange(HttpRequest request) throws IOException {
                            throw new IOException("first proxy failed");
                        }
                    };
                }
                return super.acquire(route);
            }
        };
        var proxy1 = new ProxyConfiguration(SmithyUri.of("http://proxy1.example.com:8080"),
                ProxyConfiguration.ProxyType.HTTP);
        var proxy2 = new ProxyConfiguration(SmithyUri.of("http://proxy2.example.com:9090"),
                ProxyConfiguration.ProxyType.HTTP);
        var connectFailedCalled = new AtomicBoolean(false);
        var selector = new ProxySelector() {
            @Override
            public List<ProxyConfiguration> select(SmithyUri target) {
                return List.of(proxy1, proxy2);
            }

            @Override
            public void connectFailed(SmithyUri target, ProxyConfiguration proxy, IOException cause) {
                connectFailedCalled.set(true);
            }
        };
        try (var client = HttpClient.builder()
                .connectionPool(pool)
                .proxySelector(selector)
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);

            assertEquals(200, response.statusCode(), "Should succeed via second proxy");
            assertEquals(2, attemptedProxies.get(), "Should have tried both proxies");
            assertTrue(connectFailedCalled.get(), "connectFailed should be called for first proxy");
        }
    }

    @Test
    void proxyFailoverThrowsWhenAllProxiesFail() throws IOException {
        var attemptedProxies = new AtomicInteger(0);
        var pool = new TestConnectionPool() {
            @Override
            public HttpConnection acquire(Route route) {
                attemptedProxies.incrementAndGet();
                return new TestConnection() {
                    @Override
                    public HttpExchange newExchange(HttpRequest request) throws IOException {
                        throw new IOException("proxy " + attemptedProxies.get() + " failed");
                    }
                };
            }
        };
        var proxy1 = new ProxyConfiguration(SmithyUri.of("http://proxy1.example.com:8080"),
                ProxyConfiguration.ProxyType.HTTP);
        var proxy2 = new ProxyConfiguration(SmithyUri.of("http://proxy2.example.com:9090"),
                ProxyConfiguration.ProxyType.HTTP);
        var selector = ProxySelector.of(proxy1, proxy2);
        try (var client = HttpClient.builder()
                .connectionPool(pool)
                .proxySelector(selector)
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var ex = assertThrows(IOException.class, () -> client.send(request));
            assertEquals("proxy 2 failed", ex.getMessage(), "Should throw last proxy's exception");
            assertEquals(2, attemptedProxies.get(), "Should have tried both proxies");
        }
    }

    @Test
    void connectionEvictedOnExchangeCreationFailure() throws IOException {
        var evicted = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            public HttpConnection acquire(Route route) {
                return new TestConnection() {
                    @Override
                    public HttpExchange newExchange(HttpRequest request) throws IOException {
                        throw new IOException("exchange creation failed");
                    }
                };
            }

            @Override
            public void evict(HttpConnection connection, boolean close) {
                evicted.set(true);
            }
        };
        try (var client = HttpClient.builder().connectionPool(pool).build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            assertThrows(IOException.class, () -> client.send(request));
            assertTrue(evicted.get(), "Connection should be evicted on exchange creation failure");
        }
    }

    @Test
    void readNBytesExactKnownLengthReleasesConnection() throws IOException {
        var released = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            protected HttpExchange createExchange() {
                return new TestHttpExchange() {
                    @Override
                    public long responseContentLength() {
                        return 9;
                    }
                };
            }

            @Override
            public void release(HttpConnection connection) {
                released.set(true);
            }
        };
        try (var client = HttpClient.builder().connectionPool(pool).build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);
            var stream = response.body().asInputStream();

            assertEquals("test", new String(stream.readNBytes(4)));
            assertFalse(released.get(), "Connection should remain leased until the known body length is consumed");

            assertEquals("-body", new String(stream.readNBytes(5)));
            assertTrue(released.get(), "Connection should release when readNBytes consumes the known body length");
        }
    }

    @Test
    void discardDrainsH2ResponseBeforeReleasingConnection() throws IOException {
        var drained = new AtomicBoolean(false);
        var closed = new AtomicBoolean(false);
        var released = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            protected HttpExchange createExchange() {
                return new TestHttpExchange() {
                    @Override
                    public HttpVersion responseVersion() {
                        return HttpVersion.HTTP_2;
                    }

                    @Override
                    public void discardResponseBody() {
                        drained.set(true);
                    }

                    @Override
                    public void close() {
                        closed.set(true);
                    }
                };
            }

            @Override
            public void release(HttpConnection connection) {
                released.set(true);
            }
        };
        try (var client = HttpClient.builder().connectionPool(pool).build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);
            response.body().discard();

            assertTrue(drained.get(), "H2 discard should drain the response body instead of immediately closing");
            assertTrue(closed.get(), "Exchange should still close after the body is drained");
            assertTrue(released.get(), "Drained H2 exchange should release the connection");
        }
    }

    // Test fixtures

    private static class TestConnectionPool implements ConnectionPool {
        @Override
        public HttpConnection acquire(Route route) {
            return new TestConnection() {
                @Override
                public HttpExchange newExchange(HttpRequest request) {
                    return createExchange();
                }
            };
        }

        protected HttpExchange createExchange() {
            return new TestHttpExchange();
        }

        @Override
        public void release(HttpConnection connection) {}

        @Override
        public void evict(HttpConnection connection, boolean close) {}

        @Override
        public void close() {}

        @Override
        public void shutdown(Duration timeout) {}
    }

    private static class TestConnection implements HttpConnection {
        @Override
        public HttpExchange newExchange(HttpRequest request) throws IOException {
            return new TestHttpExchange();
        }

        @Override
        public HttpVersion httpVersion() {
            return HttpVersion.HTTP_1_1;
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public Route route() {
            return Route.direct("http", "example.com", 80);
        }

        @Override
        public void close() {}

        @Override
        public SSLSession sslSession() {
            return null;
        }

        @Override
        public String negotiatedProtocol() {
            return null;
        }

        @Override
        public boolean validateForReuse() {
            return true;
        }
    }

    private static class TestHttpExchange implements HttpExchange {
        @Override
        public HttpRequest request() {
            return HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));
        }

        @Override
        public void writeRequestBody(DataStream body) throws IOException {}

        @Override
        public InputStream responseBody() {
            return new ByteArrayInputStream("test-body".getBytes());
        }

        @Override
        public HttpHeaders responseHeaders() {
            return HttpHeaders.of(Map.of());
        }

        @Override
        public int responseStatusCode() throws IOException {
            return 200;
        }

        @Override
        public HttpVersion responseVersion() {
            return HttpVersion.HTTP_1_1;
        }

        @Override
        public void close() {}
    }
}
