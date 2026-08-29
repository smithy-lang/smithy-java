/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.mcp.conformance.model.TestCustomHeaderOutput;
import software.amazon.smithy.java.mcp.conformance.model.TestLoggingToolOutput;
import software.amazon.smithy.java.mcp.conformance.model.TestMissingCapabilityOutput;
import software.amazon.smithy.java.mcp.conformance.model.TestSimpleTextOutput;
import software.amazon.smithy.java.mcp.conformance.model.TestStreamingElicitationOutput;
import software.amazon.smithy.java.mcp.conformance.service.ConformanceService;
import software.amazon.smithy.java.mcp.conformance.service.TestCustomHeaderOperation;
import software.amazon.smithy.java.mcp.conformance.service.TestErrorHandlingOperation;
import software.amazon.smithy.java.mcp.conformance.service.TestLoggingToolOperation;
import software.amazon.smithy.java.mcp.conformance.service.TestMissingCapabilityOperation;
import software.amazon.smithy.java.mcp.conformance.service.TestSimpleTextOperation;
import software.amazon.smithy.java.mcp.conformance.service.TestStreamingElicitationOperation;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;

@Tag("conformance")
class McpConformanceTest {
    private static final String CONFORMANCE_VERSION = "0.2.0-alpha.11";
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(15);
    private static final JsonCodec CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder()
                    .serializeTypeInDocuments(false)
                    .useJsonName(true)
                    .build())
            .build();

    private static HttpServer directServer;
    private static HttpServer proxyServer;
    private static String directServerUrl;
    private static String proxyServerUrl;

    @BeforeAll
    static void startServer() throws IOException {
        var service = createConformanceService();
        var directEngine = McpEngine.builder()
                .services(Map.of("conformance", service))
                .name("smithy-java-conformance")
                .version("1.0.0")
                .interceptor(new RequiredCapabilityInterceptor())
                .build();
        directServer = startHttpServer(directEngine);
        directServerUrl = serverUrl(directServer);

        var proxy = HttpMcpClient.builder()
                .endpoint(directServerUrl)
                .name("conformance-upstream")
                .build();
        var proxyEngine = McpEngine.builder()
                .remoteClients(List.of(proxy))
                .name("smithy-java-proxy-conformance")
                .version("1.0.0")
                .build();
        proxyServer = startHttpServer(proxyEngine);
        proxyServerUrl = serverUrl(proxyServer);
    }

    private static ConformanceService createConformanceService() {
        return ConformanceService.builder()
                .addTestCustomHeaderOperation(
                        (TestCustomHeaderOperation) (input, context) -> TestCustomHeaderOutput.builder()
                                .text("Custom header accepted: " + input.getValue())
                                .build())
                .addTestErrorHandlingOperation(
                        (TestErrorHandlingOperation) (input, context) -> {
                            throw new RuntimeException("This tool intentionally returns an error for testing");
                        })
                .addTestLoggingToolOperation(
                        (TestLoggingToolOperation) (input, context) -> TestLoggingToolOutput.builder()
                                .text("Logging completed.")
                                .build())
                .addTestMissingCapabilityOperation(
                        (TestMissingCapabilityOperation) (input, context) -> TestMissingCapabilityOutput.builder()
                                .text("Capability available.")
                                .build())
                .addTestSimpleTextOperation((TestSimpleTextOperation) (input, context) -> TestSimpleTextOutput.builder()
                        .text("This is a simple text response for testing.")
                        .build())
                .addTestStreamingElicitationOperation(
                        (TestStreamingElicitationOperation) (input, context) -> TestStreamingElicitationOutput.builder()
                                .text("Streaming elicitation completed.")
                                .build())
                .build();
    }

    private static HttpServer startHttpServer(McpEngine engine) throws IOException {
        var requestHandler = McpHttpHandler.forLoopback(engine);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> handleHttpRequest(exchange, requestHandler));
        server.start();
        return server;
    }

    private static String serverUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
    }

    @AfterAll
    static void stopServer() {
        if (proxyServer != null) {
            proxyServer.stop(0);
        }
        if (directServer != null) {
            directServer.stop(0);
        }
    }

    @ParameterizedTest(name = "{0} requirements {1}")
    @MethodSource("conformanceTopologies")
    void passesOfficialConformanceRequirements(
            String topology,
            String protocolVersion,
            String serverUrl
    ) throws Exception {
        var baseline = Path.of(McpConformanceTest.class
                .getResource("/conformance-baseline-" + protocolVersion + ".yaml")
                .toURI())
                .toString();
        var outputDirectory = Path.of(
                "build",
                "conformance-results",
                topology + "-" + protocolVersion).toAbsolutePath();
        Files.createDirectories(outputDirectory);
        var process = new ProcessBuilder(
                "npx",
                "--yes",
                "@modelcontextprotocol/conformance@" + CONFORMANCE_VERSION,
                "server",
                "--url",
                serverUrl,
                "--requirements",
                protocolVersion,
                "--expected-failures",
                baseline,
                "--output-dir",
                outputDirectory.toString(),
                "--verbose")
                .redirectErrorStream(true)
                .start();

        var output = CompletableFuture.supplyAsync(() -> readOutput(process));
        var exited = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!exited) {
            process.destroyForcibly();
        }

        var commandOutput = output.get(10, TimeUnit.SECONDS);
        assertTrue(exited, () -> "Conformance process timed out:\n" + commandOutput);
        assertEquals(0, process.exitValue(), () -> "Conformance scenario failed:\n" + commandOutput);
    }

    private static Stream<Arguments> conformanceTopologies() {
        return Stream.of(
                Arguments.of("direct", "2025-11-25", directServerUrl),
                Arguments.of("proxy", "2025-11-25", proxyServerUrl),
                Arguments.of("direct", "2026-07-28", directServerUrl),
                Arguments.of("proxy", "2026-07-28", proxyServerUrl));
    }

    private static void handleHttpRequest(HttpExchange exchange, McpHttpHandler requestHandler)
            throws IOException {
        try (exchange) {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            var request = normalizeConformanceToolName(
                    CODEC.deserializeShape(exchange.getRequestBody().readAllBytes(), JsonRpcRequest.builder()));
            var headers = normalizeConformanceToolNameHeader(request, exchange.getRequestHeaders());
            var response = requestHandler.handle(request, headers);
            if (response.body() == null) {
                exchange.sendResponseHeaders(response.statusCode(), -1);
                return;
            }

            var responseBytes = ByteBufferUtils.getBytes(CODEC.serialize(response.body()));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.statusCode(), responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
        }
    }

    private static JsonRpcRequest normalizeConformanceToolName(JsonRpcRequest request) {
        if (!"tools/call".equals(request.getMethod()) || request.getParams() == null) {
            return request;
        }

        var params = new HashMap<>(request.getParams().asStringMap());
        var name = params.get("name");
        if (name == null || !name.asString().contains("_")) {
            return request;
        }

        params.put("name", Document.of(toUpperCamelCase(name.asString())));
        return request.toBuilder().params(Document.of(params)).build();
    }

    private static Map<String, List<String>> normalizeConformanceToolNameHeader(
            JsonRpcRequest request,
            Map<String, List<String>> headers
    ) {
        var result = new HashMap<String, List<String>>();
        headers.forEach((name, values) -> result.put(name, List.copyOf(values)));
        if (!"tools/call".equals(request.getMethod())) {
            return result;
        }

        var nameHeader = headers.entrySet()
                .stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase("mcp-name"))
                .map(Map.Entry::getValue)
                .filter(values -> !values.isEmpty())
                .map(List::getFirst)
                .findFirst()
                .orElse(null);
        if (nameHeader == null || !nameHeader.contains("_")) {
            return result;
        }

        result.keySet().removeIf(name -> name.equalsIgnoreCase("mcp-name"));
        result.put("mcp-name", List.of(toUpperCamelCase(nameHeader)));
        return result;
    }

    private static String toUpperCamelCase(String value) {
        var result = new StringBuilder(value.length());
        var capitalizeNext = true;
        for (var character : value.toCharArray()) {
            if (character == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(character));
                capitalizeNext = false;
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    private static String readOutput(Process process) {
        try (var output = new ByteArrayOutputStream()) {
            process.getInputStream().transferTo(output);
            return output.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "Failed to read conformance process output: " + e.getMessage();
        }
    }

    private static final class RequiredCapabilityInterceptor implements McpInterceptor {
        @Override
        public void readBeforeToolCall(McpToolExecutionContext hook) {
            if (!"TestMissingCapability".equals(hook.call().name())) {
                return;
            }

            var capabilities = hook.call().metadata().clientCapabilities();
            if (capabilities == null || capabilities.getMember("sampling") == null) {
                throw new McpProtocolException(
                        -32021,
                        "The sampling client capability is required",
                        Document.of(Map.of(
                                "requiredCapabilities",
                                Document.of(Map.of("sampling", Document.of(Map.of()))))));
            }
        }
    }
}
