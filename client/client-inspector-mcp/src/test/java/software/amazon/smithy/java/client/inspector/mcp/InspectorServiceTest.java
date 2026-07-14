/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicschemas.StructDocument;
import software.amazon.smithy.java.server.Operation;

/**
 * Exercises {@link InspectorService} the same way the MCP server does: it builds each operation's
 * input as a document-backed {@link StructDocument} via {@code inputBuilder()} and invokes
 * {@code operation.function().apply(input, null)}, then asserts the result and side effects on
 * {@link InspectorState}.
 */
class InspectorServiceTest {

    @Test
    void assemblesModelAndExposesAllTools() {
        var service = new InspectorService(new InspectorState());
        var names = service.getAllOperations().stream().map(Operation::name).toList();
        // Spot-check one from each tier.
        assertTrue(names.contains("ListCalls"), names.toString());
        assertTrue(names.contains("QueryLogs"), names.toString());
        assertTrue(names.contains("InjectFault"), names.toString());
        assertTrue(names.contains("SetBreakpoint"), names.toString());
        assertEquals(16, names.size(), names.toString());
    }

    @Test
    void recordsAndListsCalls() {
        var state = new InspectorState();
        var service = new InspectorService(state);

        // Seed a recorded call directly (as the interceptor would).
        var record = new InspectorState.CallRecord();
        record.callId = state.nextCallId();
        record.operation = "GetWidget";
        record.service = "com.example";
        record.status = "error";
        record.errorType = "software.amazon.example.NotFound";
        record.attempts.set(2);
        record.durationMillis = 42;
        state.addCall(record);

        var listOut = invoke(service, "ListCalls", Document.of(Map.of("errorOnly", Document.of(true))));
        var calls = listOut.getMember("calls").asList();
        assertEquals(1, calls.size());
        assertEquals("GetWidget", calls.get(0).getMember("operation").asString());
        assertEquals(2, calls.get(0).getMember("attempts").asNumber().intValue());

        var getOut = invoke(service, "GetCall", Document.of(Map.of("callId", Document.of(record.callId))));
        assertEquals("error", getOut.getMember("call").getMember("status").asString());
        assertEquals("software.amazon.example.NotFound",
                getOut.getMember("call").getMember("errorType").asString());
    }

    @Test
    void injectAndListAndClearRules() {
        var state = new InspectorState();
        var service = new InspectorService(state);

        var injectOut = invoke(service,
                "InjectFault",
                Document.of(Map.of(
                        "operation",
                        Document.of("PutWidget"),
                        "httpStatus",
                        Document.of(500),
                        "maxHits",
                        Document.of(3))));
        var ruleId = injectOut.getMember("ruleId").asString();
        assertNotNull(ruleId);

        var listOut = invoke(service, "ListRules", Document.of(Map.of()));
        assertEquals(1, listOut.getMember("rules").asList().size());
        assertEquals("FAULT", listOut.getMember("rules").asList().get(0).getMember("kind").asString());

        // The interceptor should now find and consume the fault rule.
        var fault = state.consumeRule(InspectorState.Rule.Kind.FAULT, "PutWidget");
        assertNotNull(fault);
        assertEquals(500, fault.httpStatus);

        var clearOut = invoke(service, "ClearRule", Document.of(Map.of("ruleId", Document.of(ruleId))));
        assertEquals(1, clearOut.getMember("removed").asNumber().intValue());
    }

    @Test
    void queryLogsFiltersByLevelAndContent() {
        var state = new InspectorState();
        var service = new InspectorService(state);
        state.addLog(new InspectorState.LogEntry("INFO", "a.b.C", "connecting to endpoint", 1L, null, null));
        state.addLog(new InspectorState.LogEntry("ERROR", "a.b.C", "handshake failed", 2L, "stack...", null));

        var out = invoke(service, "QueryLogs", Document.of(Map.of("minLevel", Document.of("WARN"))));
        var logs = out.getMember("logs").asList();
        assertEquals(1, logs.size());
        assertEquals("handshake failed", logs.get(0).getMember("message").asString());
    }

    @Test
    void telemetryAggregatesByOperation() {
        var state = new InspectorState();
        var service = new InspectorService(state);
        for (int i = 0; i < 3; i++) {
            var r = new InspectorState.CallRecord();
            r.callId = state.nextCallId();
            r.operation = "ListWidgets";
            r.status = i == 0 ? "error" : "success";
            r.attempts.set(1);
            r.durationMillis = 10L * (i + 1);
            state.addCall(r);
        }
        var out = invoke(service, "QueryTelemetry", Document.of(Map.of()));
        var telemetry = out.getMember("telemetry").asList();
        assertEquals(1, telemetry.size());
        var t = telemetry.get(0);
        assertEquals("ListWidgets", t.getMember("operation").asString());
        assertEquals(3, t.getMember("callCount").asNumber().intValue());
        assertEquals(1, t.getMember("errorCount").asNumber().intValue());
    }

    @Test
    void breakpointParksAndResumesAcrossThreads() throws Exception {
        var state = new InspectorState();
        var service = new InspectorService(state);

        // Arm a breakpoint at beforeTransmit for PutWidget.
        var setOut = invoke(service,
                "SetBreakpoint",
                Document.of(Map.of(
                        "phase",
                        Document.of("beforeTransmit"),
                        "operation",
                        Document.of("PutWidget"),
                        "timeoutMillis",
                        Document.of(5000L))));
        assertNotNull(setOut.getMember("breakpointId").asString());

        // Simulate a client thread hitting the breakpoint: match + park on the latch.
        var bp = state.matchBreakpoint("beforeTransmit", "PutWidget");
        assertNotNull(bp);
        var resumedPatch = new InspectorState.RequestPatch[1];
        var parked = new AtomicBoolean(false);
        var frame = new InspectorState.PausedFrame();
        frame.operation = "PutWidget";
        frame.phase = "beforeTransmit";
        frame.parkedAtMillis = System.currentTimeMillis();
        var frameId = state.parkFrame(frame);
        var clientThread = CompletableFuture.runAsync(() -> {
            try {
                parked.set(true);
                frame.latch.await(5, TimeUnit.SECONDS);
                resumedPatch[0] = frame.patch;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Wait until the agent can see the parked frame.
        long deadline = System.currentTimeMillis() + 2000;
        List<Document> frames;
        do {
            frames = invoke(service, "ListPausedFrames", Document.of(Map.of()))
                    .getMember("frames")
                    .asList();
        } while (frames.isEmpty() && System.currentTimeMillis() < deadline);
        assertEquals(1, frames.size());
        assertEquals("PutWidget", frames.get(0).getMember("operation").asString());

        // Agent inspects then resumes with a header patch.
        var detail = invoke(service,
                "GetPausedFrame",
                Document.of(Map.of("frameId", Document.of(frameId))));
        assertEquals("beforeTransmit", detail.getMember("frame").getMember("phase").asString());

        var resumeOut = invoke(service,
                "Resume",
                Document.of(Map.of(
                        "frameId",
                        Document.of(frameId),
                        "setHeaders",
                        Document.of(Map.of("x-debug", Document.of("1"))))));
        assertTrue(resumeOut.getMember("resumed").asBoolean());

        clientThread.get(5, TimeUnit.SECONDS);
        assertTrue(parked.get());
        assertNotNull(resumedPatch[0]);
        assertEquals("1", resumedPatch[0].setHeaders.get("x-debug"));

        // Frame is no longer parked.
        assertTrue(invoke(service, "ListPausedFrames", Document.of(Map.of()))
                .getMember("frames")
                .asList()
                .isEmpty());
    }

    @Test
    void resumeAllReleasesEverything() {
        var state = new InspectorState();
        var service = new InspectorService(state);
        for (int i = 0; i < 3; i++) {
            var f = new InspectorState.PausedFrame();
            f.operation = "Op" + i;
            f.phase = "beforeTransmit";
            state.parkFrame(f);
        }
        var out = invoke(service, "ResumeAll", Document.of(Map.of()));
        assertEquals(3, out.getMember("resumed").asNumber().intValue());
        assertFalse(state.listPausedFrames().size() > 0);
    }

    /** Invoke an operation the way McpService does: build the input struct, call function().apply(input, null). */
    private static Document invoke(InspectorService service, String operationName, Document input) {
        Operation<StructDocument, StructDocument> op = service.getOperation(operationName);
        assertNotNull(op, "operation not found: " + operationName);
        var inputStruct = input.asShape(op.getApiOperation().inputBuilder());
        return op.function().apply(inputStruct, null);
    }
}
