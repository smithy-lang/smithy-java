/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h1.DelayedResponseHttp11ClientHandler;
import software.amazon.smithy.java.http.client.it.server.h1.TextResponseHttp11ClientHandler;

/**
 * Tests that read timeout throws SocketTimeoutException.
 */
public class ReadTimeoutHttp11Test extends BaseHttpClientIntegTest {

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        return builder
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new DelayedResponseHttp11ClientHandler(
                        new TextResponseHttp11ClientHandler(RESPONSE_CONTENTS),
                        2000)); // 2s delay
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .readTimeout(Duration.ofMillis(100)); // 100ms timeout, server delays 2s
    }

    @Test
    void readTimeoutThrowsException() {
        var request = plainTextRequest(HttpVersion.HTTP_1_1, "");

        assertThrows(SocketTimeoutException.class, () -> client.send(request));
    }
}
