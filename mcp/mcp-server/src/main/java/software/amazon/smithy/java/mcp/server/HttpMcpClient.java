/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
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
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyUnstableApi;

@SmithyUnstableApi
public final class HttpMcpClient extends McpRemoteClient {
    private static final InternalLogger LOG = InternalLogger.getLogger(HttpMcpClient.class);
    private static final int UPSTREAM_HTTP_ERROR_CODE = -32000;
    private static final long MAX_DISCARDED_BODY_BYTES = 8 * 1024;
    private static final int MAX_ERROR_MESSAGE_BODY_BYTES = 200;
    private static final int MAX_JSON_ERROR_BODY_BYTES = 64 * 1024;
    private static final int MAX_LOGGED_SSE_CHARS = 200;
    private static final int MAX_RESPONSE_BODY_BYTES_LIMIT = Integer.MAX_VALUE - 8;

    private static final int DEFAULT_MAX_RESPONSE_BODY_BYTES = 16 * 1024 * 1024;

    private final ClientTransport<HttpRequest, HttpResponse> transport;
    private final URI endpoint;
    private final String name;
    private final Signer<HttpRequest, ?> signer;
    private final AuthScheme<HttpRequest, ?> authScheme;
    private final IdentityResolver<?> identityResolver;
    private final Context signerContext;
    private final Duration timeout;
    private final int maxResponseBodyBytes;
    private final AtomicReference<Map<String, Map<String, String>>> toolHeaderParameters =
            new AtomicReference<>(Map.of());
    private final AtomicReference<String> sessionId = new AtomicReference<>();

    private HttpMcpClient(Builder builder) {
        this.transport = builder.transport != null ? builder.transport : new JavaHttpClientTransport();
        this.endpoint = URI.create(builder.endpoint);
        this.name = builder.name != null ? builder.name : sanitizeName(endpoint.getHost());
        this.signer = builder.signer;
        this.authScheme = builder.authScheme;
        this.identityResolver = builder.identityResolver;
        this.signerContext = builder.signerContext != null ? builder.signerContext : Context.create();
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofMinutes(5);
        this.maxResponseBodyBytes = builder.maxResponseBodyBytes;
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
        private int maxResponseBodyBytes = DEFAULT_MAX_RESPONSE_BODY_BYTES;

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

        /**
         * Sets the largest successful response the client reads from the remote server.
         *
         * <p>This limits the bytes of a JSON response body, and the characters of each line and each event
         * of a {@code text/event-stream} response. A response over the limit is closed without being read
         * further, and the request fails with a JSON-RPC error. Defaults to 16 MiB.
         *
         * @param maxResponseBodyBytes the limit, greater than 0 and at most {@code Integer.MAX_VALUE - 8}.
         * @return the builder.
         */
        public Builder maxResponseBodyBytes(int maxResponseBodyBytes) {
            this.maxResponseBodyBytes = maxResponseBodyBytes;
            return this;
        }

        public HttpMcpClient build() {
            if (endpoint == null || endpoint.isEmpty()) {
                throw new IllegalArgumentException("Endpoint must be provided");
            }
            if (maxResponseBodyBytes <= 0 || maxResponseBodyBytes > MAX_RESPONSE_BODY_BYTES_LIMIT) {
                throw new IllegalArgumentException("maxResponseBodyBytes must be greater than 0 and at most "
                        + MAX_RESPONSE_BODY_BYTES_LIMIT + ": " + maxResponseBodyBytes);
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
    protected void onToolsPage(List<ToolInfo> tools) {
        toolHeaderParameters.updateAndGet(current -> {
            var updatedMappings = new HashMap<>(current);
            for (var tool : tools) {
                var mappings = McpHttpBinding.headerParameters(tool);
                if (mappings.isEmpty()) {
                    updatedMappings.remove(tool.getName());
                } else {
                    updatedMappings.put(tool.getName(), mappings);
                }
            }
            return Map.copyOf(updatedMappings);
        });
    }

    @Override
    protected JsonRpcResponse exchange(JsonRpcRequest request) {
        return exchange(request, true);
    }

    private JsonRpcResponse exchange(JsonRpcRequest request, boolean allowSessionRecovery) {
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
            String currentSessionId = sessionId.get();
            long currentGeneration = initializationGeneration();
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
                    sessionId.set(responseSessionId);
                    LOG.debug("Stored session ID from initialize response: {}", responseSessionId);
                }
            }

            // "When a client receives HTTP 404 in response to a request containing an Mcp-Session-Id,
            // it MUST start a new session by sending a new InitializeRequest without a session ID attached."
            if (response.statusCode() == 404 && currentSessionId != null) {
                LOG.debug("Received 404 with active session ID. Clearing session to force restart.");
                var ownsRecovery = sessionId.compareAndSet(currentSessionId, null);
                if (allowSessionRecovery
                        && !McpHttpBinding.isInitialize(request)) {
                    discardBody(response);
                    if (restartSession(currentGeneration)) {
                        return exchange(request, false);
                    }
                    return sessionRecoveryFailure(request, ownsRecovery);
                }
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return handleErrorResponse(response, request);
            }

            // A notification has no JSON-RPC response. Streamable HTTP accepts one with 202 and no body,
            // but some servers send a body anyway (e.g. "{}"), so discard it instead of parsing it.
            if (request.getId() == null) {
                discardBody(response);
                return null;
            }

            // Check if response is SSE
            String contentType = response.body().contentType();
            if (contentType != null && contentType.startsWith("text/event-stream")) {
                return parseSseResponse(response, request);
            }

            var responseBody = readAtMost(response, maxResponseBodyBytes);
            if (responseBody.truncated()) {
                LOG.warn("Response from {} exceeds {} bytes", endpoint, maxResponseBodyBytes);
                return upstreamError(request,
                        "HTTP " + response.statusCode() + ": Response body exceeds " + maxResponseBodyBytes + " bytes");
            }
            var responseBytes = responseBody.bytes();
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

    /**
     * Reads a short body of known length so the connection can be reused, and closes anything longer,
     * of unknown length, or streaming without reading it.
     */
    private static void discardBody(HttpResponse response) {
        var body = response.body();
        if (body != null && body.hasKnownLength() && body.contentLength() <= MAX_DISCARDED_BODY_BYTES) {
            ByteBufferUtils.getBytes(body.asByteBuffer());
        } else {
            response.close();
        }
    }

    private record BoundedBody(byte[] bytes, boolean truncated) {}

    /**
     * Reads at most {@code max} bytes of the body. If the body is longer, closes the response without reading
     * the rest and marks the result truncated.
     */
    private static BoundedBody readAtMost(HttpResponse response, int max) throws IOException {
        var body = response.body();
        if (body == null) {
            return new BoundedBody(new byte[0], false);
        }
        var in = body.asInputStream();
        byte[] read;
        try {
            read = in.readNBytes(max + 1);
        } catch (IOException | RuntimeException e) {
            response.close();
            throw e;
        }
        if (read.length > max) {
            response.close();
            return new BoundedBody(Arrays.copyOf(read, max), true);
        }
        in.close();
        return new BoundedBody(read, false);
    }

    private JsonRpcResponse upstreamError(JsonRpcRequest request, String message) {
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(request.getId())
                .error(JsonRpcErrorResponse.builder()
                        .code(UPSTREAM_HTTP_ERROR_CODE)
                        .message(message)
                        .build())
                .build();
    }

    private JsonRpcResponse sessionRecoveryFailure(
            JsonRpcRequest request,
            boolean ownedRecovery
    ) {
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(request.getId())
                .error(JsonRpcErrorResponse.builder()
                        .code(UPSTREAM_HTTP_ERROR_CODE)
                        .message(ownedRecovery
                                ? "HTTP 404: MCP session expired and could not be reinitialized"
                                : "HTTP 404: MCP session recovery did not complete")
                        .build())
                .build();
    }

    private McpProtocol requestProtocol(JsonRpcRequest request) {
        var params = request.getParams();
        var meta = params == null
                || !(params.isType(ShapeType.MAP) || params.isType(ShapeType.STRUCTURE))
                        ? null
                        : params.getMember("_meta");
        var requestedVersion = meta == null
                || !(meta.isType(ShapeType.MAP) || meta.isType(ShapeType.STRUCTURE))
                        ? null
                        : meta.getMember(McpWireNames.PROTOCOL_VERSION);
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
            var lineBuffer = new StringBuilder();
            String line;
            while ((line = readSseLine(reader, lineBuffer, maxResponseBodyBytes)) != null) {
                if (line.isEmpty()) {
                    var result = processSseEvent(data, request.getId());
                    if (result != null) {
                        return result;
                    }
                } else if (line.startsWith("data:")) {
                    var value = line.substring(5);
                    if (value.startsWith(" ")) {
                        value = value.substring(1);
                    }
                    if ((long) data.length() + value.length() + 1 > maxResponseBodyBytes) {
                        throw new SseLimitExceededException(
                                "SSE event exceeds " + maxResponseBodyBytes + " characters");
                    }
                    data.append(value).append('\n');
                }
            }
            var result = processSseEvent(data, request.getId());
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

    /**
     * Reads one SSE line, ended by CR, LF or CRLF, as {@link BufferedReader#readLine()} does, but fails once the
     * line has more than {@code maxChars} characters.
     *
     * @return the line without its ending, or null at the end of the stream.
     */
    private static String readSseLine(BufferedReader reader, StringBuilder line, int maxChars) throws IOException {
        line.setLength(0);
        int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') {
                return line.toString();
            }
            if (c == '\r') {
                reader.mark(1);
                if (reader.read() != '\n') {
                    reader.reset();
                }
                return line.toString();
            }
            if (line.length() >= maxChars) {
                throw new SseLimitExceededException("SSE line exceeds " + maxChars + " characters");
            }
            line.append((char) c);
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static final class SseLimitExceededException extends IOException {
        SseLimitExceededException(String message) {
            super(message);
        }
    }

    private JsonRpcResponse processSseEvent(StringBuilder data, Document expectedId) {
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
            var response = document.asShape(JsonRpcResponse.builder());
            if (!requestIdsMatch(expectedId, response.getId())) {
                LOG.warn("Ignoring SSE response with unexpected request ID");
                return null;
            }
            return response;
        } catch (RuntimeException e) {
            var logged = jsonData.length() > MAX_LOGGED_SSE_CHARS
                    ? jsonData.substring(0, MAX_LOGGED_SSE_CHARS) + "... (truncated)"
                    : jsonData;
            LOG.warn("Failed to parse SSE message: {}", logged, e);
            return null;
        }
    }

    private boolean requestIdsMatch(Document expected, Document actual) {
        if (expected == null || actual == null) {
            return expected == actual;
        }
        try {
            return StdioMcpClient.requestKey(expected).equals(StdioMcpClient.requestKey(actual));
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private JsonRpcResponse handleErrorResponse(HttpResponse response, JsonRpcRequest request)
            throws IOException {
        long contentLength = response.body().contentLength();
        String errorMessage = "HTTP " + response.statusCode();

        if (contentLength != 0) {
            String contentType = response.body().contentType();

            if (contentType != null && contentType.startsWith("application/json")) {
                var errorBody = readAtMost(response, MAX_JSON_ERROR_BODY_BYTES);
                if (errorBody.truncated()) {
                    LOG.warn("Error response from {} exceeds {} bytes", endpoint, MAX_JSON_ERROR_BODY_BYTES);
                    return upstreamError(request,
                            errorMessage + ": Error response exceeds " + MAX_JSON_ERROR_BODY_BYTES + " bytes");
                }
                byte[] bodyBytes = errorBody.bytes();
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
                var errorBody = readAtMost(response, MAX_ERROR_MESSAGE_BODY_BYTES);
                errorMessage = errorMessage + ": " + new String(errorBody.bytes(), StandardCharsets.UTF_8)
                        + (errorBody.truncated() ? " (truncated)" : "");
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
