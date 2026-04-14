/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.http.client.it.h1;

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
import software.amazon.smithy.java.http.client.it.server.h1.TextResponseHttp11ClientHandler;

/**
 * Stress test with many concurrent HTTP/1.1 requests.
 * Tests various pool sizes to verify connection reuse and permit release.
 */
public class HighConcurrencyHttp11Test extends BaseHttpClientIntegTest {

    // Set by parameterized test
    private int poolSize = 20;

    @Override
    protected NettyTestServer.Builder configureServer(NettyTestServer.Builder builder) {
        return builder
                .httpVersion(HttpVersion.HTTP_1_1)
                .http11HandlerFactory(ctx -> new TextResponseHttp11ClientHandler(RESPONSE_CONTENTS));
    }

    @Override
    protected HttpConnectionPoolBuilder configurePool(HttpConnectionPoolBuilder builder) {
        return builder
                .httpVersionPolicy(HttpVersionPolicy.ENFORCE_HTTP_1_1)
                .maxConnectionsPerRoute(poolSize)
                .maxTotalConnections(poolSize);
    }

    static Stream<Arguments> poolConfigurations() {
        return Stream.of(
                // poolSize, numRequests - tests connection reuse when requests > pool
                Arguments.of(5, 50), // 10x reuse required
                Arguments.of(10, 100), // 10x reuse required
                Arguments.of(20, 100), // 5x reuse required
                Arguments.of(50, 100) // 2x reuse required
        );
    }

    @ParameterizedTest(name = "pool={0}, requests={1}")
    @MethodSource("poolConfigurations")
    void handlesMoreRequestsThanPoolSize(int poolSize, int numRequests) throws Exception {
        this.poolSize = poolSize;

        // Recreate client with new pool size
        if (client != null) {
            client.close();
        }
        setUp();

        var futures = new ArrayList<CompletableFuture<String>>();
        var successCount = new AtomicInteger();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < numRequests; i++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        var request = plainTextRequest(HttpVersion.HTTP_1_1, "");
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
