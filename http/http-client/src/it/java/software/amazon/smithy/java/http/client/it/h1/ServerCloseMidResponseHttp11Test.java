/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.CloseReason;
import software.amazon.smithy.java.http.client.connection.ConnectionPoolListener;
import software.amazon.smithy.java.http.client.connection.HttpConnection;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.PartialResponseHttp11ClientHandler;

/**
 * Tests client handling when server closes connection mid-response.
 */
public class ServerCloseMidResponseHttp11Test extends BaseHttpClientIntegTest {

    private final AtomicInteger connectCount = new AtomicInteger();
    private final AtomicInteger closeCount = new AtomicInteger();

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        // Server advertises 1000 bytes but only sends 10, then closes
        return builder
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new PartialResponseHttp11ClientHandler("partial...", 1000));
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .addListener(new ConnectionPoolListener() {
                    @Override
                    public void onConnected(HttpConnection conn) {
                        connectCount.incrementAndGet();
                    }

                    @Override
                    public void onClosed(HttpConnection conn, CloseReason reason) {
                        closeCount.incrementAndGet();
                    }
                });
    }

    @Test
    void throwsWhenServerClosesBeforeFullResponse() throws Exception {
        var request = plainTextRequest(HttpVersion.HTTP_1_1, "");

        var response = client.send(request);

        // Reading the body should fail because server closed before sending full content
        var ex = assertThrows(IOException.class, () -> readBody(response));
        assertTrue(
                ex.getMessage().contains("end of stream")
                        || ex.getMessage().contains("EOF")
                        || ex.getMessage().contains("closed")
                        || ex.getMessage().contains("Unexpected"),
                "Expected EOF-related error, got: " + ex.getMessage());

        // Connection should have been created
        assertEquals(1, connectCount.get(), "Should have created 1 connection");

        // Connection should be closed/evicted, not returned to pool
        assertEquals(1, closeCount.get(), "Connection should be closed after truncated response");
    }
}
