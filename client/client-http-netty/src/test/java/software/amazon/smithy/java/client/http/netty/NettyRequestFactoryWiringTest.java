/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.http.netty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.client.http.HttpContext;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Verifies the transport-supplied request-factory hook is wired end to end: the Netty transport
 * publishes its factory into the call context (H1 path), a request whose headers were allocated from
 * that factory is Netty-backed, and the send path reuses the Netty header container by reference
 * (zero re-marshal) while still delivering every header to the server.
 */
class NettyRequestFactoryWiringTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void transportPublishesNettyFactoryOnH1Path() {
        var transport = new NettyHttpClientTransport(new NettyHttpTransportConfig());
        try {
            var ctx = Context.create();
            transport.contributeRequestFactory(ctx);
            var factory = ctx.get(HttpContext.TRANSPORT_REQUEST_FACTORY);
            assertThat(factory, is(notNullValue()));
            // The factory vends Netty-backed writable headers.
            assertThat(factory.newRequestHeaders(4),
                    is(Matchers.instanceOf(NettyModifiableH1Headers.class)));
        } finally {
            closeQuietly(transport);
        }
    }

    @Test
    void buildH1RequestReusesNettyBackingByReference() {
        // A request whose headers are Netty-backed (as the protocol would produce under the factory)
        // must have those exact Netty headers reused on the request line build — not re-copied.
        var headers = new NettyModifiableH1Headers();
        headers.addHeader("x-amz-target", "Svc.Op");
        var backing = headers.nettyHeaders();

        var request = HttpRequest.create()
                .setMethod("POST")
                .setUri(URI.create("http://example.com:8080/path"))
                .setHttpVersion(HttpVersion.HTTP_1_1)
                .setHeaders(headers)
                .toUnmodifiable();

        var nettyReq = NettyUtils.buildH1Request(
                request,
                io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/path");

        assertThat(nettyReq.headers(), is(sameInstance(backing)));
        assertThat(nettyReq.headers().get("x-amz-target"), equalTo("Svc.Op"));
        // Host derived from the URI authority.
        assertThat(nettyReq.headers().get("host"), equalTo("example.com:8080"));
    }

    @Test
    void endToEndHeadersDeliveredViaFactoryPath() throws Exception {
        Map<String, String> received = new ConcurrentHashMap<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/op", exchange -> {
            exchange.getRequestHeaders().forEach((k, v) -> {
                if (!v.isEmpty()) {
                    received.put(k.toLowerCase(java.util.Locale.ROOT), v.get(0));
                }
            });
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        server.start();

        var transport = new NettyHttpClientTransport(new NettyHttpTransportConfig().maxConnectionsPerHost(1));
        try {
            var ctx = Context.create();
            // Simulate the pipeline publishing the transport factory, then a protocol serializing
            // headers into the factory-allocated container.
            transport.contributeRequestFactory(ctx);
            var headers = ctx.get(HttpContext.TRANSPORT_REQUEST_FACTORY).newRequestHeaders(4);
            headers.addHeader("X-Amz-Target", "DynamoDB_20120810.GetItem");
            headers.addHeader("Content-Type", "application/x-amz-json-1.0");

            String uri = "http://127.0.0.1:" + server.getAddress().getPort() + "/op";
            HttpRequest request = HttpRequest.create()
                    .setMethod("POST")
                    .setUri(URI.create(uri))
                    .setHttpVersion(HttpVersion.HTTP_1_1)
                    .setHeaders(headers)
                    .setBody(DataStream.ofString("{}", "application/x-amz-json-1.0"))
                    .toUnmodifiable();

            HttpResponse response = transport.send(ctx, request);
            assertThat(response.statusCode(), equalTo(200));
            assertThat(received.get("x-amz-target"), equalTo("DynamoDB_20120810.GetItem"));
            assertThat(received.get("host"), equalTo("127.0.0.1:" + server.getAddress().getPort()));
        } finally {
            closeQuietly(transport);
        }
    }

    private static void closeQuietly(NettyHttpClientTransport transport) {
        try {
            transport.close();
        } catch (Exception ignored) {
            // best effort
        }
    }
}
