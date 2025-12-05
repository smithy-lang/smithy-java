/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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

class ManagedHttpExchangeTest {

    @Test
    void releasesConnectionOnSuccessfulClose() throws IOException {
        var released = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            public void release(HttpConnection conn) {
                released.set(true);
            }
        };
        var exchange = createExchange(pool, List.of());

        exchange.responseBody().close();

        assertTrue(released.get());
    }

    @Test
    void evictsConnectionOnError() throws IOException {
        var evicted = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            public void evict(HttpConnection conn, boolean close) {
                evicted.set(true);
            }
        };
        var delegate = new FailingHttpExchange();
        var exchange = createExchange(pool, List.of(), delegate);

        try {
            exchange.responseStatusCode();
        } catch (IOException ignored) {}

        try {
            exchange.close();
        } catch (IOException ignored) {}

        assertTrue(evicted.get());
    }

    @Test
    void closingResponseBodyClosesExchange() throws IOException {
        var closed = new AtomicBoolean(false);
        var delegate = new TestHttpExchange() {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        var exchange = createExchange(new TestConnectionPool(), List.of(), delegate);

        exchange.responseBody().close();

        assertTrue(closed.get());
    }

    @Test
    void closeIsIdempotent() throws IOException {
        var releaseCount = new AtomicInteger(0);
        var pool = new TestConnectionPool() {
            @Override
            public void release(HttpConnection conn) {
                releaseCount.incrementAndGet();
            }
        };
        var exchange = createExchange(pool, List.of());

        exchange.close();
        exchange.close();
        exchange.close();

        assertEquals(1, releaseCount.get());
    }

    @Test
    void interceptorCanReplaceResponse() throws IOException {
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
        var exchange = createExchange(new TestConnectionPool(), List.of(interceptor));

        assertEquals(999, exchange.responseStatusCode());
        assertEquals("intercepted", new String(exchange.responseBody().readAllBytes()));
    }

    @Test
    void responseBodyReturnsSameStream() throws IOException {
        var exchange = createExchange(new TestConnectionPool(), List.of());

        var body1 = exchange.responseBody();
        var body2 = exchange.responseBody();

        assertSame(body1, body2);
    }

    @Test
    void drainsResponseBodyWhenInterceptorReplacesWithoutReadingOriginal() throws IOException {
        var drained = new AtomicBoolean(false);
        var delegate = new TestHttpExchange() {
            @Override
            public InputStream responseBody() {
                return new ByteArrayInputStream("original".getBytes()) {
                    @Override
                    public long transferTo(OutputStream out) throws IOException {
                        drained.set(true);
                        return super.transferTo(out);
                    }
                };
            }
        };

        // Interceptor replaces response without reading original body
        var interceptor = new HttpInterceptor() {
            @Override
            public HttpResponse interceptResponse(
                    HttpClient client,
                    HttpRequest request,
                    Context context,
                    HttpResponse response
            ) {
                // Replace without reading response.body()
                return HttpResponse.builder()
                        .statusCode(999)
                        .body(DataStream.ofString("replaced"))
                        .build();
            }
        };

        var released = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            public void release(HttpConnection conn) {
                released.set(true);
            }
        };

        var exchange = createExchange(pool, List.of(interceptor), delegate);

        // Access status (triggers interception) but don't call responseBody()
        assertEquals(999, exchange.responseStatusCode());
        exchange.close();

        assertTrue(drained.get(), "Original response body should be drained");
        assertTrue(released.get(), "Connection should be released");
    }

    @Test
    void drainsOriginalBodyWhenInterceptorReplacesAndCallerReadsReplacement() throws IOException {
        var originalDrained = new AtomicBoolean(false);
        var delegate = new TestHttpExchange() {
            @Override
            public InputStream responseBody() {
                return new ByteArrayInputStream("original".getBytes()) {
                    @Override
                    public long transferTo(OutputStream out) throws IOException {
                        originalDrained.set(true);
                        return super.transferTo(out);
                    }
                };
            }
        };

        // Interceptor replaces response without reading original body
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
                        .body(DataStream.ofString("replaced"))
                        .build();
            }
        };

        var released = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            public void release(HttpConnection conn) {
                released.set(true);
            }
        };
        var exchange = createExchange(pool, List.of(interceptor), delegate);

        // Caller reads the replacement body
        assertEquals("replaced", new String(exchange.responseBody().readAllBytes()));
        exchange.close();

        assertTrue(originalDrained.get(),
                "Original response body should be drained even when caller reads replacement");
        assertTrue(released.get(), "Connection should be released");
    }

    @Test
    void drainsResponseBodyWhenOnlyHeadersAccessedWithInterceptors() throws IOException {
        var bodyDrained = new AtomicBoolean(false);
        var delegate = new TestHttpExchange() {
            @Override
            public InputStream responseBody() {
                return new ByteArrayInputStream("body".getBytes()) {
                    @Override
                    public long transferTo(OutputStream out) throws IOException {
                        bodyDrained.set(true);
                        return super.transferTo(out);
                    }
                };
            }
        };

        // Pass-through interceptor that doesn't consume body
        var interceptor = new HttpInterceptor() {};
        var released = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            public void release(HttpConnection conn) {
                released.set(true);
            }
        };

        var exchange = createExchange(pool, List.of(interceptor), delegate);

        // Only access headers, never call responseBody()
        exchange.responseHeaders();
        exchange.close();

        assertTrue(bodyDrained.get(), "Response body should be drained even if not accessed");
        assertTrue(released.get(), "Connection should be released");
    }

    @Test
    void doesNotDrainIfResponseNeverAccessed() throws IOException {
        var bodyAccessed = new AtomicBoolean(false);
        var delegate = new TestHttpExchange() {
            @Override
            public InputStream responseBody() {
                bodyAccessed.set(true);
                return super.responseBody();
            }
        };

        var released = new AtomicBoolean(false);
        var pool = new TestConnectionPool() {
            @Override
            public void release(HttpConnection conn) {
                released.set(true);
            }
        };

        var exchange = createExchange(pool, List.of(), delegate);

        // Close without accessing response at all
        exchange.close();

        // Body should not be accessed since response was never read
        assertFalse(bodyAccessed.get(), "Response body should not be accessed if response never read");
        assertTrue(released.get(), "Connection should still be released");
    }

    private ManagedHttpExchange createExchange(ConnectionPool pool, List<HttpInterceptor> interceptors) {
        return createExchange(pool, interceptors, new TestHttpExchange());
    }

    private ManagedHttpExchange createExchange(
            ConnectionPool pool,
            List<HttpInterceptor> interceptors,
            HttpExchange delegate
    ) {
        var request = HttpRequest.builder()
                .method("GET")
                .uri(URI.create("http://example.com"))
                .build();
        return new ManagedHttpExchange(
                delegate,
                new TestConnection(),
                pool,
                request,
                Context.create(),
                interceptors,
                null);
    }

    private static class TestHttpExchange implements HttpExchange {
        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public OutputStream requestBody() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream responseBody() {
            return new ByteArrayInputStream("test".getBytes());
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

    private static class FailingHttpExchange extends TestHttpExchange {
        @Override
        public int responseStatusCode() throws IOException {
            throw new IOException("test error");
        }
    }

    private static class TestConnection implements HttpConnection {
        @Override
        public HttpExchange newExchange(HttpRequest request) {
            return null;
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

    private static class TestConnectionPool implements ConnectionPool {
        @Override
        public HttpConnection acquire(Route route) {
            return null;
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
}
