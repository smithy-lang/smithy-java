/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;

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
}
