/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.HttpClient;
import software.amazon.smithy.java.http.client.HttpInterceptor;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPool;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.dns.DnsResolver;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.TextResponseHttp11ClientHandler;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Integration tests for HTTP interceptors.
 */
public class InterceptorIntegTest {

    private static final String RESPONSE_BODY = "Original response";
    private NettyTestServer server;
    private HttpClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = NettyTestServer.builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new TextResponseHttp11ClientHandler(RESPONSE_BODY))
                .build();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null)
            client.close();
        if (server != null)
            server.stop();
    }

    private HttpClient.Builder clientBuilder() {
        DnsResolver staticDns = DnsResolver.staticMapping(Map.of(
                "localhost",
                List.of(InetAddress.getLoopbackAddress())));
        return HttpClient.builder()
                .connectionPool(HttpConnectionPool.builder()
                        .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                        .maxConnectionsPerRoute(10)
                        .maxTotalConnections(10)
                        .maxIdleTime(Duration.ofMinutes(1))
                        .dnsResolver(staticDns)
                        .build());
    }

    @Test
    void beforeRequestInterceptorModifiesRequest() throws Exception {
        var interceptor = new HttpInterceptor() {
            @Override
            public HttpRequest beforeRequest(HttpClient client, HttpRequest request, Context context) {
                var headers = HttpHeaders.ofModifiable();
                for (var entry : request.headers()) {
                    for (var value : entry.getValue()) {
                        headers.addHeader(entry.getKey(), value);
                    }
                }
                headers.addHeader("x-custom-header", "intercepted");
                return request.toBuilder().headers(headers).build();
            }
        };

        client = clientBuilder().addInterceptor(interceptor).build();
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1,
                "http://localhost:" + server.getPort(),
                "");

        var response = client.send(request);

        assertEquals(200, response.statusCode());
    }

    @Test
    void interceptResponseModifiesResponse() throws Exception {
        var interceptor = new HttpInterceptor() {
            @Override
            public HttpResponse interceptResponse(
                    HttpClient client,
                    HttpRequest request,
                    Context context,
                    HttpResponse response
            ) {
                return HttpResponse.builder()
                        .statusCode(response.statusCode())
                        .headers(response.headers())
                        .body(DataStream.ofString("Modified by interceptor"))
                        .build();
            }
        };

        client = clientBuilder().addInterceptor(interceptor).build();
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1,
                "http://localhost:" + server.getPort(),
                "");

        var response = client.send(request);
        var body = readBody(response);

        assertEquals("Modified by interceptor", body);
    }

    @Test
    void multipleInterceptorsExecuteInOrder() throws Exception {
        var order = new StringBuilder();

        var first = new HttpInterceptor() {
            @Override
            public HttpRequest beforeRequest(HttpClient client, HttpRequest request, Context context) {
                order.append("1-before,");
                return request;
            }

            @Override
            public HttpResponse interceptResponse(
                    HttpClient client,
                    HttpRequest request,
                    Context context,
                    HttpResponse response
            ) {
                order.append("1-response,");
                return response;
            }
        };

        var second = new HttpInterceptor() {
            @Override
            public HttpRequest beforeRequest(HttpClient client, HttpRequest request, Context context) {
                order.append("2-before,");
                return request;
            }

            @Override
            public HttpResponse interceptResponse(
                    HttpClient client,
                    HttpRequest request,
                    Context context,
                    HttpResponse response
            ) {
                order.append("2-response,");
                return response;
            }
        };

        client = clientBuilder().addInterceptor(first).addInterceptor(second).build();
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1,
                "http://localhost:" + server.getPort(),
                "");

        client.send(request);

        // beforeRequest: forward order, interceptResponse: reverse order
        assertEquals("1-before,2-before,2-response,1-response,", order.toString());
    }

    @Test
    void preemptRequestSkipsNetworkCall() throws Exception {
        var preemptInterceptor = new HttpInterceptor() {
            @Override
            public HttpResponse preemptRequest(HttpClient client, HttpRequest request, Context context) {
                return HttpResponse.builder()
                        .statusCode(200)
                        .headers(HttpHeaders.ofModifiable())
                        .body(DataStream.ofString("Preempted"))
                        .build();
            }
        };

        client = clientBuilder()
                .addInterceptor(preemptInterceptor)
                .build();

        // Stop server - if network is called, it will fail
        server.stop();

        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1,
                "http://localhost:" + server.getPort(),
                "");

        // Should succeed because preempt returns response without network call
        var response = client.send(request);

        assertEquals("Preempted", readBody(response));
    }

    @Test
    void onErrorInterceptorHandlesFailure() throws Exception {
        // Stop server to cause connection failure
        server.stop();

        var errorHandled = new AtomicInteger();
        var interceptor = new HttpInterceptor() {
            @Override
            public HttpResponse onError(
                    HttpClient client,
                    HttpRequest request,
                    Context context,
                    IOException exception
            ) {
                errorHandled.incrementAndGet();
                // Return fallback response
                return HttpResponse.builder()
                        .statusCode(503)
                        .headers(HttpHeaders.ofModifiable())
                        .body(DataStream.ofString("Fallback"))
                        .build();
            }
        };

        client = clientBuilder().addInterceptor(interceptor).build();
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1,
                "http://localhost:" + server.getPort(),
                "");

        var response = client.send(request);

        assertEquals(503, response.statusCode());
        assertEquals("Fallback", readBody(response));
        assertEquals(1, errorHandled.get());
    }

    private String readBody(HttpResponse response) {
        var buf = response.body().asByteBuffer();
        var bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
