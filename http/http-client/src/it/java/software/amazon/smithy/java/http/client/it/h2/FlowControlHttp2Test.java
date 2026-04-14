/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h2.LargeResponseHttp2ClientHandler;

/**
 * Tests HTTP/2 flow control with large response bodies and slow client reads.
 */
public class FlowControlHttp2Test extends BaseHttpClientIntegTest {

    private static final int RESPONSE_SIZE = 1024 * 1024; // 1MB
    private static final int CHUNK_SIZE = 16384; // 16KB chunks

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        return builder
                .httpVersion(HttpVersion.HTTP_2)
                .h2ConnectionMode(NettyTestServer.H2ConnectionMode.PRIOR_KNOWLEDGE)
                .http2HandlerFactory(ctx -> new LargeResponseHttp2ClientHandler(RESPONSE_SIZE, CHUNK_SIZE));
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE);
    }

    @Test
    void largeResponseBodyWithFlowControl() throws Exception {
        var request = plainTextRequest(HttpVersion.HTTP_2, "");
        var response = client.send(request);

        // Read slowly with small buffer to exercise flow control
        byte[] buffer = new byte[1024];
        int totalRead = 0;
        int bytesRead;

        try (InputStream is = response.body().asInputStream()) {
            while ((bytesRead = is.read(buffer)) != -1) {
                // Verify data pattern
                for (int i = 0; i < bytesRead; i++) {
                    byte expected = (byte) ((totalRead + i) & 0xFF);
                    assertEquals(expected, buffer[i], "Data mismatch at position " + (totalRead + i));
                }
                totalRead += bytesRead;

                // Simulate slow client - delay every 64KB
                if (totalRead % (64 * 1024) == 0) {
                    Thread.sleep(10);
                }
            }
        }

        assertEquals(RESPONSE_SIZE, totalRead, "Should receive complete response body");
    }
}
