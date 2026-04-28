/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.shapes.ShapeId;

public class JavaHttpClientTest {

    private static final String NAME = "jdk.httpclient.allowRestrictedHeaders";
    private static String originalValue;

    @BeforeAll
    public static void init() {
        originalValue = System.getProperty(NAME, "");
    }

    @AfterAll
    public static void cleanup() {
        System.setProperty(NAME, originalValue);
    }

    @Test
    public void setsHostInAllowedHeaders() {
        System.setProperty(NAME, "");
        new JavaHttpClientTransport();

        assertThat(System.getProperty(NAME), equalTo("host"));
    }

    @Test
    public void setsHostInAllowedHeadersWhenOtherValuesPresent() {
        System.setProperty(NAME, "foo");
        new JavaHttpClientTransport();

        assertThat(System.getProperty(NAME), equalTo("foo,host"));
    }

    @Test
    public void doesNotSetHostWhenIsolated() {
        System.setProperty(NAME, "host");
        new JavaHttpClientTransport();

        assertThat(System.getProperty(NAME), equalTo("host"));
    }

    @Test
    public void doesNotSetHostWhenTrailing() {
        System.setProperty(NAME, "foo,host");
        new JavaHttpClientTransport();

        assertThat(System.getProperty(NAME), equalTo("foo,host"));
    }

    @Test
    public void doesNotSetHostWhenLeading() {
        System.setProperty(NAME, "Host,foo");
        new JavaHttpClientTransport();

        assertThat(System.getProperty(NAME), equalTo("Host,foo"));
    }

    @Test
    public void automaticallyAppliesUserAgentPlugin() throws URISyntaxException {
        var builder = ClientConfig.builder();
        builder.protocol(new AwsJson1Protocol(ShapeId.from("foo#Bar")));
        builder.transport(new JavaHttpClientTransport());
        builder.endpointResolver(EndpointResolver.staticEndpoint(new URI("http://localhost:8080")));
        var serviceSchema = Schema.createService(ShapeId.from("foo#Bar"));
        builder.service(() -> serviceSchema);
        var config = builder.build();

        assertThat(config.interceptors(), not(empty()));
        // the interceptor is package-private, so this check will suffice.
        assertThat(config.interceptors().toString(), containsString("UserAgentPlugin"));
    }

    @Test
    public void filtersStatusPseudoHeaderFromResponse() throws Exception {
        // Create a fake Java HttpResponse with :status pseudo-header
        var fakeResponse = new HttpResponse<DataStream>() {
            @Override
            public int statusCode() {
                return 200;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(
                        Map.of(":status", List.of("200"), "content-type", List.of("application/json")),
                        (k, v) -> true);
            }

            @Override
            public DataStream body() {
                return DataStream.ofEmpty();
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_2;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<DataStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("http://localhost/test");
            }
        };

        var transport = new JavaHttpClientTransport();
        var response = transport.createSmithyResponse(fakeResponse);

        assertInstanceOf(JavaHttpResponse.class, response);
        assertInstanceOf(JavaHttpHeaders.class, response.headers());
        assertFalse(response.headers().map().containsKey(":status"),
                "Response headers should not contain :status pseudo-header");
        assertThat(response.headers().firstValue("content-type"), equalTo("application/json"));
    }

    @Test
    public void usesPublisherFastPathForKnownLengthBodies() {
        byte[] payload = new byte[128 * 1024];
        Arrays.fill(payload, (byte) 'a');
        var client = new CapturingHttpClient();
        var transport = new JavaHttpClientTransport(client);
        var request = software.amazon.smithy.java.http.api.HttpRequest.create()
                .setUri(URI.create("http://localhost/test"))
                .setMethod("POST")
                .setBody(new DataStream() {
                    @Override
                    public long contentLength() {
                        return payload.length;
                    }

                    @Override
                    public String contentType() {
                        return null;
                    }

                    @Override
                    public boolean isReplayable() {
                        return true;
                    }

                    @Override
                    public boolean isAvailable() {
                        return true;
                    }

                    @Override
                    public InputStream asInputStream() {
                        throw new AssertionError("asInputStream should not be called");
                    }

                    @Override
                    public ByteBuffer asByteBuffer() {
                        return ByteBuffer.wrap(payload);
                    }

                    @Override
                    public boolean hasByteBuffer() {
                        return true;
                    }

                    @Override
                    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
                        throw new AssertionError("subscribe should not be called");
                    }
                })
                .toUnmodifiable();

        try (var response = transport.send(Context.create(), request)) {
            assertThat(response.statusCode(), equalTo(200));
        }

        assertThat(client.capturedBytes, equalTo(payload));
    }

    @Test
    public void streamsResponseBodyThroughCustomBodySubscriber() throws Exception {
        byte[] payload = "streamed response body".getBytes();
        var client = new StreamingResponseHttpClient(payload);
        var transport = new JavaHttpClientTransport(client);
        var request = software.amazon.smithy.java.http.api.HttpRequest.create()
                .setUri(URI.create("http://localhost/test"))
                .setMethod("GET")
                .toUnmodifiable();

        try (var response = transport.send(Context.create(), request);
                var body = response.body().asInputStream()) {
            assertThat(response.statusCode(), equalTo(200));
            assertThat(body.readAllBytes(), equalTo(payload));
        }
    }

    @Test
    public void preservesExplicitHttp11RequestVersionOnHttp2Client() {
        var client = new CapturingHttpClient() {
            @Override
            public Version version() {
                return Version.HTTP_2;
            }
        };
        var transport = new JavaHttpClientTransport(client);
        var request = software.amazon.smithy.java.http.api.HttpRequest.create()
                .setUri(URI.create("http://localhost/test"))
                .setMethod("GET")
                .setHttpVersion(software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_1)
                .toUnmodifiable();

        try (var response = transport.send(Context.create(), request)) {
            assertThat(response.statusCode(), equalTo(200));
        }

        assertThat(client.capturedVersion().orElseThrow(), equalTo(HttpClient.Version.HTTP_1_1));
    }

    @Test
    public void preservesReplayabilityForSmallResponseBodies() throws Exception {
        var fakeResponse = new HttpResponse<DataStream>() {
            @Override
            public int statusCode() {
                return 200;
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(
                        Map.of(
                                "content-type",
                                List.of("text/plain"),
                                "content-length",
                                List.of("5")),
                        (k, v) -> true);
            }

            @Override
            public DataStream body() {
                return DataStream.ofBytes("hello".getBytes());
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_2;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<DataStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("http://localhost/test");
            }
        };

        var transport = new JavaHttpClientTransport();
        var response = transport.createSmithyResponse(fakeResponse);

        assertTrue(response.body().isReplayable());
        assertThat(response.body().asInputStream().readAllBytes(), equalTo("hello".getBytes()));
        assertThat(response.body().asInputStream().readAllBytes(), equalTo("hello".getBytes()));
    }

    private static class CapturingHttpClient extends HttpClient {
        private byte[] capturedBytes = new byte[0];
        private Optional<HttpClient.Version> capturedVersion = Optional.empty();

        Optional<HttpClient.Version> capturedVersion() {
            return capturedVersion;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            capturedBytes = collect(request.bodyPublisher().orElseThrow());
            capturedVersion = request.version();
            var responseInfo = new HttpResponse.ResponseInfo() {
                @Override
                public int statusCode() {
                    return 200;
                }

                @Override
                public HttpHeaders headers() {
                    return HttpHeaders.of(Map.of(), (k, v) -> true);
                }

                @Override
                public HttpClient.Version version() {
                    return HttpClient.Version.HTTP_1_1;
                }
            };
            var subscriber = responseBodyHandler.apply(responseInfo);
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {}
            });
            T body = subscriber.getBody().toCompletableFuture().join();
            return new HttpResponse<>() {
                @Override
                public int statusCode() {
                    return 200;
                }

                @Override
                public HttpHeaders headers() {
                    return responseInfo.headers();
                }

                @Override
                public T body() {
                    return body;
                }

                @Override
                public HttpClient.Version version() {
                    return responseInfo.version();
                }

                @Override
                public HttpRequest request() {
                    return request;
                }

                @Override
                public Optional<HttpResponse<T>> previousResponse() {
                    return Optional.empty();
                }

                @Override
                public Optional<SSLSession> sslSession() {
                    return Optional.empty();
                }

                @Override
                public URI uri() {
                    return request.uri();
                }
            };
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            return CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        private static byte[] collect(HttpRequest.BodyPublisher publisher) {
            var out = new java.io.ByteArrayOutputStream();
            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    ByteBuffer copy = item.duplicate();
                    byte[] bytes = new byte[copy.remaining()];
                    copy.get(bytes);
                    out.write(bytes, 0, bytes.length);
                }

                @Override
                public void onError(Throwable throwable) {
                    throw new AssertionError("BodyPublisher failed", throwable);
                }

                @Override
                public void onComplete() {}
            });
            return out.toByteArray();
        }
    }

    private static final class StreamingResponseHttpClient extends HttpClient {
        private final byte[] responseBytes;

        private StreamingResponseHttpClient(byte[] responseBytes) {
            this.responseBytes = responseBytes;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            var responseInfo = new HttpResponse.ResponseInfo() {
                @Override
                public int statusCode() {
                    return 200;
                }

                @Override
                public HttpHeaders headers() {
                    return HttpHeaders.of(
                            Map.of(
                                    "content-length",
                                    List.of(Integer.toString(responseBytes.length)),
                                    "content-type",
                                    List.of("text/plain")),
                            (k, v) -> true);
                }

                @Override
                public HttpClient.Version version() {
                    return HttpClient.Version.HTTP_2;
                }
            };

            var subscriber = responseBodyHandler.apply(responseInfo);
            subscriber.onSubscribe(new Flow.Subscription() {
                private boolean cancelled;

                @Override
                public void request(long n) {
                    if (cancelled) {
                        return;
                    }
                    var buffers = new ArrayList<ByteBuffer>(2);
                    buffers.add(ByteBuffer.wrap(responseBytes, 0, 8));
                    buffers.add(ByteBuffer.wrap(responseBytes, 8, responseBytes.length - 8));
                    subscriber.onNext((List) buffers);
                    subscriber.onComplete();
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });

            T body = subscriber.getBody().toCompletableFuture().join();
            return new HttpResponse<>() {
                @Override
                public int statusCode() {
                    return 200;
                }

                @Override
                public HttpRequest request() {
                    return request;
                }

                @Override
                public Optional<HttpResponse<T>> previousResponse() {
                    return Optional.empty();
                }

                @Override
                public HttpHeaders headers() {
                    return responseInfo.headers();
                }

                @Override
                public T body() {
                    return body;
                }

                @Override
                public Optional<SSLSession> sslSession() {
                    return Optional.empty();
                }

                @Override
                public URI uri() {
                    return request.uri();
                }

                @Override
                public HttpClient.Version version() {
                    return responseInfo.version();
                }
            };
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_2;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler
        ) {
            return CompletableFuture.completedFuture(send(request, responseBodyHandler));
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
