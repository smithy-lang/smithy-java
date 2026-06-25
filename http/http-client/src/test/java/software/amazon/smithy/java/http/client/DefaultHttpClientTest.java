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
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
        try (var client = HttpClient.builder().connectionPoolFactory(config -> pool).build()) {
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
    void requestEndFiresWhenResponseBodyIsConsumed() throws IOException {
        var starts = new AtomicInteger();
        var ends = new AtomicInteger();
        var startedExchangeId = new AtomicLong();
        var endedExchangeId = new AtomicLong();
        var listener = new HttpClientListener() {
            @Override
            public void onRequestStart(long exchangeId, HttpRequest request) {
                starts.incrementAndGet();
                startedExchangeId.set(exchangeId);
            }

            @Override
            public void onRequestEnd(long exchangeId, Throwable error) {
                ends.incrementAndGet();
                endedExchangeId.set(exchangeId);
            }
        };

        try (var client = HttpClient.builder()
                .connectionPoolFactory(config -> new TestConnectionPool())
                .addListener(listener)
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);

            assertEquals(1, starts.get());
            assertEquals(0, ends.get(), "Streaming request should not end when headers are returned");

            assertEquals("test-body", new String(response.body().asInputStream().readAllBytes()));

            assertEquals(1, ends.get());
            assertEquals(startedExchangeId.get(), endedExchangeId.get());
        }
    }

    @Test
    void listenerExceptionDoesNotFailRequest() throws IOException {
        var listener = new HttpClientListener() {
            @Override
            public void onRequestStart(long exchangeId, HttpRequest request) {
                throw new RuntimeException("boom");
            }

            @Override
            public void onRequestEnd(long exchangeId, Throwable error) {
                throw new RuntimeException("boom");
            }
        };

        try (var client = HttpClient.builder()
                .connectionPoolFactory(config -> new TestConnectionPool())
                .addListener(listener)
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);

            assertEquals(200, response.statusCode());
            response.body().discard();
        }
    }

    @Test
    void connectionPoolFactoryReceivesClientListeners() throws IOException {
        var listener = new HttpClientListener() {};
        var sawListener = new AtomicBoolean();
        var pool = new TestConnectionPool();

        try (var client = HttpClient.builder()
                .addListener(listener)
                .connectionPoolFactory(config -> {
                    sawListener.set(config.listeners().contains(listener));
                    return pool;
                })
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            client.send(request).body().discard();

            assertTrue(sawListener.get(), "Custom pool factory must receive the client listener config");
        }
    }

    @Test
    void requestEndFiresOnExchangeCreationFailure() throws IOException {
        var starts = new AtomicInteger();
        var ends = new AtomicInteger();
        var endedError = new AtomicReference<Throwable>();
        var startedExchangeId = new AtomicLong();
        var endedExchangeId = new AtomicLong();
        var listener = new HttpClientListener() {
            @Override
            public void onRequestStart(long exchangeId, HttpRequest request) {
                starts.incrementAndGet();
                startedExchangeId.set(exchangeId);
            }

            @Override
            public void onRequestEnd(long exchangeId, Throwable error) {
                ends.incrementAndGet();
                endedExchangeId.set(exchangeId);
                endedError.set(error);
            }
        };
        var pool = new TestConnectionPool() {
            @Override
            public HttpConnection acquire(Route route, long exchangeId, RequestOptions options) {
                return new TestConnection() {
                    @Override
                    public HttpExchange newExchange(HttpRequest request, RequestOptions options) throws IOException {
                        throw new IOException("exchange creation failed");
                    }
                };
            }
        };

        try (var client = HttpClient.builder()
                .connectionPoolFactory(config -> pool)
                .addListener(listener)
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            assertThrows(IOException.class, () -> client.send(request));

            assertEquals(1, starts.get());
            assertEquals(1, ends.get());
            assertEquals(startedExchangeId.get(), endedExchangeId.get());
            assertEquals("exchange creation failed", endedError.get().getMessage());
        }
    }

    @Test
    void proxyFallbackKeepsSingleExchangeId() throws IOException {
        var startedExchangeId = new AtomicLong();
        var endedExchangeId = new AtomicLong();
        var acquiredExchangeIds = new ArrayList<Long>();
        var listener = new HttpClientListener() {
            @Override
            public void onRequestStart(long exchangeId, HttpRequest request) {
                startedExchangeId.set(exchangeId);
            }

            @Override
            public void onRequestEnd(long exchangeId, Throwable error) {
                endedExchangeId.set(exchangeId);
            }
        };
        var pool = new TestConnectionPool() {
            @Override
            public HttpConnection acquire(Route route, long exchangeId, RequestOptions options) {
                acquiredExchangeIds.add(exchangeId);
                if (route.proxy() != null && route.proxy().port() == 8080) {
                    return new TestConnection() {
                        @Override
                        public HttpExchange newExchange(HttpRequest request, RequestOptions options)
                                throws IOException {
                            throw new IOException("first proxy failed");
                        }
                    };
                }
                return super.acquire(route, exchangeId, options);
            }
        };
        var proxy1 = new ProxyConfiguration(SmithyUri.of("http://proxy1.example.com:8080"),
                ProxyConfiguration.ProxyType.HTTP);
        var proxy2 = new ProxyConfiguration(SmithyUri.of("http://proxy2.example.com:9090"),
                ProxyConfiguration.ProxyType.HTTP);

        try (var client = HttpClient.builder()
                .connectionPoolFactory(config -> pool)
                .proxySelector(ProxySelector.of(proxy1, proxy2))
                .addListener(listener)
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);
            response.body().discard();

            assertEquals(2, acquiredExchangeIds.size());
            assertEquals(startedExchangeId.get(), acquiredExchangeIds.get(0));
            assertEquals(startedExchangeId.get(), acquiredExchangeIds.get(1));
            assertEquals(startedExchangeId.get(), endedExchangeId.get());
        }
    }

    @Test
    void proxyFallbackDoesNotEndExchangeOnPerRouteFailure() throws IOException {
        // Regression: the first proxy fails AFTER the exchange is created (mid response-read), then the
        // second proxy succeeds. onRequestEnd must fire exactly once, with no error, when the successful
        // response body is closed — not prematurely with the first proxy's failure.
        var ends = new ArrayList<Throwable>();
        var listener = new HttpClientListener() {
            @Override
            public void onRequestEnd(long exchangeId, Throwable error) {
                ends.add(error);
            }
        };
        var pool = new TestConnectionPool() {
            @Override
            public HttpConnection acquire(Route route, long exchangeId, RequestOptions options) {
                if (route.proxy() != null && route.proxy().port() == 8080) {
                    return new TestConnection() {
                        @Override
                        public HttpExchange newExchange(HttpRequest request, RequestOptions options) {
                            return new TestHttpExchange() {
                                @Override
                                public int responseStatusCode() throws IOException {
                                    throw new IOException("first proxy failed mid-response");
                                }
                            };
                        }
                    };
                }
                return super.acquire(route, exchangeId, options);
            }
        };
        var proxy1 = new ProxyConfiguration(SmithyUri.of("http://proxy1.example.com:8080"),
                ProxyConfiguration.ProxyType.HTTP);
        var proxy2 = new ProxyConfiguration(SmithyUri.of("http://proxy2.example.com:9090"),
                ProxyConfiguration.ProxyType.HTTP);
        try (var client = HttpClient.builder()
                .connectionPoolFactory(config -> pool)
                .proxySelector(ProxySelector.of(proxy1, proxy2))
                .addListener(listener)
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);
            assertTrue(ends.isEmpty(), "onRequestEnd must not fire while a later proxy may still succeed");

            response.body().discard();

            assertEquals(1, ends.size(), "onRequestEnd must fire exactly once");
            assertEquals(null, ends.get(0), "Successful request must end with no error");
        }
    }

    @Test
    void requestEndFiresOnceOnTimeout() throws IOException {
        // Regression: a timeout is observed on the caller thread, not the worker VT. onRequestEnd must
        // still fire exactly once with the timeout error.
        var ends = new ArrayList<Throwable>();
        var listener = new HttpClientListener() {
            @Override
            public void onRequestEnd(long exchangeId, Throwable error) {
                synchronized (ends) {
                    ends.add(error);
                }
            }
        };
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
                .connectionPoolFactory(config -> pool)
                .addListener(listener)
                .requestTimeout(Duration.ofMillis(50))
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var ex = assertThrows(IOException.class, () -> client.send(request));
            assertTrue(ex.getMessage().contains("exceeded request timeout"), ex.getMessage());

            synchronized (ends) {
                assertEquals(1, ends.size(), "onRequestEnd must fire exactly once on timeout");
                assertTrue(ends.get(0) instanceof IOException, "End error should be the timeout IOException");
            }
        }
    }

    @Test
    void listenerErrorDoesNotLeakOrFailRequest() throws IOException {
        // Regression: listener isolation must catch Throwable, not just RuntimeException. An Error thrown
        // from a callback must not propagate to the caller or skip connection cleanup.
        var released = new AtomicBoolean(false);
        var listener = new HttpClientListener() {
            @Override
            public void onRequestStart(long exchangeId, HttpRequest request) {
                throw new AssertionError("boom");
            }
        };
        var pool = new TestConnectionPool() {
            @Override
            public void release(HttpConnection connection) {
                released.set(true);
            }
        };
        try (var client = HttpClient.builder()
                .connectionPoolFactory(config -> pool)
                .addListener(listener)
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);
            assertEquals(200, response.statusCode(), "Listener Error must not fail the request");
            response.body().discard();
            assertTrue(released.get(), "Connection must still be released despite the listener Error");
        }
    }

    @Test
    void proxyFallbackContinuesPastUnsupportedSocksProxy() throws IOException {
        // Regression: an unsupported (SOCKS) proxy is a per-route failure surfaced as an IOException, so the
        // proxy loop must run connectFailed and try the next proxy rather than aborting the whole send.
        // The real HttpConnectionFactory throws IOException for SOCKS; this models that at the pool boundary.
        var socksAttempted = new AtomicBoolean(false);
        var httpAttempted = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            public HttpConnection acquire(Route route, long exchangeId, RequestOptions options) {
                if (route.proxy() != null && route.proxy().type() == ProxyConfiguration.ProxyType.SOCKS5) {
                    socksAttempted.set(true);
                    return new TestConnection() {
                        @Override
                        public HttpExchange newExchange(HttpRequest request, RequestOptions options)
                                throws IOException {
                            throw new IOException("SOCKS proxies not yet supported: SOCKS5");
                        }
                    };
                }
                httpAttempted.set(true);
                return super.acquire(route, exchangeId, options);
            }
        };
        var socks = new ProxyConfiguration(SmithyUri.of("http://socks.example.com:1080"),
                ProxyConfiguration.ProxyType.SOCKS5);
        var http = new ProxyConfiguration(SmithyUri.of("http://http.example.com:8080"),
                ProxyConfiguration.ProxyType.HTTP);
        var connectFailedFor = new ArrayList<ProxyConfiguration>();
        var selector = new ProxySelector() {
            @Override
            public List<ProxyConfiguration> select(SmithyUri target) {
                return List.of(socks, http);
            }

            @Override
            public void connectFailed(SmithyUri target, ProxyConfiguration proxy, IOException cause) {
                connectFailedFor.add(proxy);
            }
        };
        try (var client = HttpClient.builder()
                .connectionPoolFactory(config -> pool)
                .proxySelector(selector)
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);

            assertEquals(200, response.statusCode(), "Should succeed via the HTTP proxy after SOCKS fails");
            assertTrue(socksAttempted.get(), "SOCKS proxy should have been attempted first");
            assertTrue(httpAttempted.get(), "HTTP proxy should be tried after the SOCKS attempt fails");
            assertEquals(List.of(socks), connectFailedFor, "connectFailed should fire for the SOCKS proxy");
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
        try (var client = HttpClient.builder().connectionPoolFactory(config -> pool).build()) {
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
                .connectionPoolFactory(config -> pool)
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
                .connectionPoolFactory(config -> pool)
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
            public HttpConnection acquire(Route route, long exchangeId, RequestOptions options) {
                if (route.usesProxy()) {
                    proxyUsed.set(true);
                }
                return super.acquire(route, exchangeId, options);
            }
        };
        var proxy = new ProxyConfiguration(SmithyUri.of("http://proxy.example.com:8080"),
                ProxyConfiguration.ProxyType.HTTP);
        try (var client = HttpClient.builder()
                .connectionPoolFactory(config -> pool)
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
            public HttpConnection acquire(Route route, long exchangeId, RequestOptions options) {
                attemptedProxies.incrementAndGet();
                if (route.proxy() != null && route.proxy().port() == 8080) {
                    return new TestConnection() {
                        @Override
                        public HttpExchange newExchange(HttpRequest request, RequestOptions options)
                                throws IOException {
                            throw new IOException("first proxy failed");
                        }
                    };
                }
                return super.acquire(route, exchangeId, options);
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
                .connectionPoolFactory(config -> pool)
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
            public HttpConnection acquire(Route route, long exchangeId, RequestOptions options) {
                attemptedProxies.incrementAndGet();
                return new TestConnection() {
                    @Override
                    public HttpExchange newExchange(HttpRequest request, RequestOptions options) throws IOException {
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
                .connectionPoolFactory(config -> pool)
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
            public HttpConnection acquire(Route route, long exchangeId, RequestOptions options) {
                return new TestConnection() {
                    @Override
                    public HttpExchange newExchange(HttpRequest request, RequestOptions options) throws IOException {
                        throw new IOException("exchange creation failed");
                    }
                };
            }

            @Override
            public void evict(HttpConnection connection, boolean close) {
                evicted.set(true);
            }
        };
        try (var client = HttpClient.builder().connectionPoolFactory(config -> pool).build()) {
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
                    public HttpHeaders responseHeaders() {
                        return HttpHeaders.of(Map.of("content-length", List.of("9")));
                    }
                };
            }

            @Override
            public void release(HttpConnection connection) {
                released.set(true);
            }
        };
        try (var client = HttpClient.builder().connectionPoolFactory(config -> pool).build()) {
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
        try (var client = HttpClient.builder().connectionPoolFactory(config -> pool).build()) {
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
        public HttpConnection acquire(Route route, long exchangeId, RequestOptions options) {
            return new TestConnection() {
                @Override
                public HttpExchange newExchange(HttpRequest request, RequestOptions options) {
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
        public HttpExchange newExchange(HttpRequest request, RequestOptions options) throws IOException {
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

    @Test
    void closingOpenedChannelClosesChannelNotDiscard() throws IOException {
        // Regression for drainOrDiscardBody: when the caller opened the body as a channel, teardown must
        // close that channel, NOT call discardResponseBody (the "never opened" branch). Guards the
        // wrappedChannel != null vs == null distinction.
        var channelClosed = new AtomicBoolean(false);
        var discardCalled = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            protected HttpExchange createExchange() {
                return new TestHttpExchange() {
                    @Override
                    public ReadableByteChannel responseBodyChannel() {
                        return new ReadableByteChannel() {
                            private final ReadableByteChannel inner =
                                    Channels.newChannel(new ByteArrayInputStream("test-body".getBytes()));

                            @Override
                            public int read(ByteBuffer dst) throws IOException {
                                return inner.read(dst);
                            }

                            @Override
                            public boolean isOpen() {
                                return inner.isOpen();
                            }

                            @Override
                            public void close() throws IOException {
                                channelClosed.set(true);
                                inner.close();
                            }
                        };
                    }

                    @Override
                    public void discardResponseBody() {
                        discardCalled.set(true);
                    }
                };
            }
        };
        try (var client = HttpClient.builder().connectionPoolFactory(config -> pool).build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);
            response.body().asChannel(); // opens the channel, sets wrappedChannel
            response.body().close();

            assertTrue(channelClosed.get(), "Opened channel must be closed on teardown");
            assertFalse(discardCalled.get(), "discardResponseBody must not run when a channel was opened");
        }
    }

    @Test
    void discardWithoutOpeningBodyDiscardsAtExchange() throws IOException {
        // Regression for drainOrDiscardBody: when the body was never opened, teardown must discard at the
        // exchange level (the final else branch), not touch a stream or channel.
        var discardCalled = new AtomicBoolean(false);
        var released = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            protected HttpExchange createExchange() {
                return new TestHttpExchange() {
                    @Override
                    public void discardResponseBody() {
                        discardCalled.set(true);
                    }
                };
            }

            @Override
            public void release(HttpConnection connection) {
                released.set(true);
            }
        };
        try (var client = HttpClient.builder().connectionPoolFactory(config -> pool).build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);
            response.body().discard(); // never opened the body

            assertTrue(discardCalled.get(), "Unopened body must be discarded at the exchange level");
            assertTrue(released.get(), "Connection must be released after a clean discard");
        }
    }

    /** The body-consumption methods whose read failure must route through the error terminal. */
    static Stream<Arguments> failingBodyConsumers() {
        return Stream.of(
                Arguments.of("asInputStream.readAllBytes",
                        (BodyConsumer) body -> body.asInputStream().readAllBytes()),
                Arguments.of("asInputStream.read",
                        (BodyConsumer) body -> body.asInputStream().read()),
                Arguments.of("asChannel.read",
                        (BodyConsumer) body -> body.asChannel().read(ByteBuffer.allocate(16))),
                Arguments.of("writeTo.outputStream",
                        (BodyConsumer) body -> body.writeTo(OutputStream.nullOutputStream())),
                Arguments.of("writeTo.channel",
                        (BodyConsumer) body -> body.writeTo(Channels.newChannel(OutputStream.nullOutputStream()))));
    }

    @FunctionalInterface
    interface BodyConsumer {
        void consume(DataStream body) throws IOException;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failingBodyConsumers")
    void bodyReadFailureEndsWithErrorAndEvicts(String name, BodyConsumer consumer) throws IOException {
        // Regression for the error-terminal routing across every consumption path (input stream, channel,
        // and both writeTo variants): a body read that throws must fire onRequestEnd WITH the error and
        // evict the torn connection — not report a clean close (onRequestEnd(null)) and pool it.
        var endedError = new AtomicReference<Throwable>();
        var ends = new AtomicInteger();
        var evicted = new AtomicBoolean(false);
        var released = new AtomicBoolean(false);
        var boom = new IOException("read blew up");
        var listener = new HttpClientListener() {
            @Override
            public void onRequestEnd(long exchangeId, Throwable error) {
                ends.incrementAndGet();
                endedError.set(error);
            }
        };
        var pool = new TestConnectionPool() {
            @Override
            protected HttpExchange createExchange() {
                return new TestHttpExchange() {
                    @Override
                    public InputStream responseBody() {
                        return new InputStream() {
                            @Override
                            public int read() throws IOException {
                                throw boom;
                            }

                            @Override
                            public int read(byte[] b, int off, int len) throws IOException {
                                throw boom;
                            }
                        };
                    }

                    @Override
                    public ReadableByteChannel responseBodyChannel() {
                        return new ReadableByteChannel() {
                            @Override
                            public int read(ByteBuffer dst) throws IOException {
                                throw boom;
                            }

                            @Override
                            public boolean isOpen() {
                                return true;
                            }

                            @Override
                            public void close() {}
                        };
                    }
                };
            }

            @Override
            public void evict(HttpConnection connection, boolean isError) {
                evicted.set(true);
            }

            @Override
            public void release(HttpConnection connection) {
                released.set(true);
            }
        };
        try (var client = HttpClient.builder()
                .connectionPoolFactory(config -> pool)
                .addListener(listener)
                .build()) {
            var request = HttpRequest.create()
                    .setMethod("GET")
                    .setUri(SmithyUri.of("http://example.com/test"));

            var response = client.send(request);
            assertThrows(IOException.class, () -> consumer.consume(response.body()));

            assertEquals(1, ends.get(), name + ": onRequestEnd must fire exactly once");
            assertEquals(boom, endedError.get(), name + ": onRequestEnd must carry the read failure, not null");
            assertTrue(evicted.get(), name + ": a torn connection must be evicted");
            assertFalse(released.get(), name + ": a torn connection must NOT be released to the pool");
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
