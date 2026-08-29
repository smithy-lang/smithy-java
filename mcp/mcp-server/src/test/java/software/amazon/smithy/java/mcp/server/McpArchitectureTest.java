/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;

class McpArchitectureTest {

    @Test
    void everyKnownVersionSelectsItsOwnProtocol() {
        for (var version : KnownProtocolVersion.values()) {
            assertEquals(version, BuiltInProtocols.protocol(version).version());
        }
    }

    @Test
    void unsupportedProtocolOperationUsesDefaultBehavior() {
        try (var engine = McpEngine.builder().build()) {
            var outcome = engine.execute(
                    new McpCall.ReadResource(
                            Document.of(1),
                            "test://resource",
                            McpMetadata.EMPTY),
                    context(KnownProtocolVersion.V2025_11_25));

            var failure = assertInstanceOf(McpOutcome.Failure.class, outcome);
            assertEquals(-32601, failure.error().code());
            assertEquals("Method not found: resources/read", failure.error().message());
        }
    }

    @Test
    void userUnsupportedOperationExceptionIsAnInternalError() {
        var interceptor = new McpInterceptor() {
            @Override
            public void readBeforeExecution(McpExecutionContext context) {
                throw new UnsupportedOperationException("user code failed");
            }
        };

        try (var engine = McpEngine.builder().interceptor(interceptor).build()) {
            var outcome = engine.execute(
                    new McpCall.Ping(Document.of(1), McpMetadata.EMPTY),
                    context(KnownProtocolVersion.V2025_11_25));

            var failure = assertInstanceOf(McpOutcome.Failure.class, outcome);
            assertEquals(-32603, failure.error().code());
            assertEquals("Internal error", failure.error().message());
        }
    }

    @Test
    void afterExecutionFailureDoesNotReplaceTheOriginalFailure() {
        var original = new IllegalStateException("original");
        var after = new IllegalArgumentException("after");
        var observed = new AtomicReference<RuntimeException>();
        var interceptor = new McpInterceptor() {
            @Override
            public void readBeforeExecution(McpExecutionContext context) {
                throw original;
            }

            @Override
            public void readAfterExecution(
                    McpExecutionContext context,
                    McpOutcome outcome,
                    RuntimeException error
            ) {
                throw after;
            }

            @Override
            public McpOutcome modifyAfterExecution(
                    McpExecutionContext context,
                    McpOutcome outcome,
                    RuntimeException error
            ) {
                observed.set(error);
                throw error;
            }
        };

        try (var engine = McpEngine.builder().interceptor(interceptor).build()) {
            engine.execute(
                    new McpCall.Ping(Document.of(1), McpMetadata.EMPTY),
                    context(KnownProtocolVersion.V2025_11_25));
        }

        assertSame(original, observed.get());
        assertSame(after, original.getSuppressed()[0]);
    }

    @Test
    void statelessProtocolDoesNotAccidentallyInheritLegacyPing() {
        try (var engine = McpEngine.builder().build()) {
            var metadata = new McpMetadata(
                    KnownProtocolVersion.V2026_07_28,
                    null,
                    Document.of(Map.of()),
                    Map.of());
            var outcome = engine.execute(
                    new McpCall.Ping(Document.of(1), metadata),
                    context(KnownProtocolVersion.V2026_07_28));

            var failure = assertInstanceOf(McpOutcome.Failure.class, outcome);
            assertEquals(-32601, failure.error().code());
        }
    }

    @Test
    void statelessProtocolRejectsRemovedMethodBeforeCapabilityParameterValidation() {
        try (var engine = McpEngine.builder().build()) {
            var request = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(Document.of(1))
                    .method(McpMethod.Standard.LOGGING_SET_LEVEL.wireName())
                    .params(statelessParams())
                    .build();

            var response = engine.execute(request, KnownProtocolVersion.V2026_07_28);

            assertEquals(-32601, response.getError().getCode());
        }
    }

    @Test
    void unsupportedVersionReportsRequestedAndSupportedVersions() {
        try (var engine = McpEngine.builder().build()) {
            var request = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(Document.of(1))
                    .method(McpMethod.Standard.SERVER_DISCOVER.wireName())
                    .build();

            var response = engine.execute(request, new UnknownProtocolVersion("v999.0.0"));

            assertEquals(-32022, response.getError().getCode());
            assertEquals("v999.0.0", response.getError().getData().getMember("requested").asString());
            assertEquals(
                    KnownProtocolVersion.supportedIdentifiers().size(),
                    response.getError().getData().getMember("supported").asList().size());
        }
    }

    @Test
    void extensionMethodsDecodeExecuteAndEncodeTypedParameters() {
        var extension = new EchoExtension();
        try (var engine = McpEngine.builder().addExtension(extension).build()) {
            var request = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(Document.of("extension-id"))
                    .method(extension.method())
                    .params(Document.of(Map.of("value", Document.of("hello"))))
                    .build();

            var response = engine.execute(request, KnownProtocolVersion.V2025_11_25);
            assertNull(response.getError());
            assertEquals("hello", response.getResult().getMember("echo").asString());

            var call = new McpCall.ExtensionCall<>(
                    Document.of(2),
                    extension,
                    new EchoParameters("outbound"),
                    McpMetadata.EMPTY);
            var encoded = new McpWireCodec(Map.of(extension.method(), extension)).encode(call);
            assertEquals(extension.method(), encoded.getMethod());
            assertEquals("outbound", encoded.getParams().getMember("value").asString());
        }
    }

    @Test
    void extensionCannotReplaceAStandardMethod() {
        var extension = new EchoExtension() {
            @Override
            public String method() {
                return McpMethod.Standard.PING.wireName();
            }
        };
        assertThrows(IllegalArgumentException.class, () -> McpEngine.builder().addExtension(extension));
    }

    @Test
    void extensionProtocolNegotiatesAndDispatchesThroughTheRegistry() {
        var protocol = protocol("2099-01-01", Set.of(McpMethod.Standard.PING));
        try (var engine = McpEngine.builder()
                .discoverProtocols(false)
                .addProtocol(protocol)
                .build()) {
            var request = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(Document.of(1))
                    .method(McpMethod.Standard.PING.wireName())
                    .build();

            var response = engine.execute(request, new UnknownProtocolVersion(protocol.id().identifier()));

            assertNull(response.getError());
            assertEquals(Map.of(), response.getResult().asStringMap());
        }
    }

    @Test
    void initializeNegotiatesAnExtensionProtocol() {
        var protocol = protocol("2099-01-01", Set.of(McpMethod.Standard.INITIALIZE));
        try (var engine = McpEngine.builder()
                .discoverProtocols(false)
                .addProtocol(protocol)
                .build()) {
            var request = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(Document.of(1))
                    .method(McpMethod.Standard.INITIALIZE.wireName())
                    .params(Document.of(Map.of(
                            "protocolVersion",
                            Document.of(protocol.id().identifier()),
                            "capabilities",
                            Document.of(Map.of()),
                            "clientInfo",
                            Document.of(Map.of()))))
                    .build();

            var response = engine.execute(request, null);

            assertNull(response.getError());
            assertEquals(
                    protocol.id().identifier(),
                    response.getResult().getMember("protocolVersion").asString());
        }
    }

    @Test
    void initializeFallsBackToLatestInitializationCapableProtocol() {
        try (var engine = McpEngine.builder().build()) {
            for (var requested : List.of(
                    new UnknownProtocolVersion("2099-01-01"),
                    KnownProtocolVersion.V2026_07_28)) {
                var response = engine.execute(initializeRequest(requested), null);

                assertNull(response.getError());
                assertEquals(
                        KnownProtocolVersion.V2025_11_25.identifier(),
                        response.getResult().getMember("protocolVersion").asString());
            }
        }
    }

    @Test
    void extensionProtocolCanBecomeTheInitializationFallback() {
        var protocol = protocol(
                "2099-01-01",
                Set.of(McpMethod.Standard.INITIALIZE),
                100);
        try (var engine = McpEngine.builder()
                .discoverProtocols(false)
                .addProtocol(protocol)
                .build()) {
            var response = engine.execute(
                    initializeRequest(new UnknownProtocolVersion("unsupported")),
                    null);

            assertNull(response.getError());
            assertEquals(
                    protocol.id().identifier(),
                    response.getResult().getMember("protocolVersion").asString());
        }
    }

    @Test
    void ambiguousInitializationFallbackPrioritiesFailConstruction() {
        var first = protocol(
                "2099-01-01",
                Set.of(McpMethod.Standard.INITIALIZE),
                100);
        var second = protocol(
                "2099-02-01",
                Set.of(McpMethod.Standard.INITIALIZE),
                100);

        var error = assertThrows(
                IllegalStateException.class,
                () -> McpEngine.builder()
                        .discoverProtocols(false)
                        .addProtocol(first)
                        .addProtocol(second)
                        .build());

        assertTrue(error.getMessage().contains("Ambiguous initialization fallback priority"));
        assertTrue(error.getMessage().contains(first.id().identifier()));
        assertTrue(error.getMessage().contains(second.id().identifier()));
    }

    @Test
    void statelessCacheHintsAreConfigurablePerMethod() {
        var cachePolicy = McpCachePolicy.builder()
                .hint(
                        McpMethod.Standard.TOOLS_LIST,
                        new McpCacheHint(30_000, McpCacheScope.PUBLIC))
                .build();
        try (var engine = McpEngine.builder().cachePolicy(cachePolicy).build()) {
            var response = engine.execute(
                    JsonRpcRequest.builder()
                            .jsonrpc("2.0")
                            .id(Document.of(1))
                            .method(McpMethod.Standard.TOOLS_LIST.wireName())
                            .params(statelessParams())
                            .build(),
                    KnownProtocolVersion.V2026_07_28);

            assertNull(response.getError());
            assertEquals(30_000, response.getResult().getMember("ttlMs").asNumber().longValue());
            assertEquals("public", response.getResult().getMember("cacheScope").asString());
        }
    }

    @Test
    void cacheHintRejectsNegativeTtl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new McpCacheHint(-1, McpCacheScope.PRIVATE));
    }

    @Test
    void protocolErrorsDoNotProduceResponsesForNotifications() {
        try (var engine = McpEngine.builder().build()) {
            var notification = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .method(McpMethod.Standard.NOTIFICATIONS_INITIALIZED.wireName())
                    .build();

            var response = engine.execute(
                    notification,
                    new UnknownProtocolVersion("unsupported-version"));

            assertNull(response);
        }
    }

    @Test
    void decodeErrorsDoNotProduceResponsesForNotifications() {
        try (var engine = McpEngine.builder().build()) {
            var notification = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .method(McpMethod.Standard.NOTIFICATIONS_INITIALIZED.wireName())
                    .params(Document.of("not-an-object"))
                    .build();

            assertNull(engine.execute(notification, KnownProtocolVersion.V2025_11_25));
        }
    }

    @Test
    void metricsObservationToleratesMistypedInitializeMetadata() {
        var observer = new McpMetricsObserver() {
            @Override
            public void onInitialize(
                    String method,
                    String extractedProtocolVersion,
                    boolean rootsListChanged,
                    boolean sampling,
                    boolean elicitation,
                    String clientName,
                    String clientTitle
            ) {}

            @Override
            public void onToolCall(String method, String toolName) {}
        };
        try (var engine = McpEngine.builder().metricsObserver(observer).build()) {
            var request = JsonRpcRequest.builder()
                    .jsonrpc("2.0")
                    .id(Document.of(1))
                    .method(McpMethod.Standard.INITIALIZE.wireName())
                    .params(Document.of(Map.of(
                            "protocolVersion",
                            Document.of(KnownProtocolVersion.V2025_11_25.identifier()),
                            "capabilities",
                            Document.of(Map.of("roots", Document.of("not-an-object"))),
                            "clientInfo",
                            Document.of(42))))
                    .build();

            assertNull(engine.execute(request, KnownProtocolVersion.V2025_11_25).getError());
        }
    }

    @Test
    void programmaticProtocolConflictFailsWithoutAnOverride() {
        var protocol = protocol(
                KnownProtocolVersion.V2025_11_25.identifier(),
                Set.of(McpMethod.Standard.PING));

        var builder = McpEngine.builder()
                .discoverProtocols(false)
                .addProtocol(protocol);

        var error = assertThrows(IllegalStateException.class, builder::build);
        assertTrue(error.getMessage().contains(protocol.id().identifier()));
    }

    @Test
    void explicitOverrideReplacesABuiltInProtocol() {
        var protocol = protocol(KnownProtocolVersion.V2025_11_25.identifier(), Set.of());
        try (var engine = McpEngine.builder()
                .discoverProtocols(false)
                .overrideProtocol(protocol)
                .build()) {
            var response = engine.execute(
                    JsonRpcRequest.builder()
                            .jsonrpc("2.0")
                            .id(Document.of(1))
                            .method(McpMethod.Standard.PING.wireName())
                            .build(),
                    KnownProtocolVersion.V2025_11_25);

            assertEquals(-32601, response.getError().getCode());
        }
    }

    @Test
    void builtInOverrideRetainsTheVersionsInitializationPriority() {
        var protocol = protocol(
                KnownProtocolVersion.V2025_11_25.identifier(),
                Set.of(McpMethod.Standard.INITIALIZE));
        try (var engine = McpEngine.builder()
                .discoverProtocols(false)
                .overrideProtocol(protocol)
                .build()) {
            var response = engine.execute(
                    initializeRequest(new UnknownProtocolVersion("unsupported")),
                    null);

            assertNull(response.getError());
            assertEquals(
                    KnownProtocolVersion.V2025_11_25.identifier(),
                    response.getResult().getMember("protocolVersion").asString());
        }
    }

    @Test
    void spiConflictWithANewBuiltInFailsUnlessExplicitlyOverridden() {
        var protocol = protocol(
                KnownProtocolVersion.V2025_11_25.identifier(),
                Set.of(McpMethod.Standard.PING));
        McpProtocolProvider provider = () -> List.of(protocol);

        var error = assertThrows(
                IllegalStateException.class,
                () -> McpProtocolRegistry.create(List.of(), List.of(), List.of(provider)));
        assertTrue(error.getMessage().contains("SPI provider"));

        var registry = McpProtocolRegistry.create(
                List.of(),
                List.of(protocol),
                List.of(provider));
        assertSame(protocol, registry.require(KnownProtocolVersion.V2025_11_25));
    }

    @Test
    void conflictingSpiProvidersFailDeterministically() {
        var first = protocol("2099-01-01", Set.of(McpMethod.Standard.PING));
        var second = protocol("2099-01-01", Set.of(McpMethod.Standard.TOOLS_LIST));
        McpProtocolProvider firstProvider = () -> List.of(first);
        McpProtocolProvider secondProvider = () -> List.of(second);

        var error = assertThrows(
                IllegalStateException.class,
                () -> McpProtocolRegistry.create(
                        List.of(),
                        List.of(),
                        List.of(firstProvider, secondProvider)));

        assertTrue(error.getMessage().contains("2099-01-01"));
    }

    @Test
    void discoversProtocolProvidersWithServiceLoader(@TempDir Path temporaryDirectory) throws Exception {
        var serviceDirectory = temporaryDirectory.resolve("META-INF/services");
        Files.createDirectories(serviceDirectory);
        Files.writeString(
                serviceDirectory.resolve(McpProtocolProvider.class.getName()),
                TestProtocolProvider.class.getName());

        try (var classLoader = new URLClassLoader(
                new java.net.URL[] {temporaryDirectory.toUri().toURL()},
                getClass().getClassLoader())) {
            var registry = McpProtocolRegistry.create(List.of(), List.of(), classLoader);

            assertEquals(
                    TestProtocolProvider.ID,
                    registry.require(new UnknownProtocolVersion(TestProtocolProvider.ID)).id().identifier());
        }
    }

    @Test
    void protocolRegistryReturnsSingletonImplementations() {
        assertSame(
                BuiltInProtocols.protocol(KnownProtocolVersion.V2026_07_28),
                BuiltInProtocols.protocol(KnownProtocolVersion.V2026_07_28));
    }

    private McpRequestContext context(KnownProtocolVersion version) {
        return new McpRequestContext(version, McpTransportContext.STDIO, Context.create());
    }

    private TestProtocol protocol(String identifier, Set<McpMethod.Standard> methods) {
        return protocol(identifier, methods, 0);
    }

    private TestProtocol protocol(
            String identifier,
            Set<McpMethod.Standard> methods,
            int initializationPriority
    ) {
        return new TestProtocol(
                McpProtocolId.of(identifier),
                methods,
                initializationPriority);
    }

    private Document statelessParams() {
        return Document.of(Map.of(
                "_meta",
                Document.of(Map.of(
                        McpWireNames.PROTOCOL_VERSION,
                        Document.of(KnownProtocolVersion.V2026_07_28.identifier()),
                        McpWireNames.CLIENT_CAPABILITIES,
                        Document.of(Map.of())))));
    }

    private JsonRpcRequest initializeRequest(ProtocolVersion requestedVersion) {
        return JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(Document.of(1))
                .method(McpMethod.Standard.INITIALIZE.wireName())
                .params(Document.of(Map.of(
                        "protocolVersion",
                        Document.of(requestedVersion.identifier()),
                        "capabilities",
                        Document.of(Map.of()),
                        "clientInfo",
                        Document.of(Map.of()))))
                .build();
    }

    private record EchoParameters(String value) {}

    private record TestProtocol(
            McpProtocolId id,
            Set<McpMethod.Standard> supportedMethods,
            int initializationPriority) implements ExtensionMcpProtocol {
        private TestProtocol {
            supportedMethods = Set.copyOf(supportedMethods);
        }
    }

    public static final class TestProtocolProvider implements McpProtocolProvider {
        private static final String ID = "2099-service-loader";

        @Override
        public List<ExtensionMcpProtocol> protocols() {
            return List.of(new TestProtocol(
                    McpProtocolId.of(ID),
                    Set.of(McpMethod.Standard.PING),
                    0));
        }
    }

    private static class EchoExtension implements McpExtensionMethod<EchoParameters> {
        @Override
        public String method() {
            return "example/echo";
        }

        @Override
        public EchoParameters decode(Document params) {
            return new EchoParameters(params.getMember("value").asString());
        }

        @Override
        public Document encode(EchoParameters params) {
            return Document.of(Map.of("value", Document.of(params.value())));
        }

        @Override
        public McpOutcome execute(
                McpCall.ExtensionCall<EchoParameters> call,
                McpRequestContext context
        ) {
            return new McpOutcome.Success(
                    call.id(),
                    Document.of(Map.of("echo", Document.of(call.parameters().value()))));
        }
    }
}
