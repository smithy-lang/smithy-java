/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server.spi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.server.KnownProtocolVersion;
import software.amazon.smithy.java.mcp.server.McpRemoteClient;
import software.amazon.smithy.java.mcp.server.McpRemoteException;
import software.amazon.smithy.java.mcp.server.McpServerIdentity;
import software.amazon.smithy.java.mcp.server.ProtocolVersion;

class StandaloneMcpClientTest {
    private static final McpServerIdentity IDENTITY = new McpServerIdentity("test-client", "2.0");

    @Test
    void publicInitializationAdvertisesIdentityAndNegotiatesOnceWithoutListingTools() {
        var client = new Client();
        client.start();
        client.initialize(IDENTITY, ProtocolVersion.defaultVersion());
        client.initialize(IDENTITY, ProtocolVersion.defaultVersion());

        assertEquals(List.of("initialize", "notifications/initialized"),
                client.requests.stream().map(JsonRpcRequest::getMethod).toList());
        var params = client.requests.getFirst().getParams();
        assertEquals("test-client", params.getMember("clientInfo").getMember("name").asString());
        assertEquals("2.0", params.getMember("clientInfo").getMember("version").asString());
        assertEquals(ProtocolVersion.defaultVersion().identifier(),
                params.getMember("protocolVersion").asString());
        assertTrue(params.getMember("capabilities").asStringMap().isEmpty());
        assertNull(client.requests.getLast().getId());
        assertEquals(KnownProtocolVersion.V2025_11_25, client.negotiatedVersion());
    }

    @Test
    void initializationFailureReachesCallerWithoutSendingInitializedNotification() {
        var client = new Client();
        client.error = JsonRpcErrorResponse.builder().code(-32000).message("access denied").build();

        var error = assertThrows(McpRemoteException.class,
                () -> client.initialize(IDENTITY, ProtocolVersion.defaultVersion()));

        assertTrue(error.getMessage().contains("access denied"));
        assertEquals(1, client.requests.size());
    }

    @Test
    void unsupportedNegotiatedVersionReachesCaller() {
        var client = new Client();
        client.version = "2099-01-01";

        assertThrows(McpRemoteException.class,
                () -> client.initialize(IDENTITY, ProtocolVersion.defaultVersion()));
        assertEquals(1, client.requests.size());
    }

    @Test
    void statelessVersionCannotBeInitialized() {
        var client = new Client();

        assertThrows(IllegalArgumentException.class,
                () -> client.initialize(IDENTITY, KnownProtocolVersion.V2026_07_28));
        assertTrue(client.requests.isEmpty());
    }

    private static final class Client extends McpRemoteClient {
        private final List<JsonRpcRequest> requests = new ArrayList<>();
        private String version = KnownProtocolVersion.V2025_11_25.identifier();
        private JsonRpcErrorResponse error;

        @Override
        protected JsonRpcResponse exchange(JsonRpcRequest request) {
            requests.add(request);
            if (request.getId() == null) {
                return null;
            }
            var response = JsonRpcResponse.builder().jsonrpc("2.0").id(request.getId());
            return error == null
                    ? response.result(Document.of(Map.of("protocolVersion", Document.of(version)))).build()
                    : response.error(error).build();
        }

        ProtocolVersion negotiatedVersion() {
            return protocolVersion();
        }

        @Override
        public void start() {}

        @Override
        public void close() {}

        @Override
        public String name() {
            return "remote";
        }
    }
}
