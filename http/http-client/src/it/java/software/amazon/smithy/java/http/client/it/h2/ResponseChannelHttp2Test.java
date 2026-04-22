/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h2;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.DefaultHttp2DataFrame;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.handler.codec.http2.DefaultHttp2HeadersFrame;
import io.netty.handler.codec.http2.Http2HeadersFrame;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.TestUtils;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h2.Http2ClientHandler;

/**
 * Tests the native channel response body path over a real HTTP/2 connection.
 */
public class ResponseChannelHttp2Test extends BaseHttpClientIntegTest {

    private static final int LARGE_RESPONSE_SIZE = 1024 * 1024;
    private static final int CHUNK_SIZE = 16 * 1024;
    private static final String SMALL_RESPONSE = "small response";

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        return builder
                .httpVersion(HttpVersion.HTTP_2)
                .h2ConnectionMode(NettyTestServer.H2ConnectionMode.PRIOR_KNOWLEDGE)
                .http2HandlerFactory(ctx -> new PathResponseHandler());
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder
                .httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE)
                .maxConnectionsPerRoute(1);
    }

    @Test
    void readsLargeResponseThroughChannel() throws Exception {
        var response = client.send(request("/large"));
        var actual = new ByteArrayOutputStream(LARGE_RESPONSE_SIZE);
        var buffer = ByteBuffer.allocate(8192);

        try (var channel = response.body().asChannel()) {
            while (channel.read(buffer) != -1) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    actual.write(buffer.get());
                }
                buffer.clear();
            }
        }

        assertArrayEquals(expectedLargeResponse(), actual.toByteArray());
    }

    @Test
    void partialChannelReadCloseAllowsNextStreamOnSameConnection() throws Exception {
        var largeResponse = client.send(request("/large"));
        var buffer = ByteBuffer.allocate(8192);

        try (var channel = largeResponse.body().asChannel()) {
            int totalRead = 0;
            while (totalRead < buffer.capacity()) {
                int read = channel.read(buffer);
                if (read == -1) {
                    break;
                }
                totalRead += read;
            }
            assertEquals(buffer.capacity(), totalRead);
        }

        var smallResponse = client.send(request("/small"));
        assertEquals(SMALL_RESPONSE, readBody(smallResponse));
    }

    private software.amazon.smithy.java.http.api.HttpRequest request(String path) {
        return TestUtils.plainTextRequest(HttpVersion.HTTP_2, uri(path), "");
    }

    private static byte[] expectedLargeResponse() {
        byte[] expected = new byte[LARGE_RESPONSE_SIZE];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = (byte) (i & 0xFF);
        }
        return expected;
    }

    private static final class PathResponseHandler implements Http2ClientHandler {

        @Override
        public void onHeadersFrame(ChannelHandlerContext ctx, Http2HeadersFrame frame) {
            String path = frame.headers().path().toString();
            if ("/small".equals(path)) {
                sendSmallResponse(ctx);
            } else {
                sendLargeResponse(ctx);
            }
        }

        private static void sendSmallResponse(ChannelHandlerContext ctx) {
            byte[] body = SMALL_RESPONSE.getBytes(StandardCharsets.UTF_8);
            var headers = new DefaultHttp2Headers();
            headers.status("200");
            headers.set("content-type", "text/plain");
            headers.setInt("content-length", body.length);
            ctx.write(new DefaultHttp2HeadersFrame(headers));
            ctx.writeAndFlush(new DefaultHttp2DataFrame(Unpooled.wrappedBuffer(body), true));
        }

        private static void sendLargeResponse(ChannelHandlerContext ctx) {
            var headers = new DefaultHttp2Headers();
            headers.status("200");
            headers.set("content-type", "application/octet-stream");
            headers.setInt("content-length", LARGE_RESPONSE_SIZE);
            ctx.write(new DefaultHttp2HeadersFrame(headers));

            int position = 0;
            while (position < LARGE_RESPONSE_SIZE) {
                int size = Math.min(CHUNK_SIZE, LARGE_RESPONSE_SIZE - position);
                byte[] chunk = new byte[size];
                for (int i = 0; i < size; i++) {
                    chunk[i] = (byte) ((position + i) & 0xFF);
                }
                position += size;
                ctx.write(new DefaultHttp2DataFrame(
                        Unpooled.wrappedBuffer(chunk),
                        position == LARGE_RESPONSE_SIZE));
            }
            ctx.flush();
        }
    }
}
