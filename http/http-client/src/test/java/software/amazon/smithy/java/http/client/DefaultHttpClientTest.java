/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.ConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.Route;
import software.amazon.smithy.java.io.datastream.DataStream;

class DefaultHttpClientTest {

    @Test
    void sendReturnsResponse() throws IOException {
        var pool = new TestConnectionPool();
        try (var client = HttpClient.builder().connectionPool(pool).build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

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
                    public OutputStream requestBody() {
                        return new OutputStream() {
                            private final StringBuilder sb = new StringBuilder();

                            @Override
                            public void write(int b) {
                                sb.append((char) b);
                            }

                            @Override
                            public void close() {
                                bodyWritten.set(sb.toString());
                            }
                        };
                    }
                };
            }
        };
        try (var client = HttpClient.builder().connectionPool(pool).build()) {
            var request = HttpRequest.builder()
                    .method("POST")
                    .uri(URI.create("http://example.com/test"))
                    .body(DataStream.ofString("request-body"))
                    .build();

            client.send(request);

            assertEquals("request-body", bodyWritten.get(), "Request body should be written");
        }
    }

    @Test
    void beforeRequestInterceptorModifiesRequest() throws IOException {
        var capturedUri = new AtomicReference<URI>();
        var pool = new TestConnectionPool() {
            @Override
            protected HttpExchange createExchange() {
                return new TestHttpExchange() {
                    @Override
                    public HttpRequest request() {
                        return null;
                    }
                };
            }

            @Override
            public HttpConnection acquire(Route route) {
                capturedUri.set(URI.create(route.scheme() + "://" + route.host() + ":" + route.port()));
                return super.acquire(route);
            }
        };
        var interceptor = new HttpInterceptor() {
            @Override
            public HttpRequest beforeRequest(HttpClient client, HttpRequest request, Context context) {
                return request.toBuilder()
                        .uri(URI.create("http://modified.com/path"))
                        .build();
            }
        };
        try (var client = HttpClient.builder().connectionPool(pool).addInterceptor(interceptor).build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://original.com/test"))
                    .build();

            client.send(request);

            assertEquals("http://modified.com:80",
                    capturedUri.get().toString(),
                    "Request URI should be modified by interceptor");
        }
    }

    @Test
    void preemptRequestReturnsWithoutNetworkCall() throws IOException {
        var networkCalled = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            public HttpConnection acquire(Route route) {
                networkCalled.set(true);
                return super.acquire(route);
            }
        };
        var interceptor = new HttpInterceptor() {
            @Override
            public HttpResponse preemptRequest(HttpClient client, HttpRequest request, Context context) {
                return HttpResponse.builder()
                        .statusCode(304)
                        .body(DataStream.ofString("cached"))
                        .build();
            }
        };
        try (var client = HttpClient.builder().connectionPool(pool).addInterceptor(interceptor).build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

            var response = client.send(request);

            assertEquals(304, response.statusCode(), "Should return preempted response");
            assertEquals("cached",
                    new String(response.body().asInputStream().readAllBytes()),
                    "Should return preempted body");
            assertFalse(networkCalled.get(), "Should not make network call when preempted");
        }
    }

    @Test
    void preemptedResponseCanBeReplacedByInterceptResponse() throws IOException {
        var pool = new TestConnectionPool();
        var interceptor = new HttpInterceptor() {
            @Override
            public HttpResponse preemptRequest(HttpClient client, HttpRequest request, Context context) {
                return HttpResponse.builder()
                        .statusCode(304)
                        .body(DataStream.ofString("cached"))
                        .build();
            }

            @Override
            public HttpResponse interceptResponse(
                    HttpClient client,
                    HttpRequest request,
                    Context context,
                    HttpResponse response
            ) {
                return HttpResponse.builder()
                        .statusCode(200)
                        .body(DataStream.ofString("modified-cached"))
                        .build();
            }
        };
        try (var client = HttpClient.builder().connectionPool(pool).addInterceptor(interceptor).build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

            var response = client.send(request);

            assertEquals(200, response.statusCode(), "Should return intercepted status");
            assertEquals("modified-cached",
                    new String(response.body().asInputStream().readAllBytes()),
                    "Should return intercepted body");
        }
    }

    @Test
    void preemptRequestFailsWithoutRecovery() throws IOException {
        var pool = new TestConnectionPool();
        var interceptor = new HttpInterceptor() {
            @Override
            public HttpResponse preemptRequest(HttpClient client, HttpRequest request, Context context)
                    throws IOException {
                throw new IOException("preempt failed");
            }
        };
        try (var client = HttpClient.builder().connectionPool(pool).addInterceptor(interceptor).build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

            var ex = assertThrows(IOException.class, () -> client.send(request));
            assertEquals("preempt failed", ex.getMessage(), "Should propagate preempt exception");
        }
    }

    @Test
    void preemptRequestFailsAndRecovers() throws IOException {
        var pool = new TestConnectionPool();
        var interceptor = new HttpInterceptor() {
            @Override
            public HttpResponse preemptRequest(HttpClient client, HttpRequest request, Context context) {
                return HttpResponse.builder()
                        .statusCode(200)
                        .body(DataStream.ofString("preempted"))
                        .build();
            }

            @Override
            public HttpResponse interceptResponse(
                    HttpClient client,
                    HttpRequest request,
                    Context context,
                    HttpResponse response
            ) throws IOException {
                throw new IOException("intercept failed");
            }

            @Override
            public HttpResponse onError(
                    HttpClient client,
                    HttpRequest request,
                    Context context,
                    IOException error
            ) {
                return HttpResponse.builder()
                        .statusCode(503)
                        .body(DataStream.ofString("recovered"))
                        .build();
            }
        };
        try (var client = HttpClient.builder().connectionPool(pool).addInterceptor(interceptor).build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

            var response = client.send(request);

            assertEquals(503, response.statusCode(), "Should return recovered response");
            assertEquals("recovered",
                    new String(response.body().asInputStream().readAllBytes()),
                    "Should return recovered body");
        }
    }

    @Test
    void interceptResponseCanReplaceResponse() throws IOException {
        var pool = new TestConnectionPool();
        var interceptor = new HttpInterceptor() {
            @Override
            public HttpResponse interceptResponse(
                    HttpClient client,
                    HttpRequest request,
                    Context context,
                    HttpResponse response
            ) {
                return HttpResponse.builder()
                        .statusCode(999)
                        .body(DataStream.ofString("intercepted"))
                        .build();
            }
        };
        try (var client = HttpClient.builder().connectionPool(pool).addInterceptor(interceptor).build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

            var response = client.send(request);

            assertEquals(999, response.statusCode(), "Should return intercepted status");
            assertEquals("intercepted",
                    new String(response.body().asInputStream().readAllBytes()),
                    "Should return intercepted body");
        }
    }

    @Test
    void onErrorCanRecoverFromNetworkFailure() throws IOException {
        var pool = new TestConnectionPool() {
            @Override
            public HttpConnection acquire(Route route) {
                return new TestConnection() {
                    @Override
                    public HttpExchange newExchange(HttpRequest request) throws IOException {
                        throw new IOException("network failure");
                    }
                };
            }
        };
        var interceptor = new HttpInterceptor() {
            @Override
            public HttpResponse onError(
                    HttpClient client,
                    HttpRequest request,
                    Context context,
                    IOException error
            ) {
                return HttpResponse.builder()
                        .statusCode(503)
                        .body(DataStream.ofString("fallback"))
                        .build();
            }
        };
        try (var client = HttpClient.builder().connectionPool(pool).addInterceptor(interceptor).build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

            var response = client.send(request);

            assertEquals(503, response.statusCode(), "Should return recovery response");
            assertEquals("fallback",
                    new String(response.body().asInputStream().readAllBytes()),
                    "Should return recovery body");
        }
    }

    @Test
    void interceptorsExecuteInCorrectOrder() throws IOException {
        var order = new AtomicInteger(0);
        var beforeA = new AtomicInteger();
        var beforeB = new AtomicInteger();
        var responseA = new AtomicInteger();
        var responseB = new AtomicInteger();

        var interceptorA = new HttpInterceptor() {
            @Override
            public HttpRequest beforeRequest(HttpClient client, HttpRequest request, Context context) {
                beforeA.set(order.incrementAndGet());
                return request;
            }

            @Override
            public HttpResponse interceptResponse(
                    HttpClient client,
                    HttpRequest request,
                    Context context,
                    HttpResponse response
            ) {
                responseA.set(order.incrementAndGet());
                return response;
            }
        };
        var interceptorB = new HttpInterceptor() {
            @Override
            public HttpRequest beforeRequest(HttpClient client, HttpRequest request, Context context) {
                beforeB.set(order.incrementAndGet());
                return request;
            }

            @Override
            public HttpResponse interceptResponse(
                    HttpClient client,
                    HttpRequest request,
                    Context context,
                    HttpResponse response
            ) {
                responseB.set(order.incrementAndGet());
                return response;
            }
        };

        var pool = new TestConnectionPool();
        try (var client = HttpClient.builder()
                .connectionPool(pool)
                .addInterceptor(interceptorA)
                .addInterceptor(interceptorB)
                .build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

            client.send(request);

            assertEquals(1, beforeA.get(), "beforeRequest A should be first");
            assertEquals(2, beforeB.get(), "beforeRequest B should be second");
            assertEquals(3, responseB.get(), "interceptResponse B should be third (reverse order)");
            assertEquals(4, responseA.get(), "interceptResponse A should be fourth (reverse order)");
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
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

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
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

            var response = client.send(request);

            assertEquals(200, response.statusCode(), "Should complete within timeout");
        }
    }

    @Test
    void newExchangeReturnsExchange() throws IOException {
        var pool = new TestConnectionPool();
        try (var client = HttpClient.builder().connectionPool(pool).build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

            var exchange = client.newExchange(request);

            assertNotNull(exchange, "Should return exchange");
            assertEquals(200, exchange.responseStatusCode(), "Should return status from delegate");
            exchange.close();
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
        var proxy = new ProxyConfiguration(URI.create("http://proxy.example.com:8080"),
                ProxyConfiguration.ProxyType.HTTP);
        try (var client = HttpClient.builder()
                .connectionPool(pool)
                .proxy(proxy)
                .build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

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
        var proxy1 = new ProxyConfiguration(URI.create("http://proxy1.example.com:8080"),
                ProxyConfiguration.ProxyType.HTTP);
        var proxy2 = new ProxyConfiguration(URI.create("http://proxy2.example.com:9090"),
                ProxyConfiguration.ProxyType.HTTP);
        var connectFailedCalled = new AtomicBoolean(false);
        var selector = new ProxySelector() {
            @Override
            public List<ProxyConfiguration> select(URI target, Context context) {
                return List.of(proxy1, proxy2);
            }

            @Override
            public void connectFailed(URI target, Context context, ProxyConfiguration proxy, IOException cause) {
                connectFailedCalled.set(true);
            }
        };
        try (var client = HttpClient.builder()
                .connectionPool(pool)
                .proxySelector(selector)
                .build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

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
        var proxy1 = new ProxyConfiguration(URI.create("http://proxy1.example.com:8080"),
                ProxyConfiguration.ProxyType.HTTP);
        var proxy2 = new ProxyConfiguration(URI.create("http://proxy2.example.com:9090"),
                ProxyConfiguration.ProxyType.HTTP);
        var selector = ProxySelector.of(proxy1, proxy2);
        try (var client = HttpClient.builder()
                .connectionPool(pool)
                .proxySelector(selector)
                .build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

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
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();

            assertThrows(IOException.class, () -> client.send(request));
            assertTrue(evicted.get(), "Connection should be evicted on exchange creation failure");
        }
    }

    @Test
    void requestOptionsInterceptorsAreApplied() throws IOException {
        var clientInterceptorCalled = new AtomicBoolean(false);
        var requestInterceptorCalled = new AtomicBoolean(false);

        var clientInterceptor = new HttpInterceptor() {
            @Override
            public HttpRequest beforeRequest(HttpClient client, HttpRequest request, Context context) {
                clientInterceptorCalled.set(true);
                return request;
            }
        };
        var requestInterceptor = new HttpInterceptor() {
            @Override
            public HttpRequest beforeRequest(HttpClient client, HttpRequest request, Context context) {
                requestInterceptorCalled.set(true);
                return request;
            }
        };

        var pool = new TestConnectionPool();
        try (var client = HttpClient.builder()
                .connectionPool(pool)
                .addInterceptor(clientInterceptor)
                .build()) {
            var request = HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();
            var options = RequestOptions.builder()
                    .addInterceptor(requestInterceptor)
                    .build();

            client.send(request, options);

            assertTrue(clientInterceptorCalled.get(), "Client interceptor should be called");
            assertTrue(requestInterceptorCalled.get(), "Request interceptor should be called");
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
            return HttpRequest.builder()
                    .method("GET")
                    .uri(URI.create("http://example.com/test"))
                    .build();
        }

        @Override
        public OutputStream requestBody() {
            return OutputStream.nullOutputStream();
        }

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
