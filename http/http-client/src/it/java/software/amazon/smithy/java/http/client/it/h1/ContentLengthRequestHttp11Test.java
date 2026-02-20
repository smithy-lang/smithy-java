/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpHeaders;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.Http11ClientHandler;
import software.amazon.smithy.java.io.datastream.DataStream;

/**
 * Tests HTTP/1.1 request body with Content-Length (non-chunked).
 */
public class ContentLengthRequestHttp11Test extends BaseHttpClientIntegTest {

    private CapturingHandler handler;

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        handler = new CapturingHandler();
        return builder
                .httpVersion(software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> handler);
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1);
    }

    @Test
    void sendsRequestBodyWithContentLength() throws Exception {
        var headers = HttpHeaders.ofModifiable();
        headers.addHeader("content-type", "text/plain");
        headers.addHeader("content-length", String.valueOf(REQUEST_CONTENTS.length()));

        var request = HttpRequest.builder()
                .httpVersion(software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_1)
                .uri(new URI(uri()))
                .method("POST")
                .headers(headers)
                .body(DataStream.ofString(REQUEST_CONTENTS))
                .build();

        var response = client.send(request);

        assertEquals(200, response.statusCode());
        assertEquals(RESPONSE_CONTENTS, readBody(response));
        assertEquals(REQUEST_CONTENTS, handler.capturedBody.toString(StandardCharsets.UTF_8));

        // Verify Content-Length was sent (not chunked)
        assertTrue(handler.capturedHeaders.containsKey("content-length")
                || handler.capturedHeaders.containsKey("Content-Length"));
    }

    @Test
    void sendsLargeRequestBodyWithContentLength() throws Exception {
        // 100KB body
        String largeBody = "x".repeat(100 * 1024);

        var headers = HttpHeaders.ofModifiable();
        headers.addHeader("content-type", "text/plain");
        headers.addHeader("content-length", String.valueOf(largeBody.length()));

        var request = HttpRequest.builder()
                .httpVersion(software.amazon.smithy.java.http.api.HttpVersion.HTTP_1_1)
                .uri(new URI(uri()))
                .method("POST")
                .headers(headers)
                .body(DataStream.ofString(largeBody))
                .build();

        var response = client.send(request);

        assertEquals(200, response.statusCode());
        assertEquals(largeBody, handler.capturedBody.toString(StandardCharsets.UTF_8));
    }

    static class CapturingHandler implements Http11ClientHandler {
        final Map<String, List<String>> capturedHeaders = new HashMap<>();
        final ByteArrayOutputStream capturedBody = new ByteArrayOutputStream();

        @Override
        public void onFullRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
            // Capture headers
            for (var entry : request.headers()) {
                capturedHeaders.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(entry.getValue());
            }
            // Capture body
            var content = request.content();
            var bytes = new byte[content.readableBytes()];
            content.getBytes(content.readerIndex(), bytes);
            try {
                capturedBody.write(bytes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            // Send response
            var responseContent = Unpooled.copiedBuffer(RESPONSE_CONTENTS, CharsetUtil.UTF_8);
            var response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, responseContent);
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, responseContent.readableBytes());
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            ctx.writeAndFlush(response);
        }
    }
}
