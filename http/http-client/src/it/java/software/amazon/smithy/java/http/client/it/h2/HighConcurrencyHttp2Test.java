/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.http.api.HttpVersion;
import software.amazon.smithy.java.http.client.connection.HttpConnectionPoolBuilder;
import software.amazon.smithy.java.http.client.connection.HttpVersionPolicy;
import software.amazon.smithy.java.http.client.it.server.NettyTestServer;
import software.amazon.smithy.java.http.client.it.server.h2.TextResponseHttp2ClientHandler;

/**
 * Stress test with many concurrent HTTP/2 requests.
 * Tests various pool sizes to verify stream multiplexing and connection reuse.
 */
public class HighConcurrencyHttp2Test extends BaseHttpClientIntegTest {

    private int poolSize = 5;

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        return builder
                .httpVersion(HttpVersion.HTTP_2)
                .h2ConnectionMode(NettyTestServer.H2ConnectionMode.PRIOR_KNOWLEDGE)
                .http2HandlerFactory(ctx -> new TextResponseHttp2ClientHandler(RESPONSE_CONTENTS));
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder
                .httpVersionPolicy(HttpVersionPolicy.H2C_PRIOR_KNOWLEDGE)
                .maxConnectionsPerRoute(poolSize)
                .maxTotalConnections(poolSize);
    }

    static Stream<Arguments> poolConfigurations() {
        return Stream.of(
                // poolSize, numRequests - H2 multiplexes so fewer connections needed
                Arguments.of(1, 50), // All on single connection
                Arguments.of(2, 100), // 50 streams per connection
                Arguments.of(5, 100), // 20 streams per connection
                Arguments.of(10, 200) // 20 streams per connection
        );
    }

    @ParameterizedTest(name = "pool={0}, requests={1}")
    @MethodSource("poolConfigurations")
    void handlesHighConcurrency(int poolSize, int numRequests) throws Exception {
        this.poolSize = poolSize;
        if (client != null)
            client.close();
        setUp();

        var futures = new ArrayList<CompletableFuture<String>>();
        var successCount = new AtomicInteger();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < numRequests; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        var request = plainTextRequest(HttpVersion.HTTP_2, "");
                        var response = client.send(request);
                        var body = readBody(response);
                        successCount.incrementAndGet();
                        return body;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, executor));
            }

            for (var future : futures) {
                assertEquals(RESPONSE_CONTENTS, future.join());
            }
        }

        assertEquals(numRequests, successCount.get());
    }
}
