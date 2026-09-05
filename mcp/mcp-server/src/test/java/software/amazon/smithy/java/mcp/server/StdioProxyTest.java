/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;

class StdioProxyTest {

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void rpcTimesOutWhenServerStaysSilent() {
        // `sleep` accepts the request on stdin but never writes a response, so the request future must
        // fail via the per-request timeout rather than blocking the caller forever.
        var proxy = StdioProxy.builder()
                .name("silent-server")
                .command("sleep")
                .arguments(List.of("30"))
                .timeout(Duration.ofMillis(500))
                .build();
        proxy.start();
        try {
            var future = proxy.rpc(JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(Document.of(1))
                    .method("tools/list")
                    .build());

            var ex = assertThrows(CompletionException.class, future::join);
            assertInstanceOf(TimeoutException.class, ex.getCause());
        } finally {
            proxy.shutdown().join();
        }
    }
}
