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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.aws.client.awsjson.AwsJson1Protocol;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.endpoint.EndpointResolver;
import software.amazon.smithy.java.core.schema.Schema;
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
        builder.endpointResolver(EndpointResolver.staticEndpoint(new URI("localhost:8080")));
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
        var fakeResponse = new HttpResponse<InputStream>() {
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
            public InputStream body() {
                return new ByteArrayInputStream(new byte[0]);
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
            public Optional<HttpResponse<InputStream>> previousResponse() {
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

        assertFalse(response.headers().map().containsKey(":status"),
                "Response headers should not contain :status pseudo-header");
        assertThat(response.headers().firstValue("content-type"), equalTo("application/json"));
    }
}
