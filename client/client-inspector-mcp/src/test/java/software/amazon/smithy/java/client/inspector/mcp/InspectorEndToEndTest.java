/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.smithy.java.aws.client.auth.scheme.sigv4.SigV4AuthScheme;
import software.amazon.smithy.java.aws.client.core.settings.RegionSetting;
import software.amazon.smithy.java.aws.client.restjson.RestJsonClientProtocol;
import software.amazon.smithy.java.aws.sdkv2.auth.SdkCredentialsResolver;
import software.amazon.smithy.java.client.core.auth.scheme.AuthSchemeResolver;
import software.amazon.smithy.java.client.http.mock.MockPlugin;
import software.amazon.smithy.java.client.http.mock.MockQueue;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.dynamicclient.DynamicClient;
import software.amazon.smithy.java.dynamicschemas.StructDocument;
import software.amazon.smithy.java.endpoints.EndpointResolver;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.retries.StandardRetryStrategy;
import software.amazon.smithy.java.server.Operation;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * A contrived but real end-to-end exercise of the inspector tooling: a {@link DynamicClient} for a
 * fake "Sprockets" service is driven through a mock HTTP transport with the {@link InspectorPlugin}
 * attached, and then the {@link InspectorService} MCP tools are used — exactly as an agent would —
 * to observe the recorded calls, query telemetry and logs, and inject a fault.
 */
class InspectorEndToEndTest {

    private static final Model MODEL = Model.assembler()
            .addUnparsedModel("test.smithy", """
                    $version: "2"
                    namespace smithy.example

                    @aws.protocols#restJson1
                    @aws.auth#sigv4(name: "sprockets")
                    service Sprockets {
                        operations: [GetSprocket]
                    }

                    @http(method: "POST", uri: "/s")
                    @readonly
                    operation GetSprocket {
                        input := {
                            id: String
                        }
                        output := {
                            id: String
                        }
                    }
                    """)
            .discoverModels()
            .assemble()
            .unwrap();

    private static final ShapeId SERVICE = ShapeId.from("smithy.example#Sprockets");

    private InspectorState state;
    private InspectorService inspector;
    private InspectorLogHandler logHandler;

    @BeforeEach
    void setUp() {
        state = new InspectorState();
        inspector = new InspectorService(state);
        logHandler = InspectorLogHandler.attachToRoot(state);
    }

    @AfterEach
    void tearDown() {
        logHandler.close();
    }

    @Test
    void recordsARealCallEndToEnd() {
        var queue = new MockQueue();
        queue.enqueue(HttpResponse.create()
                .setBody(DataStream.ofString("{\"id\":\"10\"}"))
                .setStatusCode(200)
                .toUnmodifiable());
        var client = createClient(queue);

        // Drive a real call through the real client pipeline.
        client.call("GetSprocket", Document.of(Map.of("id", Document.of("10"))));

        // The agent lists calls via the MCP tool and sees the recorded call.
        var calls = tool("ListCalls", Map.of()).getMember("calls").asList();
        assertEquals(1, calls.size());
        var summary = calls.get(0);
        assertEquals("GetSprocket", summary.getMember("operation").asString());
        assertEquals("success", summary.getMember("status").asString());

        // GetCall exposes the wire request/response and per-phase timings.
        long callId = summary.getMember("callId").asNumber().longValue();
        var detail = tool("GetCall", Map.of("callId", Document.of(callId))).getMember("call");
        assertEquals("POST", detail.getMember("requestMethod").asString());
        assertEquals(200, detail.getMember("responseStatus").asNumber().intValue());
        assertNotNull(detail.getMember("requestHeaders"));
        var timings = detail.getMember("phaseTimings");
        assertNotNull(timings, "phase timings should be recorded");
        assertNotNull(timings.getMember("serialize"), "serialize phase should be timed");
        assertNotNull(timings.getMember("deserialize"), "deserialize phase should be timed");
    }

    @Test
    void telemetryAggregatesRealCallsIncludingRetries() {
        var queue = new MockQueue();
        // First attempt fails (retryable 429), second succeeds -> two attempts, one error.
        queue.enqueue(HttpResponse.create()
                .setBody(DataStream.ofString("{\"__type\":\"InvalidSprocketId\"}"))
                .setStatusCode(429)
                .toUnmodifiable());
        queue.enqueue(HttpResponse.create()
                .setBody(DataStream.ofString("{\"id\":\"10\"}"))
                .setStatusCode(200)
                .toUnmodifiable());
        var client = createClient(queue);

        client.call("GetSprocket", Document.of(Map.of("id", Document.of("10"))));

        var telemetry = tool("QueryTelemetry", Map.of()).getMember("telemetry").asList();
        assertEquals(1, telemetry.size());
        var t = telemetry.get(0);
        assertEquals("GetSprocket", t.getMember("operation").asString());
        assertEquals(1, t.getMember("callCount").asNumber().intValue());
        // Two transmit attempts were made for the one call.
        var detail = tool("GetCall", Map.of("callId", Document.of(1L))).getMember("call");
        assertEquals(2, detail.getMember("attempts").asNumber().intValue());
    }

    @Test
    void injectFaultShortCircuitsARealCall() {
        var queue = new MockQueue();
        // Enqueue a would-be-success; the injected fault should prevent it from being used.
        queue.enqueue(HttpResponse.create()
                .setBody(DataStream.ofString("{\"id\":\"10\"}"))
                .setStatusCode(200)
                .toUnmodifiable());
        var client = createClient(queue);

        // Agent arms a fault for the next GetSprocket call.
        var ruleId = tool("InjectFault",
                Map.of(
                        "operation",
                        Document.of("GetSprocket"),
                        "throwable",
                        Document.of("java.io.IOException"),
                        "maxHits",
                        Document.of(1)))
                .getMember("ruleId")
                .asString();
        assertNotNull(ruleId);

        // The real call now fails because the interceptor short-circuits transmit.
        assertThrows(RuntimeException.class,
                () -> client.call("GetSprocket", Document.of(Map.of("id", Document.of("10")))));

        // Rule was consumed.
        assertTrue(tool("ListRules", Map.of()).getMember("rules").asList().isEmpty());

        // The injected failure is finalized as an error (not left "in-progress"): the fix moves
        // finalization into interceptCall, which catches the modifyBeforeTransmit short-circuit.
        var calls = tool("ListCalls", Map.of()).getMember("calls").asList();
        assertEquals(1, calls.size());
        var callId = calls.get(0).getMember("callId").asNumber().longValue();
        var detail = tool("GetCall", Map.of("callId", Document.of(callId))).getMember("call");
        assertEquals("error", detail.getMember("status").asString());
        assertNotNull(detail.getMember("errorType"), "recorded call should have an error type");
        assertEquals(1L, detail.getMember("phaseTimings").getMember("faultInjected").asNumber().longValue());

        // Telemetry reflects the error too.
        var telemetry = tool("QueryTelemetry", Map.of()).getMember("telemetry").asList();
        assertEquals(1, telemetry.get(0).getMember("errorCount").asNumber().intValue());
        // Request bytes were captured before the transmit-stage fault short-circuited the call.
        assertTrue(detail.getMember("requestContentLength").asNumber().longValue() > 0,
                "request bytes should be captured even on the fault path");
    }

    @Test
    void capturesTheSdksOwnInternalLogs() {
        var queue = new MockQueue();
        queue.enqueue(HttpResponse.create()
                .setBody(DataStream.ofString("{\"id\":\"10\"}"))
                .setStatusCode(200)
                .toUnmodifiable());
        var client = createClient(queue);

        client.call("GetSprocket", Document.of(Map.of("id", Document.of("10"))));

        // The SDK emits internal debug/trace logs during a call; with no log backend on the test
        // classpath they route through JUL, which our handler mirrors into queryable state.
        var logs = tool("QueryLogs", Map.of("minLevel", Document.of("TRACE"))).getMember("logs").asList();
        assertFalse(logs.isEmpty(), "expected to capture the SDK's internal log output");
        // Captured records carry real logger + level metadata (not empty placeholders).
        var first = logs.get(0);
        assertNotNull(first.getMember("level"), "captured log should have a level");
        assertNotNull(first.getMember("logger"), "captured log should have a logger name");
    }

    // ---- helpers ----

    /** Invoke an inspector MCP tool by name, as McpService would. */
    private Document tool(String operationName, Map<String, Document> args) {
        Operation<StructDocument, StructDocument> op = inspector.getOperation(operationName);
        var input = Document.of(args).asShape(op.getApiOperation().inputBuilder());
        return op.function().apply(input, null);
    }

    private DynamicClient createClient(MockQueue queue) {
        return createClient(queue, true);
    }

    private DynamicClient createClient(MockQueue queue, boolean withInspector) {
        var credentials = AwsBasicCredentials.create("access_key", "secret_key");
        var builder = DynamicClient.builder()
                .model(MODEL)
                .serviceId(SERVICE)
                .protocol(new RestJsonClientProtocol(SERVICE))
                // Real retry strategy: a 429 retries only because the operation is @readonly and
                // ApplyModelRetryInfoPlugin marks it isRetrySafe=YES — not an unconditional strategy.
                .retryStrategy(StandardRetryStrategy.builder()
                        .maxAttempts(3)
                        .backoffBaseDelay(Duration.ofMillis(1))
                        .build())
                .addPlugin(MockPlugin.builder().addQueue(queue).build())
                .endpointResolver(EndpointResolver.staticEndpoint(URI.create("http://localhost")))
                .putConfig(RegionSetting.REGION, "us-west-2")
                .putSupportedAuthSchemes(new SigV4AuthScheme("sprockets"))
                .authSchemeResolver(AuthSchemeResolver.DEFAULT)
                .addIdentityResolver(new SdkCredentialsResolver(StaticCredentialsProvider.create(credentials)));
        if (withInspector) {
            builder.addPlugin(new InspectorPlugin(state));
        }
        return builder.build();
    }

    /**
     * Regression guard for the failure mode the cold-baseline eval surfaced: an observing
     * interceptor ordered before the retry-classification plugin can suppress model-based retry
     * (because {@code modifyBeforeAttemptCompletion} has no per-interceptor error isolation).
     * The inspector pins a LATE plugin phase, so attaching it must NOT change the attempt count.
     */
    @Test
    void inspectorDoesNotAlterRetryBehavior() {
        // Baseline: same 429→200 sequence with NO inspector attached. We can't read attempts
        // without an observer, so we assert the call still succeeds (retry happened) ...
        var baselineQueue = new MockQueue();
        baselineQueue.enqueue(HttpResponse.create()
                .setBody(DataStream.ofString("{\"__type\":\"InvalidSprocketId\"}"))
                .setStatusCode(429)
                .toUnmodifiable());
        baselineQueue.enqueue(HttpResponse.create()
                .setBody(DataStream.ofString("{\"id\":\"10\"}"))
                .setStatusCode(200)
                .toUnmodifiable());
        var noInspector = createClient(baselineQueue, false);
        var out = noInspector.call("GetSprocket", Document.of(Map.of("id", Document.of("10"))));
        assertEquals("10", out.getMember("id").asString(), "retry should still succeed without inspector");

        // ... and with the inspector attached, the recorded attempt count is 2 — proving the
        // observer neither suppressed nor added a retry.
        var withQueue = new MockQueue();
        withQueue.enqueue(HttpResponse.create()
                .setBody(DataStream.ofString("{\"__type\":\"InvalidSprocketId\"}"))
                .setStatusCode(429)
                .toUnmodifiable());
        withQueue.enqueue(HttpResponse.create()
                .setBody(DataStream.ofString("{\"id\":\"10\"}"))
                .setStatusCode(200)
                .toUnmodifiable());
        createClient(withQueue, true).call("GetSprocket", Document.of(Map.of("id", Document.of("10"))));
        var calls = tool("ListCalls", Map.of()).getMember("calls").asList();
        var detail = tool("GetCall",
                Map.of("callId",
                        Document.of(
                                calls.get(0).getMember("callId").asNumber().longValue())))
                .getMember("call");
        assertEquals(2,
                detail.getMember("attempts").asNumber().intValue(),
                "inspector must observe the real 2-attempt retry, not alter it");
    }

}
