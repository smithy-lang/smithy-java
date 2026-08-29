/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import software.amazon.smithy.java.auth.api.Signer;
import software.amazon.smithy.java.auth.api.identity.Identity;
import software.amazon.smithy.java.auth.api.identity.IdentityResolver;
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.client.core.auth.scheme.AuthScheme;
import software.amazon.smithy.java.client.http.HttpContext;
import software.amazon.smithy.java.client.http.JavaHttpClientTransport;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.ModifiableHttpRequest;
import software.amazon.smithy.java.io.ByteBufferUtils;
import software.amazon.smithy.java.io.datastream.DataStream;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public final class HttpMcpClient extends McpRemoteClient {
    private static final InternalLogger LOG = InternalLogger.getLogger(HttpMcpClient.class);
    private static final int UPSTREAM_HTTP_ERROR_CODE = -32000;

    private final ClientTransport<HttpRequest, HttpResponse> transport;
    private final URI endpoint;
    private final String name;
    private final Signer<HttpRequest, ?> signer;
    private final AuthScheme<HttpRequest, ?> authScheme;
    private final IdentityResolver<?> identityResolver;
    private final Context signerContext;
    private final Duration timeout;
    private final AtomicReference<Map<String, Map<String, String>>> toolHeaderParameters =
            new AtomicReference<>(Map.of());
    private volatile String sessionId;

    private HttpMcpClient(Builder builder) {
        this.transport = builder.transport != null ? builder.transport : new JavaHttpClientTransport();
        this.endpoint = URI.create(builder.endpoint);
        this.name = builder.name != null ? builder.name : sanitizeName(endpoint.getHost());
        this.signer = builder.signer;
        this.authScheme = builder.authScheme;
        this.identityResolver = builder.identityResolver;
        this.signerContext = builder.signerContext != null ? builder.signerContext : Context.create();
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofMinutes(5);
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
        private AuthScheme<HttpRequest, ?> authScheme;
        private IdentityResolver<?> identityResolver;
        private Context signerContext;
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

        public Builder authScheme(AuthScheme<HttpRequest, ?> authScheme) {
            this.authScheme = authScheme;
            return this;
        }

        public Builder identityResolver(IdentityResolver<?> identityResolver) {
            this.identityResolver = identityResolver;
            return this;
        }

        public Builder signerContext(Context signerContext) {
            this.signerContext = signerContext;
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

        public HttpMcpClient build() {
            if (endpoint == null || endpoint.isEmpty()) {
                throw new IllegalArgumentException("Endpoint must be provided");
            }
            if (signer != null && authScheme != null) {
                throw new IllegalArgumentException(
                        "Cannot set both signer and authScheme; use one or the other");
            }
            if (authScheme != null && identityResolver == null) {
                throw new IllegalArgumentException(
                        "identityResolver must be provided when authScheme is set");
            }
            if (identityResolver != null && authScheme == null) {
                throw new IllegalArgumentException(
                        "authScheme must be provided when identityResolver is set");
            }
            return new HttpMcpClient(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ToolInfo> listTools() {
        var tools = super.listTools();
        var updatedMappings = new HashMap<String, Map<String, String>>();
        for (var tool : tools) {
            var mappings = McpHttpBinding.headerParameters(tool);
            if (!mappings.isEmpty()) {
                updatedMappings.put(tool.getName(), mappings);
            }
        }
        toolHeaderParameters.set(Map.copyOf(updatedMappings));
        return tools;
    }

    @Override
    protected JsonRpcResponse exchange(JsonRpcRequest request) {
        try {
            byte[] body = ByteBufferUtils.getBytes(McpJson.CODEC.serialize(request));
            LOG.trace("Sending HTTP request to {}", endpoint);

            var protocol = requestProtocol(request);

            var requestBuilder = HttpRequest.create()
                    .setUri(endpoint)
                    .setMethod("POST")
                    .addHeader(HeaderName.CONTENT_TYPE, "application/json")
                    .addHeader(HeaderName.ACCEPT, "application/json, text/event-stream")
                    .addHeader(McpHttpBinding.PROTOCOL_VERSION, protocol.id().identifier());

            if (McpHttpBinding.usesMethodHeaders(protocol)) {
                requestBuilder.addHeader(McpHttpBinding.METHOD, request.getMethod());
                var requestName = McpHttpBinding.requestName(request);
                if (requestName != null) {
                    requestBuilder.addHeader(McpHttpBinding.NAME, requestName);
                }
                addParameterHeaders(requestBuilder, request, requestName);
            }

            // Include session ID if we have one
            String currentSessionId = sessionId;
            if (currentSessionId != null) {
                requestBuilder.addHeader(McpHttpBinding.SESSION_ID, currentSessionId);
                LOG.debug("Including session ID in request: method={}, sessionId={}",
                        request.getMethod(),
                        currentSessionId);
            } else {
                LOG.debug("No session ID available for request: method={}", request.getMethod());
            }

            HttpRequest httpRequest = requestBuilder
                    .setBody(DataStream.ofBytes(body, "application/json"))
                    .toUnmodifiable();

            Context context = Context.create();
            context.put(HttpContext.HTTP_REQUEST_TIMEOUT, timeout);

            if (authScheme != null) {
                httpRequest = signWithAuthScheme(httpRequest);
            } else if (signer != null) {
                httpRequest = signer.sign(httpRequest, null, context).signedRequest();
            }

            HttpResponse response = transport.send(context, httpRequest);
            LOG.trace("Received HTTP response with status: {}", response.statusCode());

            // Extract and store session ID from response only during initialize
            if (McpHttpBinding.isInitialize(request)) {
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
                return handleErrorResponse(response, request);
            }

            // Check if response is SSE
            String contentType = response.body().contentType();
            if (contentType != null && contentType.startsWith("text/event-stream")) {
                return parseSseResponse(response, request);
            }

            var responseBytes = ByteBufferUtils.getBytes(response.body().asByteBuffer());
            if (responseBytes.length == 0) {
                return null;
            }
            return JsonRpcResponse.builder()
                    .deserialize(McpJson.CODEC.createDeserializer(responseBytes))
                    .build();
        } catch (Exception e) {
            throw new McpRemoteException("HTTP MCP exchange failed", e);
        }
    }

    private McpProtocol requestProtocol(JsonRpcRequest request) {
        var params = request.getParams();
        var meta = params == null ? null : params.getMember("_meta");
        var requestedVersion = meta == null ? null : meta.getMember(McpWireNames.PROTOCOL_VERSION);
        var selected = protocol();
        if (requestedVersion == null || requestedVersion.asString().equals(selected.id().identifier())) {
            return selected;
        }
        var parsed = ProtocolVersion.parse(requestedVersion.asString());
        if (parsed instanceof KnownProtocolVersion known) {
            return BuiltInProtocols.protocol(known);
        }
        throw new McpRemoteException("Unregistered MCP protocol: " + parsed.identifier());
    }

    private String stringMember(Document document, String name) {
        return McpHttpBinding.stringMember(document, name);
    }

    private void addParameterHeaders(
            ModifiableHttpRequest requestBuilder,
            JsonRpcRequest request,
            String toolName
    ) {
        if (!McpHttpBinding.isToolCall(request) || toolName == null) {
            return;
        }

        var mappings = toolHeaderParameters.get().get(toolName);
        var params = request.getParams();
        var arguments = params == null ? null : params.getMember("arguments");
        if (mappings == null || arguments == null) {
            return;
        }

        for (var entry : mappings.entrySet()) {
            var value = stringMember(arguments, entry.getKey());
            if (value != null) {
                requestBuilder.addHeader(
                        HeaderName.of("Mcp-Param-" + entry.getValue()),
                        McpHttpBinding.encodeParameter(value));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <I extends Identity> HttpRequest signWithAuthScheme(HttpRequest request) {
        AuthScheme<HttpRequest, I> scheme = (AuthScheme<HttpRequest, I>) authScheme;
        IdentityResolver<I> resolver = (IdentityResolver<I>) identityResolver;

        Context signerProperties = scheme.getSignerProperties(signerContext);
        Context identityProperties = scheme.getIdentityProperties(signerContext);
        I identity = resolver.resolveIdentity(identityProperties).unwrap();

        try (var schemeSigner = scheme.signer()) {
            return schemeSigner.sign(request, identity, signerProperties).signedRequest();
        }
    }

    private JsonRpcResponse parseSseResponse(HttpResponse response, JsonRpcRequest request) {
        try (var input = response.body().asInputStream();
                var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            var data = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    var result = processSseEvent(data);
                    if (result != null) {
                        return result;
                    }
                } else if (line.startsWith("data:")) {
                    var value = line.substring(5);
                    data.append(value.startsWith(" ") ? value.substring(1) : value).append('\n');
                }
            }
            var result = processSseEvent(data);
            if (result != null) {
                return result;
            }
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
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(request.getId())
                .error(JsonRpcErrorResponse.builder()
                        .code(-32001)
                        .message("SSE parsing error: No final response found in stream")
                        .build())
                .build();
    }

    private JsonRpcResponse processSseEvent(StringBuilder data) {
        if (data.isEmpty()) {
            return null;
        }
        var jsonData = data.toString().stripTrailing();
        data.setLength(0);
        if (jsonData.isEmpty()) {
            return null;
        }

        try {
            var document = McpJson.CODEC.createDeserializer(jsonData.getBytes(StandardCharsets.UTF_8))
                    .readDocument();
            if (isNotification(document)) {
                var notification = document.asShape(JsonRpcRequest.builder());
                LOG.debug("Received notification from SSE stream: method={}", notification.getMethod());
                notify(notification);
                return null;
            }
            return document.asShape(JsonRpcResponse.builder());
        } catch (RuntimeException e) {
            LOG.warn("Failed to parse SSE message: {}", jsonData, e);
            return null;
        }
    }

    private JsonRpcResponse handleErrorResponse(HttpResponse response, JsonRpcRequest request) {
        long contentLength = response.body().contentLength();
        String errorMessage = "HTTP " + response.statusCode();

        if (contentLength != 0) {
            String contentType = response.body().contentType();
            byte[] bodyBytes = ByteBufferUtils.getBytes(response.body().asByteBuffer());

            if (contentType != null && contentType.startsWith("application/json")) {
                try {
                    return JsonRpcResponse.builder()
                            .deserialize(McpJson.CODEC.createDeserializer(bodyBytes))
                            .build();
                } catch (Exception e) {
                    LOG.warn("Failed to deserialize JSON error response", e);
                    return JsonRpcResponse.builder()
                            .jsonrpc("2.0")
                            .id(request.getId())
                            .error(JsonRpcErrorResponse.builder()
                                    .code(UPSTREAM_HTTP_ERROR_CODE)
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
                .id(request.getId())
                .error(JsonRpcErrorResponse.builder()
                        .code(UPSTREAM_HTTP_ERROR_CODE)
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
    public void close() {
        // HTTP client doesn't need explicit shutdown
        LOG.debug("HTTP MCP proxy shutdown for endpoint: {}", endpoint);
    }

    @Override
    public String name() {
        return name;
    }
}
