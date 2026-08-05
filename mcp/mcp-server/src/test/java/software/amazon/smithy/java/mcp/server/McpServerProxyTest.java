/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ListPromptsResult;
import software.amazon.smithy.java.mcp.model.ListToolsResult;
import software.amazon.smithy.java.mcp.model.PromptInfo;
import software.amazon.smithy.java.mcp.model.ToolInfo;

class McpServerProxyTest {

    /**
     * Test proxy that replays a fixed list of canned responses and records every request it received,
     * so pagination behaviour (nextCursor -> cursor round-tripping) can be asserted.
     */
    private static final class FakeProxy extends McpServerProxy {
        private final List<JsonRpcRequest> requests = new ArrayList<>();
        private final List<JsonRpcResponse> responses;
        private int index = 0;

        FakeProxy(List<JsonRpcResponse> responses) {
            this.responses = responses;
        }

        @Override
        protected CompletableFuture<JsonRpcResponse> rpc(JsonRpcRequest request) {
            requests.add(request);
            return CompletableFuture.completedFuture(responses.get(index++));
        }

        @Override
        protected void start() {}

        @Override
        protected CompletableFuture<Void> shutdown() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public String name() {
            return "fake";
        }
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
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .result(Document.of(result.build()))
                .build();
    }

    private static JsonRpcResponse promptsResponse(List<PromptInfo> prompts, String nextCursor) {
        var result = ListPromptsResult.builder().prompts(prompts);
        if (nextCursor != null) {
            result.nextCursor(nextCursor);
        }
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .result(Document.of(result.build()))
                .build();
    }

    private static JsonRpcResponse errorResponse(String message) {
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .error(JsonRpcErrorResponse.builder().code(-32000).message(message).build())
                .build();
    }

    private static JsonRpcResponse emptyResponse() {
        // A malformed response carrying neither a result nor an error.
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .build();
    }

    @Test
    void listToolsFollowsNextCursorAcrossPages() {
        var proxy = new FakeProxy(List.of(
                toolsResponse(List.of(tool("a"), tool("b")), "CURSOR1"),
                toolsResponse(List.of(tool("c"), tool("d")), "CURSOR2"),
                toolsResponse(List.of(tool("e")), null)));

        var tools = proxy.listTools();

        assertEquals(List.of("a", "b", "c", "d", "e"),
                tools.stream().map(ToolInfo::getName).toList());
        assertEquals(3, proxy.requests.size());
        // First page carries no cursor.
        assertNull(proxy.requests.get(0).getParams());
        // Each subsequent page echoes the prior page's nextCursor as the cursor param.
        assertEquals("CURSOR1", proxy.requests.get(1).getParams().getMember("cursor").asString());
        assertEquals("CURSOR2", proxy.requests.get(2).getParams().getMember("cursor").asString());
    }

    @Test
    void listToolsSinglePageMakesOneCall() {
        var proxy = new FakeProxy(List.of(
                toolsResponse(List.of(tool("only")), null)));

        var tools = proxy.listTools();

        assertEquals(1, tools.size());
        assertEquals(1, proxy.requests.size());
        assertNull(proxy.requests.get(0).getParams());
    }

    @Test
    void listPromptsFollowsNextCursorAcrossPages() {
        var proxy = new FakeProxy(List.of(
                promptsResponse(List.of(prompt("p1")), "PC1"),
                promptsResponse(List.of(prompt("p2"), prompt("p3")), null)));

        var prompts = proxy.listPrompts();

        assertEquals(List.of("p1", "p2", "p3"),
                prompts.stream().map(PromptInfo::getName).toList());
        assertEquals(2, proxy.requests.size());
        assertEquals("PC1", proxy.requests.get(1).getParams().getMember("cursor").asString());
    }

    @Test
    void listToolsAbortsOnRepeatedCursor() {
        var proxy = new FakeProxy(List.of(
                toolsResponse(List.of(tool("a")), "SAME"),
                toolsResponse(List.of(tool("b")), "SAME")));

        assertThrows(IllegalStateException.class, proxy::listTools);
    }

    @Test
    void listToolsAbortsAtPageCap() {
        // A server that always advances the cursor never trips the repeated-cursor guard, so the
        // MAX_LIST_PAGES cap must stop it. Supply 1001 ever-advancing pages; only 1000 are fetched.
        var responses = new ArrayList<JsonRpcResponse>();
        for (int i = 0; i <= 1000; i++) {
            responses.add(toolsResponse(List.of(tool("t" + i)), "c" + i));
        }
        var proxy = new FakeProxy(responses);

        assertThrows(IllegalStateException.class, proxy::listTools);
        assertEquals(1000, proxy.requests.size());
    }

    @Test
    void listToolsTreatsBlankCursorAsEndOfList() {
        // A server that signals end-of-list with an empty cursor (instead of omitting it) must not
        // trigger an extra round-trip or trip the repeated-cursor guard.
        var proxy = new FakeProxy(List.of(
                toolsResponse(List.of(tool("a"), tool("b")), "")));

        var tools = proxy.listTools();

        assertEquals(List.of("a", "b"), tools.stream().map(ToolInfo::getName).toList());
        assertEquals(1, proxy.requests.size());
    }

    @Test
    void listToolsAbortsOnCyclingCursor() {
        // A -> B -> A is a non-advancing cycle the consecutive-only check would miss; the
        // seen-cursor guard must still abort it.
        var proxy = new FakeProxy(List.of(
                toolsResponse(List.of(tool("a")), "A"),
                toolsResponse(List.of(tool("b")), "B"),
                toolsResponse(List.of(tool("c")), "A")));

        assertThrows(IllegalStateException.class, proxy::listTools);
    }

    @Test
    void listToolsThrowsOnErrorResponse() {
        var proxy = new FakeProxy(List.of(errorResponse("boom")));

        var ex = assertThrows(RuntimeException.class, proxy::listTools);
        assertTrue(ex.getMessage().contains("boom"));
    }

    @Test
    void listToolsThrowsOnErrorOnLaterPage() {
        var proxy = new FakeProxy(List.of(
                toolsResponse(List.of(tool("a")), "c1"),
                errorResponse("kaboom")));

        assertThrows(RuntimeException.class, proxy::listTools);
        assertEquals(2, proxy.requests.size());
    }

    @Test
    void listToolsThrowsWhenResponseHasNeitherResultNorError() {
        var proxy = new FakeProxy(List.of(emptyResponse()));

        var ex = assertThrows(RuntimeException.class, proxy::listTools);
        assertTrue(ex.getMessage().contains("listing tools"));
    }

    @Test
    void listPromptsAbortsOnRepeatedCursor() {
        var proxy = new FakeProxy(List.of(
                promptsResponse(List.of(prompt("p1")), "SAME"),
                promptsResponse(List.of(prompt("p2")), "SAME")));

        assertThrows(IllegalStateException.class, proxy::listPrompts);
    }

    @Test
    void listToolsReturnsImmutableList() {
        var proxy = new FakeProxy(List.of(toolsResponse(List.of(tool("a")), null)));

        var tools = proxy.listTools();

        assertThrows(UnsupportedOperationException.class, () -> tools.add(tool("b")));
    }
}
