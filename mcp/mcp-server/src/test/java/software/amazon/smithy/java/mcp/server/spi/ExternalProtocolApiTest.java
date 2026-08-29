/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server.spi;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.server.ExtensionMcpProtocol;
import software.amazon.smithy.java.mcp.server.McpEngine;
import software.amazon.smithy.java.mcp.server.McpMethod;
import software.amazon.smithy.java.mcp.server.McpProtocolId;
import software.amazon.smithy.java.mcp.server.UnknownProtocolVersion;

class ExternalProtocolApiTest {

    @Test
    void externalPackageCanImplementAndRegisterAProtocol() {
        var protocol = new ExternalProtocol();
        try (var engine = McpEngine.builder()
                .discoverProtocols(false)
                .addProtocol(protocol)
                .build()) {
            var response = engine.execute(
                    JsonRpcRequest.builder()
                            .jsonrpc("2.0")
                            .id(Document.of(1))
                            .method(McpMethod.Standard.PING.wireName())
                            .build(),
                    new UnknownProtocolVersion(protocol.id().identifier()));

            assertNull(response.getError());
        }
    }

    private record ExternalProtocol() implements ExtensionMcpProtocol {
        @Override
        public McpProtocolId id() {
            return McpProtocolId.of("2099-external-api");
        }

        @Override
        public Set<McpMethod.Standard> supportedMethods() {
            return Set.of(McpMethod.Standard.PING);
        }
    }
}
