/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.http.HttpContext;
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.json.JsonCodec;
import software.amazon.smithy.java.json.JsonSettings;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public final class HttpMcpProxy extends McpServerProxy {
    private static final InternalLogger LOG = InternalLogger.getLogger(HttpMcpProxy.class);
    private static final JsonCodec JSON_CODEC = JsonCodec.builder()
            .settings(JsonSettings.builder().serializeTypeInDocuments(false).useJsonName(true).build())
            .build();

    private final ClientTransport<HttpRequest, HttpResponse> transport;
    private final URI endpoint;
    private final String name;
    private final Signer<HttpRequest, ?> signer;
    private final Duration timeout;
    private volatile String sessionId;

    private HttpMcpProxy(Builder builder) {
        this.transport = builder.transport != null ? builder.transport : new JavaHttpClientTransport();
        this.endpoint = URI.create(builder.endpoint);
        this.name = builder.name != null ? builder.name : sanitizeName(endpoint.getHost());
        this.signer = builder.signer;
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofSeconds(60);
    }

    private static String sanitizeName(String host) {
        if (host == null) {
            return "http-proxy-mcp";
        }
        return host.replaceAll("[^a-zA-Z0-9-]", "-");
    }

    public static final class Builder {
        private String endpoint;
        private String name;
        private Signer<HttpRequest, ?> signer;
        private ClientTransport<HttpRequest, HttpResponse> transport;
        private Duration timeout;

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder signer(Signer<HttpRequest, ?> signer) {
            this.signer = signer;
            return this;
        }

        public Builder transport(ClientTransport<HttpRequest, HttpResponse> transport) {
            this.transport = transport;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public HttpMcpProxy build() {
            if (endpoint == null || endpoint.isEmpty()) {
                throw new IllegalArgumentException("Endpoint must be provided");
            }
            return new HttpMcpProxy(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CompletableFuture<JsonRpcResponse> rpc(JsonRpcRequest request) {
        try {
            byte[] body = JSON_CODEC.serializeToString(request).getBytes(StandardCharsets.UTF_8);
            LOG.trace("Sending HTTP request to {}", endpoint);

            String protocolVersionHeader = getProtocolVersion().identifier();

            HttpRequest.Builder requestBuilder = HttpRequest.builder()
                    .uri(endpoint)
                    .method("POST")
                    .withAddedHeader("Content-Type", "application/json")
                    .withAddedHeader("Accept", "application/json, text/event-stream")
                    .withAddedHeader("MCP-Protocol-Version", protocolVersionHeader);

            // Include session ID if we have one
            String currentSessionId = sessionId;
            if (currentSessionId != null) {
                requestBuilder.withAddedHeader("Mcp-Session-Id", currentSessionId);
                LOG.debug("Including session ID in request: method={}, sessionId={}",
                        request.getMethod(),
                        currentSessionId);
            } else {
                LOG.debug("No session ID available for request: method={}", request.getMethod());
            }

            HttpRequest httpRequest = requestBuilder
                    .body(DataStream.ofBytes(body, "application/json"))
                    .build();

            Context context = Context.create();
            context.put(HttpContext.HTTP_REQUEST_TIMEOUT, timeout);

            if (signer != null) {
                httpRequest = signer.sign(httpRequest, null, context);
            }

            HttpResponse response = transport.send(context, httpRequest);
            LOG.trace("Received HTTP response with status: {}", response.statusCode());

            // Extract and store session ID from response only during initialize
            if ("initialize".equals(request.getMethod())) {
                String responseSessionId = response.headers().firstValue("Mcp-Session-Id");
                if (responseSessionId != null) {
                    sessionId = responseSessionId;
                    LOG.debug("Stored session ID from initialize response: {}", responseSessionId);
                }
            }

            // "When a client receives HTTP 404 in response to a request containing an Mcp-Session-Id, 
            // it MUST start a new session by sending a new InitializeRequest without a session ID attached."
            if (response.statusCode() == 404 && sessionId != null) {
                LOG.debug("Received 404 with active session ID. Clearing session to force restart.");
                sessionId = null;
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return CompletableFuture.completedFuture(handleErrorResponse(response));
            }

            // Check if response is SSE
            String contentType = response.body().contentType();
            if ("text/event-stream".equals(contentType)) {
                return CompletableFuture.completedFuture(parseSseResponse(response, request));
            }

            return CompletableFuture.completedFuture(JsonRpcResponse.builder()
                    .deserialize(JSON_CODEC.createDeserializer(response.body().asByteBuffer()))
                    .build());
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private JsonRpcResponse parseSseResponse(HttpResponse response, JsonRpcRequest request) {
        try {
            byte[] bodyBytes = ByteBufferUtils.getBytes(response.body().asByteBuffer());
            String sseContent = new String(bodyBytes, StandardCharsets.UTF_8);

            JsonRpcResponse finalResponse = null;
            Iterable<String> lines = sseContent.lines()::iterator;
            StringBuilder dataBuffer = new StringBuilder();

            for (String line : lines) {
                if (line.startsWith("data:")) {
                    var value = line.substring(5);
                    dataBuffer.append(value.startsWith(" ") ? value.substring(1) : value);
                } else if (line.trim().isEmpty() && !dataBuffer.isEmpty()) {
                    // End of an SSE event
                    String jsonData = dataBuffer.toString().trim();
                    dataBuffer.setLength(0);

                    if (jsonData.isEmpty()) {
                        continue;
                    }

                    try {
                        // Parse JSON once into Document
                        Document jsonDocument = JSON_CODEC.createDeserializer(jsonData.getBytes(StandardCharsets.UTF_8))
                                .readDocument();

                        // Check if it's a notification by checking for top-level "id" field
                        // Notifications have "method" but no "id", responses have "id"
                        if (isNotification(jsonDocument)) {
                            // This is a notification - convert Document to JsonRpcRequest and forward
                            JsonRpcRequest notification = jsonDocument.asShape(JsonRpcRequest.builder());
                            LOG.debug("Received notification from SSE stream: method={}", notification.getMethod());
                            notifyRequest(notification);
                        } else {
                            // This is a response - convert Document to JsonRpcResponse
                            finalResponse = jsonDocument.asShape(JsonRpcResponse.builder());
                            finalResponse = message;
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to parse SSE message: {}", jsonData, e);
                    }
                }
            }

            // Process any remaining data in buffer (in case stream doesn't end with empty line)
            if (!dataBuffer.isEmpty()) {
                String jsonData = dataBuffer.toString().trim();
                if (!jsonData.isEmpty()) {
                    try {
                        // Parse JSON once into Document
                        Document jsonDocument = JSON_CODEC.createDeserializer(jsonData.getBytes(StandardCharsets.UTF_8))
                                .readDocument();

                        // Check if it's a notification by checking for top-level "id" field
                        // Notifications have "method" but no "id", responses have "id"
                        if (isNotification(jsonDocument)) {
                            JsonRpcRequest notification = JsonRpcRequest.builder()
                                    .deserialize(jsonDocument.createDeserializer())
                                    .build();
                            LOG.debug("Received notification from remaining SSE buffer: method={}",
                                    notification.getMethod());
                            notifyRequest(notification);
                        } else {
                            JsonRpcResponse message = JsonRpcResponse.builder()
                                    .deserialize(jsonDocument.createDeserializer())
                                    .build();

                            if (message.getId() == null) {
                                notifyRequest(JsonRpcRequest.builder()
                                        .jsonrpc("2.0")
                                        .method("notifications/unknown")
                                        .build());
                            } else {
                                finalResponse = message;
                            }
                        }
                    } catch (Exception e) {
                        LOG.warn("Failed to parse remaining SSE message: {}", jsonData, e);
                    }
                }
            }

            if (finalResponse == null) {
                return JsonRpcResponse.builder()
                        .jsonrpc("2.0")
                        .id(request.getId())
                        .error(JsonRpcErrorResponse.builder()
                                .code(-32001)
                                .message("SSE parsing error: No final response found in stream")
                                .build())
                        .build();
            }

            return finalResponse;
        } catch (Exception e) {
            LOG.error("Error parsing SSE response", e);
            return JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(request.getId())
                    .error(JsonRpcErrorResponse.builder()
                            .code(-32001)
                            .message("SSE parsing error: " + e.getMessage())
                            .build())
                    .build();
        }
    }

    /**
     * Determines if a Document represents a notification (has "method" but no "id")
     * rather than a response (has "id").
     * 
     * - Responses have an "id" field at the top level
     * - Notifications have a "method" field but no "id" field at the top level
     */
    private boolean isNotification(Document doc) {
        try {
            if (!doc.isType(ShapeType.STRUCTURE) && !doc.isType(ShapeType.MAP)) {
                return false;
            }

            
            // If it has a "method" field but no "id", it's a notification
            return doc.getMember("id") == null && doc.getMember("method") != null;
        } catch (Exception e) {
            LOG.warn("Failed to determine if notification from Document", e);
            return false;
        }
    }

    private JsonRpcResponse handleErrorResponse(HttpResponse response) {
        long contentLength = response.body().contentLength();
        String errorMessage = "HTTP " + response.statusCode();

        if (contentLength > 0) {
            String contentType = response.body().contentType();
            byte[] bodyBytes = ByteBufferUtils.getBytes(response.body().asByteBuffer());

            if ("application/json".equals(contentType)) {
                try {
                    return JsonRpcResponse.builder()
                            .deserialize(JSON_CODEC.createDeserializer(bodyBytes))
                            .build();
                } catch (Exception e) {
                    LOG.warn("Failed to deserialize JSON error response", e);
                    return JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .error(JsonRpcErrorResponse.builder()
                                    .code(response.statusCode())
                                    .message("HTTP " + response.statusCode() + ": Invalid JSON response")
                                    .build())
                            .build();
                }
            } else {
                int length = Math.min(200, bodyBytes.length);
                String errorBody = new String(bodyBytes, 0, length, StandardCharsets.UTF_8);
                errorMessage = errorMessage + ": " + errorBody + (length != bodyBytes.length ? " (truncated)" : "");
            }
        }

        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .error(JsonRpcErrorResponse.builder()
                        .code(response.statusCode())
                        .message(errorMessage)
                        .build())
                .build();
    }

    @Override
    public void start() {
        // HTTP is connectionless, nothing to start
        LOG.debug("HTTP MCP proxy started for endpoint: {}", endpoint);
    }

    @Override
    public CompletableFuture<Void> shutdown() {
        // HTTP client doesn't need explicit shutdown
        LOG.debug("HTTP MCP proxy shutdown for endpoint: {}", endpoint);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String name() {
        return name;
    }
}
