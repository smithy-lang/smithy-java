/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.client.core.CallContext;
import software.amazon.smithy.java.client.core.SmithyUserAgent;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public class JavaHttpClientTest {

    private static final Model MODEL = Model.assembler()
        .addUnparsedModel("test.smithy", """
            $version: "2"
            namespace smithy.example

            service Sprockets {
                operations: [GetSprocket]
            }

            operation GetSprocket {}
            """)
        .assemble()
        .unwrap();

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
    public void addsUserAgentHeaderByDefault() {
        var result = callAndGetHeader(null, null);

        assertThat(result, hasSize(1));
        var value = result.get(0);

        assertThat(value, startsWith("smithy-java/"));
        assertThat(value, containsString("ua/2.1"));
        assertThat(value, containsString("lang/java#"));
    }

    @Test
    public void usesDefaultUserAgent() {
        var ua = SmithyUserAgent.create();
        ua.setTool("my-client", "1.0");
        var result = callAndGetHeader(null, ua);

        assertThat(result, hasSize(1));
        var value = result.get(0);

        assertThat(value, startsWith("my-client/1.0 "));
        assertThat(value, containsString("smithy-java/"));
        assertThat(value, containsString("ua/2.1"));
        assertThat(value, containsString("lang/java#"));
    }

    @Test
    public void doesNotOverwriteExistingHeader() {
        var result = callAndGetHeader("Foo", null);

        assertThat(result, hasSize(1));
        var value = result.get(0);

        assertThat(value, equalTo("Foo"));
    }

    private List<String> callAndGetHeader(String defaultHeader, SmithyUserAgent defaultUa) {
        AtomicReference<List<String>> ref = new AtomicReference<>();
        var service = ShapeId.from("smithy.example#Sprockets");
        var client = DynamicClient.builder()
            .service(service)
            .model(MODEL)
            .authSchemeResolver(AuthSchemeResolver.NO_AUTH)
            .protocol(new AwsJson1Protocol(service))
            .putConfig(CallContext.USER_AGENT, defaultUa)
            .transport(new JavaHttpClientTransport(new HttpClient() {
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

                @Override
                public <T> HttpResponse<T> send(
                    java.net.http.HttpRequest request,
                    HttpResponse.BodyHandler<T> responseBodyHandler
                ) throws IOException, InterruptedException {
                    throw new UnsupportedOperationException();
                }

                @Override
                public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                    java.net.http.HttpRequest request,
                    HttpResponse.BodyHandler<T> responseBodyHandler
                ) {
                    ref.set(request.headers().allValues("user-agent"));
                    return CompletableFuture.completedFuture(new HttpResponse<T>() {
                        @Override
                        public int statusCode() {
                            return 200;
                        }

                        @Override
                        public java.net.http.HttpRequest request() {
                            return request;
                        }

                        @Override
                        public Optional<HttpResponse<T>> previousResponse() {
                            return Optional.empty();
                        }

                        @Override
                        public HttpHeaders headers() {
                            return HttpHeaders.of(Map.of(), (a, b) -> true);
                        }

                        @Override
                        @SuppressWarnings("unchecked")
                        public T body() {
                            return (T) DataStream.ofEmpty(); // we know this is always what's requested
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
                        public Version version() {
                            return Version.HTTP_1_1;
                        }
                    });
                }

                @Override
                public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                    java.net.http.HttpRequest request,
                    HttpResponse.BodyHandler<T> responseBodyHandler,
                    HttpResponse.PushPromiseHandler<T> pushPromiseHandler
                ) {
                    throw new UnsupportedOperationException();
                }
            }))
            .endpointResolver(EndpointResolver.staticEndpoint("https://foo.com"))
            .addInterceptor(new ClientInterceptor() {
                @Override
                public <RequestT> RequestT modifyBeforeSigning(RequestHook<?, ?, RequestT> hook) {
                    return hook.mapRequest(
                        software.amazon.smithy.java.http.api.HttpRequest.class,
                        defaultHeader,
                        (h, dh) -> {
                            if (dh == null) {
                                return h.request();
                            }
                            return h.request().toBuilder().withReplacedHeader("user-agent", List.of(dh)).build();
                        }
                    );
                }
            })
            .build();

        client.call("GetSprocket");

        return ref.get();
    }
}
