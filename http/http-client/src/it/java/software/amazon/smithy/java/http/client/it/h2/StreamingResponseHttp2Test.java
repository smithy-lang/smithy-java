/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h2.StreamingResponseHttp2ClientHandler;

/**
 * Tests incremental reading of streaming response.
 */
public class StreamingResponseHttp2Test extends BaseHttpClientIntegTest {

    private static final List<String> CHUNKS = List.of("chunk1-", "chunk2-", "chunk3-", "chunk4-", "chunk5");
    private static final long DELAY_MS = 50;

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        return builder
                .httpVersion(HttpVersion.HTTP_2)
                .h2ConnectionMode(NettyTestServer.H2ConnectionMode.PRIOR_KNOWLEDGE)
                .http2HandlerFactory(ctx -> new StreamingResponseHttp2ClientHandler(CHUNKS, DELAY_MS));
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder.httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE);
    }

    @Test
    void canReadStreamingResponseIncrementally() throws Exception {
        var request = plainTextRequest(HttpVersion.HTTP_2, "");
        var response = client.send(request);

        var receivedChunks = new ArrayList<String>();
        byte[] buffer = new byte[64];
        int bytesRead;

        try (InputStream is = response.body().asInputStream()) {
            StringBuilder current = new StringBuilder();
            while ((bytesRead = is.read(buffer)) != -1) {
                current.append(new String(buffer, 0, bytesRead));
                while (current.indexOf("-") >= 0) {
                    int idx = current.indexOf("-");
                    receivedChunks.add(current.substring(0, idx + 1));
                    current.delete(0, idx + 1);
                }
            }
            if (!current.isEmpty()) {
                receivedChunks.add(current.toString());
            }
        }

        assertEquals(String.join("", CHUNKS), String.join("", receivedChunks));
    }
}
