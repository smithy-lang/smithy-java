/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;

class StdioMcpClientTest {

    @Test
    void requestKeysSupportEveryValidNumericId() {
        assertEquals("number:2147483648", StdioMcpClient.requestKey(Document.of(2_147_483_648L)));
        assertEquals(
                "number:9223372036854775808",
                StdioMcpClient.requestKey(Document.of(new BigInteger("9223372036854775808"))));
    }

    @Test
    void numericAndStringIdsDoNotCollide() {
        assertNotEquals(
                StdioMcpClient.requestKey(Document.of(1)),
                StdioMcpClient.requestKey(Document.of("1")));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    void exchangeTimesOutWhenServerStaysSilent() {
        var client = StdioMcpClient.builder()
                .name("silent-server")
                .command("sleep")
                .arguments(List.of("30"))
                .timeout(Duration.ofMillis(500))
                .build();
        client.start();
        try {
            var error = assertThrows(
                    McpRemoteException.class,
                    () -> client.exchange(JsonRpcRequest.builder()
                            .jsonrpc("2.0")
                            .id(Document.of(1))
                            .method("tools/list")
                            .build()));
            assertTrue(error.getMessage().contains("Timed out"));
        } finally {
            client.close();
        }
    }
}
