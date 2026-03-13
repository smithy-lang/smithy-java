/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.TestUtils;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.TextResponseHttp11ClientHandler;

/**
 * Tests that connection timeout throws SocketTimeoutException.
 */
public class ConnectTimeoutHttp11Test extends BaseHttpClientIntegTest {

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        // Server exists but we'll connect to a non-routable IP
        return builder
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new TextResponseHttp11ClientHandler(RESPONSE_CONTENTS));
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .connectTimeout(Duration.ofMillis(100)); // Very short timeout
    }

    @Test
    void connectTimeoutThrowsException() {
        // Use non-routable IP address (RFC 5737 TEST-NET-1) to trigger connect timeout
        var request = TestUtils.plainTextRequest(HttpVersion.HTTP_1_1, "http://192.0.2.1:12345", "");

        assertThrows(IOException.class, () -> client.send(request));
    }
}
