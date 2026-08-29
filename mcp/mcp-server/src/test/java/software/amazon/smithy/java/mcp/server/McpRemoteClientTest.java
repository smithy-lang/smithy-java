/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ListPromptsResult;
import software.amazon.smithy.java.mcp.model.ListToolsResult;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.java.mcp.model.ToolInfo;

class McpRemoteClientTest {

    private static final class FakeClient extends McpRemoteClient {
        private final List<JsonRpcRequest> requests = new ArrayList<>();
        private final List<JsonRpcResponse> responses;
        private int index;

        FakeClient(List<JsonRpcResponse> responses) {
            this.responses = responses;
        }

        @Override
        protected JsonRpcResponse exchange(JsonRpcRequest request) {
            requests.add(request);
            return responses.get(index++);
        }

        @Override
        public void start() {}

        @Override
        public void close() {}

        @Override
        public String name() {
            return "fake";
        }
    }

    @Test
    void toolsPaginationFetchesOnePageAtATime() {
        var client = new FakeClient(List.of(
                toolsResponse(List.of(tool("a"), tool("b")), "CURSOR1"),
                toolsResponse(List.of(tool("c"), tool("d")), "CURSOR2"),
                toolsResponse(List.of(tool("e")), null)));

        var first = client.listTools();
        assertEquals(List.of("a", "b"), names(first.items()));
        assertEquals(1, client.requests.size());
        assertNull(client.requests.getFirst().getParams());

        var second = first.nextPage().orElseThrow().fetch();
        assertEquals(List.of("c", "d"), names(second.items()));
        assertEquals(2, client.requests.size());
        assertEquals("CURSOR1", client.requests.get(1).getParams().getMember("cursor").asString());

        var third = second.nextPage().orElseThrow().fetch();
        assertEquals(List.of("e"), names(third.items()));
        assertFalse(third.nextPage().isPresent());
        assertEquals("CURSOR2", client.requests.get(2).getParams().getMember("cursor").asString());
    }

    @Test
    void promptsPaginationFetchesOnePageAtATime() {
        var client = new FakeClient(List.of(
                promptsResponse(List.of(prompt("p1")), "PC1"),
                promptsResponse(List.of(prompt("p2"), prompt("p3")), null)));

        var first = client.listPrompts();
        assertEquals(List.of("p1"), first.items().stream().map(PromptInfo::getName).toList());
        assertEquals(1, client.requests.size());

        var second = first.nextPage().orElseThrow().fetch();
        assertEquals(List.of("p2", "p3"), second.items().stream().map(PromptInfo::getName).toList());
        assertEquals("PC1", client.requests.get(1).getParams().getMember("cursor").asString());
    }

    @Test
    void forwardedRequestsUseClientOwnedIdsAndRestoreTheCallerId() {
        var client = new FakeClient(List.of(
                success(Document.of(Map.of())),
                success(Document.of(Map.of()))));
        var callerId = Document.of("shared-caller-id");
        var request = JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(callerId)
                .method(McpMethod.Standard.PING.wireName())
                .build();

        var first = client.exchangeForwarded(request);
        var second = client.exchangeForwarded(request);

        assertEquals(callerId, first.getId());
        assertEquals(callerId, second.getId());
        assertFalse(client.requests.getFirst().getId().equals(callerId));
        assertFalse(client.requests.get(1).getId().equals(callerId));
        assertFalse(client.requests.getFirst().getId().equals(client.requests.get(1).getId()));
    }

    @Test
    void initializeUsesTheProtocolVersionNegotiatedByTheRemote() {
        var client = new McpRemoteClient() {
            @Override
            protected JsonRpcResponse exchange(JsonRpcRequest request) {
                if (McpHttpBinding.isInitialize(request)) {
                    return JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .id(request.getId())
                            .result(Document.of(Map.of(
                                    "protocolVersion",
                                    Document.of(KnownProtocolVersion.V2025_03_26.identifier()))))
                            .build();
                }
                return null;
            }

            ProtocolVersion currentProtocolVersion() {
                return protocolVersion();
            }

            @Override
            public void start() {}

            @Override
            public void close() {}

            @Override
            public String name() {
                return "negotiating";
            }
        };
        var requested = BuiltInProtocols.protocol(KnownProtocolVersion.V2025_11_25);

        client.initialize(
                ignored -> {},
                ignored -> {},
                JsonRpcRequest.builder()
                        .jsonrpc("2.0")
                        .id(Document.of(1))
                        .method(McpMethod.Standard.INITIALIZE.wireName())
                        .params(Document.of(Map.of(
                                "protocolVersion",
                                Document.of(requested.id().identifier()))))
                        .build(),
                requested);

        assertEquals(KnownProtocolVersion.V2025_03_26, client.currentProtocolVersion());
        assertEquals(
                KnownProtocolVersion.V2025_11_25,
                client.usingProtocol(requested, client::currentProtocolVersion));
    }

    @Test
    void modernProtocolMetadataIsRetainedAcrossToolPages() {
        var client = new FakeClient(List.of(
                toolsResponse(List.of(tool("FirstTool")), "next"),
                toolsResponse(List.of(tool("SecondTool")), null)));
        var modern = BuiltInProtocols.protocol(KnownProtocolVersion.V2026_07_28);

        var first = client.usingProtocol(modern, client::listTools);
        var second = first.nextPage().orElseThrow().fetch();

        assertEquals(List.of("FirstTool"), names(first.items()));
        assertEquals(List.of("SecondTool"), names(second.items()));
        for (var request : client.requests) {
            var metadata = request.getParams().getMember("_meta");
            assertEquals(
                    KnownProtocolVersion.V2026_07_28.identifier(),
                    metadata.getMember(McpWireNames.PROTOCOL_VERSION).asString());
            assertEquals(
                    Map.of(),
                    metadata.getMember(McpWireNames.CLIENT_CAPABILITIES).asStringMap());
        }
    }

    @Test
    void blankCursorEndsListing() {
        var client = new FakeClient(List.of(toolsResponse(List.of(tool("a")), "")));

        var page = client.listTools();

        assertFalse(page.nextPage().isPresent());
        assertEquals(1, client.requests.size());
    }

    @Test
    void laterPageErrorsAreDeferredUntilContinuationIsInvoked() {
        var client = new FakeClient(List.of(
                toolsResponse(List.of(tool("a")), "next"),
                errorResponse("boom")));

        var first = client.listTools();
        assertEquals(List.of("a"), names(first.items()));

        var error = assertThrows(
                McpRemoteException.class,
                () -> first.nextPage().orElseThrow().fetch());
        assertTrue(error.getMessage().contains("boom"));
    }

    @Test
    void repeatedCursorFailsWhenTheContinuationIsInvoked() {
        var client = new FakeClient(List.of(
                toolsResponse(List.of(tool("FirstTool")), "same"),
                toolsResponse(List.of(tool("SecondTool")), "same")));

        var first = client.listTools();

        var error = assertThrows(
                McpRemoteException.class,
                () -> first.nextPage().orElseThrow().fetch());
        assertTrue(error.getMessage().contains("repeated cursor"));
        assertEquals(2, client.requests.size());
    }

    @Test
    void cachedContinuationCanStartIndependentPaginationTraversals() {
        var client = new FakeClient(List.of(
                toolsResponse(List.of(tool("FirstTool")), "cursor1"),
                toolsResponse(List.of(tool("SecondTool")), "cursor2"),
                toolsResponse(List.of(tool("SecondTool")), "cursor2")));

        var first = client.listTools();
        var continuation = first.nextPage().orElseThrow();

        var firstBranch = continuation.fetch();
        var secondBranch = continuation.fetch();

        assertEquals(List.of("SecondTool"), names(firstBranch.items()));
        assertEquals(List.of("SecondTool"), names(secondBranch.items()));
        assertTrue(firstBranch.nextPage().isPresent());
        assertTrue(secondBranch.nextPage().isPresent());
        assertEquals("cursor1", client.requests.get(1).getParams().getMember("cursor").asString());
        assertEquals("cursor1", client.requests.get(2).getParams().getMember("cursor").asString());
    }

    @Test
    void listingStopsBeforeFetchingMoreThanThePageLimit() {
        var responses = IntStream.range(0, 1_000)
                .mapToObj(index -> toolsResponse(
                        List.of(tool("Tool" + index)),
                        "cursor" + index))
                .toList();
        var client = new FakeClient(responses);

        var page = client.listTools();
        for (int index = 1; index < 1_000; index++) {
            page = page.nextPage().orElseThrow().fetch();
        }

        var lastPage = page;
        var error = assertThrows(
                McpRemoteException.class,
                () -> lastPage.nextPage().orElseThrow().fetch());
        assertTrue(error.getMessage().contains("maximum of 1000 pages"));
        assertEquals(1_000, client.requests.size());
    }

    @Test
    void missingResultProducesAnActionableRemoteError() {
        var client = new FakeClient(List.of(JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .build()));

        var error = assertThrows(McpRemoteException.class, client::listTools);

        assertTrue(error.getMessage().contains("listing tools"));
        assertTrue(error.getMessage().contains("did not contain a result"));
    }

    @Test
    void pageItemsAreImmutable() {
        var client = new FakeClient(List.of(toolsResponse(List.of(tool("a")), null)));

        var page = client.listTools();

        assertThrows(UnsupportedOperationException.class, () -> page.items().add(tool("b")));
    }

    private static List<String> names(List<ToolInfo> tools) {
        return tools.stream().map(ToolInfo::getName).toList();
    }

    private static ToolInfo tool(String name) {
        return ToolInfo.builder().name(name).build();
    }

    private static PromptInfo prompt(String name) {
        return PromptInfo.builder().name(name).build();
    }

    private static JsonRpcResponse toolsResponse(List<ToolInfo> tools, String nextCursor) {
        var result = ListToolsResult.builder().tools(tools);
        if (nextCursor != null) {
            result.nextCursor(nextCursor);
        }
        return success(Document.of(result.build()));
    }

    private static JsonRpcResponse promptsResponse(List<PromptInfo> prompts, String nextCursor) {
        var result = ListPromptsResult.builder().prompts(prompts);
        if (nextCursor != null) {
            result.nextCursor(nextCursor);
        }
        return success(Document.of(result.build()));
    }

    private static JsonRpcResponse success(Document result) {
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .result(result)
                .build();
    }

    private static JsonRpcResponse errorResponse(String message) {
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .error(JsonRpcErrorResponse.builder().code(-32000).message(message).build())
                .build();
    }
}
