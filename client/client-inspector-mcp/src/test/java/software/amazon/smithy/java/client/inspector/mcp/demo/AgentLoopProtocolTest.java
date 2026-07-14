/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.server.Server;

/**
 * Drives the full agent loop over the <em>real</em> MCP JSON-RPC wire protocol: the same messages
 * Claude Code would exchange with {@link InspectorDemoServer}. It initializes, lists tools, calls
 * the workload tool to generate real SDK traffic, then uses the inspector tools to observe and
 * control it — exactly the sequence an agent performs, but scripted and asserted.
 */
class AgentLoopProtocolTest {

    private static final JsonCodec CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder()
                    .serializeTypeInDocuments(false)
                    .useJsonName(true)
                    .build())
            .build();

    private PipeInput input;
    private LineOutput output;
    private Server server;
    private int id;

    @BeforeEach
    void setUp() {
        input = new PipeInput();
        output = new LineOutput();
        server = InspectorDemoServer.create(input, output);
        server.start();
        // MCP handshake, negotiating the version that includes structuredContent tool results.
        var init = rpc("initialize",
                Document.of(Map.of(
                        "protocolVersion",
                        Document.of("2025-06-18"))));
        assertNotNull(init.getResult());
        assertEquals("2025-06-18", init.getResult().getMember("protocolVersion").asString());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.shutdown().join();
        }
        input.close();
        output.close();
    }

    @Test
    void listsInspectorToolsOnly() {
        var tools = toolNames();
        // A representative inspector tool from each tier.
        assertTrue(tools.contains("ListCalls"), tools.toString());
        assertTrue(tools.contains("QueryTelemetry"), tools.toString());
        assertTrue(tools.contains("QueryLogs"), tools.toString());
        assertTrue(tools.contains("InjectFault"), tools.toString());
        assertTrue(tools.contains("SetBreakpoint"), tools.toString());
        // There is deliberately NO traffic-generation tool — the agent inspects traffic it
        // cannot author.
        assertFalse(tools.contains("MakeSprocketCall"), tools.toString());
    }

    @Test
    void agentInspectsPreGeneratedTraffic() {
        // The demo fired a fixed 3-call mix before serving: success, retried-429, persistent-500.
        var calls = callTool("ListCalls", Map.of()).getMember("calls").asList();
        assertEquals(3, calls.size());

        // The successful call: real wire request/response + phase timings.
        var success = calls.stream()
                .filter(c -> "success".equals(c.getMember("status").asString()))
                .findFirst()
                .orElseThrow();
        var detail = callTool("GetCall",
                Map.of("callId", Document.of(success.getMember("callId").asNumber().longValue())))
                .getMember("call");
        assertEquals("POST", detail.getMember("requestMethod").asString());
        assertEquals(200, detail.getMember("responseStatus").asNumber().intValue());
        assertNotNull(detail.getMember("phaseTimings").getMember("serialize"));

        // Telemetry aggregates all three: 3 calls, 1 error (the persistent 500).
        var telemetry = callTool("QueryTelemetry", Map.of()).getMember("telemetry").asList();
        assertEquals(1, telemetry.size());
        assertEquals(3, telemetry.get(0).getMember("callCount").asNumber().intValue());
        assertEquals(1, telemetry.get(0).getMember("errorCount").asNumber().intValue());

        // The SDK's own internal logs were captured during those calls.
        var logs = callTool("QueryLogs", Map.of("minLevel", Document.of("TRACE"))).getMember("logs").asList();
        assertFalse(logs.isEmpty(), "expected captured SDK logs");
    }

    @Test
    void agentSeesTheRetriedCall() {
        // Exactly one of the fixed calls retried (429 → 200): it should show 2 attempts + success.
        var calls = callTool("ListCalls", Map.of()).getMember("calls").asList();
        var retried = calls.stream()
                .map(c -> callTool("GetCall",
                        Map.of("callId", Document.of(c.getMember("callId").asNumber().longValue())))
                        .getMember("call"))
                .filter(d -> d.getMember("attempts").asNumber().intValue() == 2)
                .toList();
        assertEquals(1, retried.size(), "exactly one call should have retried");
        assertEquals("success", retried.get(0).getMember("status").asString());
    }

    @Test
    void agentSeesThePersistentFailure() {
        // The persistent 500 is recorded as an error the agent can find via ListCalls(errorOnly).
        var errorCalls = callTool("ListCalls", Map.of("errorOnly", Document.of(true)))
                .getMember("calls")
                .asList();
        assertEquals(1, errorCalls.size());
        assertEquals("error", errorCalls.get(0).getMember("status").asString());
        assertNotNull(errorCalls.get(0).getMember("errorType"));
    }

    // ---- MCP wire helpers ----

    private Set<String> toolNames() {
        var result = rpc("tools/list", Document.of(Map.of())).getResult();
        var names = new HashSet<String>();
        for (var tool : result.getMember("tools").asList()) {
            names.add(tool.getMember("name").asString());
        }
        return names;
    }

    /** Call an MCP tool and return its structuredContent result document. */
    private Document callTool(String name, Map<String, Document> arguments) {
        var params = Document.of(Map.of(
                "name",
                Document.of(name),
                "arguments",
                Document.of(arguments)));
        var response = rpc("tools/call", params);
        assertNotNull(response.getResult(),
                "tool " + name + " errored: "
                        + (response.getError() != null ? response.getError().getMessage() : "unknown"));
        return response.getResult().getMember("structuredContent");
    }

    private JsonRpcResponse rpc(String method, Document params) {
        var request = JsonRpcRequest.builder()
                .id(Document.of(id++))
                .method(method)
                .params(params)
                .jsonrpc("2.0")
                .build();
        input.write(CODEC.serializeToString(request) + "\n");
        var line = output.readLine();
        return CODEC.deserializeShape(line, JsonRpcResponse.builder());
    }

    // ---- In-memory stdio streams speaking the MCP framing (one JSON object per line) ----

    private static final class PipeInput extends InputStream {
        private final BlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>();
        private byte[] current;
        private int pos;

        void write(String s) {
            chunks.add(s.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public int read() {
            if (!ensure()) {
                return -1;
            }
            return current[pos++] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (!ensure()) {
                return -1;
            }
            int n = Math.min(len, current.length - pos);
            System.arraycopy(current, pos, b, off, n);
            pos += n;
            return n;
        }

        private boolean ensure() {
            try {
                while (current == null || pos == current.length) {
                    current = chunks.take();
                    pos = 0;
                }
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private static final class LineOutput extends OutputStream {
        private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private final List<Byte> buffer = new ArrayList<>();

        @Override
        public synchronized void write(int b) {
            if (b == '\n') {
                flushLine();
            } else {
                buffer.add((byte) b);
            }
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            for (int i = off; i < off + len; i++) {
                write(b[i]);
            }
        }

        private void flushLine() {
            var bytes = new byte[buffer.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = buffer.get(i);
            }
            buffer.clear();
            lines.add(new String(bytes, StandardCharsets.UTF_8));
        }

        String readLine() {
            try {
                var line = lines.poll(5, java.util.concurrent.TimeUnit.SECONDS);
                if (line == null) {
                    throw new AssertionError("No MCP response within 5 seconds");
                }
                return line;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

}
