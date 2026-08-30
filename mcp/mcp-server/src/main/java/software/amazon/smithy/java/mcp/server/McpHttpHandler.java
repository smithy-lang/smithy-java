/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Maps Streamable HTTP request metadata to the transport-independent MCP service.
 */
@SmithyUnstableApi
public final class McpHttpHandler {
    private static final int HEADER_MISMATCH_ERROR_CODE = -32020;
    private final McpEngine engine;
    private final boolean loopbackOnly;

    public McpHttpHandler(McpEngine engine) {
        this(engine, false);
    }

    private McpHttpHandler(McpEngine engine, boolean loopbackOnly) {
        this.engine = engine;
        this.loopbackOnly = loopbackOnly;
        engine.bindTransport(ignored -> {}, ignored -> {});
    }

    /**
     * Creates a handler for a server bound exclusively to a loopback interface.
     *
     * <p>The handler rejects non-loopback Host and Origin headers to protect local
     * MCP servers from DNS rebinding attacks.
     */
    public static McpHttpHandler forLoopback(McpEngine engine) {
        return new McpHttpHandler(engine, true);
    }

    /**
     * Handles a decoded Streamable HTTP request.
     *
     * @param request JSON-RPC request body.
     * @param headers HTTP request headers.
     * @return HTTP status and optional JSON-RPC response body.
     */
    public Response handle(
            JsonRpcRequest request,
            Map<String, List<String>> headers
    ) {
        headers = McpHttpBinding.normalizeHeaders(headers);
        var bodyVersion = McpHttpBinding.protocolVersionFromMetadata(request);
        var headerVersion = McpHttpBinding.firstHeader(headers, "mcp-protocol-version");
        var protocolVersion = resolveProtocolVersion(request, bodyVersion, headerVersion);
        var modernClaim = isModernClaim(bodyVersion, headerVersion);

        if (loopbackOnly) {
            var hostError = validateLoopbackHeaders(request, headers);
            if (hostError != null) {
                return new Response(400, hostError);
            }
        }

        if (modernClaim) {
            var headerError = validateModernHeaders(request, headers, bodyVersion, headerVersion);
            if (headerError != null) {
                return new Response(400, headerError);
            }

            var parameterError = validateMcpParameterHeaders(request, headers);
            if (parameterError != null) {
                return new Response(400, parameterError);
            }
        }

        var outcome = engine.execute(
                request,
                engine.newSession(),
                protocolVersion,
                new McpTransportContext.Http(headers, loopbackOnly));
        var response = engine.encode(outcome);
        if (response == null) {
            return new Response(202, null);
        }
        return new Response(
                McpHttpBinding.statusCode(response, modernClaim, headerVersion != null),
                response);
    }

    private ProtocolVersion resolveProtocolVersion(
            JsonRpcRequest request,
            String bodyVersion,
            String headerVersion
    ) {
        if (bodyVersion != null) {
            return ProtocolVersion.parse(bodyVersion);
        }
        if (McpHttpBinding.isInitialize(request)) {
            var params = request.getParams();
            var initializeVersion = params == null ? null : params.getMember("protocolVersion");
            var identifier = initializeVersion == null
                    ? null
                    : McpHttpBinding.stringMember(params, "protocolVersion");
            return identifier == null
                    ? ProtocolVersion.defaultVersion()
                    : ProtocolVersion.parse(identifier);
        }
        return headerVersion == null
                ? ProtocolVersion.defaultVersion()
                : ProtocolVersion.parse(headerVersion);
    }

    private boolean isModernClaim(String bodyVersion, String headerVersion) {
        if (bodyVersion != null) {
            return true;
        }
        if (headerVersion == null) {
            return false;
        }
        var protocol = engine.findProtocol(ProtocolVersion.parse(headerVersion));
        return protocol != null && McpHttpBinding.usesMethodHeaders(protocol);
    }

    private JsonRpcResponse validateModernHeaders(
            JsonRpcRequest request,
            Map<String, List<String>> headers,
            String bodyVersion,
            String headerVersion
    ) {
        if (headerVersion == null) {
            return headerMismatch(request, "Missing MCP-Protocol-Version header");
        }
        if (bodyVersion != null && !bodyVersion.equals(headerVersion)) {
            return headerMismatch(request, "MCP-Protocol-Version header does not match request metadata");
        }

        var method = McpHttpBinding.firstHeader(headers, "mcp-method");
        if (!request.getMethod().equals(method)) {
            return headerMismatch(request, "Mcp-Method header does not match the JSON-RPC method");
        }

        var expectedName = McpHttpBinding.requestName(request);
        var actualName = McpHttpBinding.firstHeader(headers, "mcp-name");
        if (expectedName != null && !expectedName.equals(actualName)) {
            return headerMismatch(request, "Mcp-Name header does not match the request parameters");
        }
        if (expectedName == null && actualName != null) {
            return headerMismatch(request, "Mcp-Name header is not valid for this method");
        }
        return null;
    }

    private JsonRpcResponse validateMcpParameterHeaders(
            JsonRpcRequest request,
            Map<String, List<String>> headers
    ) {
        if (!McpHttpBinding.isToolCall(request)) {
            return null;
        }

        var params = request.getParams();
        var toolName = params == null ? null : McpHttpBinding.stringMember(params, "name");
        if (toolName == null) {
            return null;
        }

        var arguments = params.getMember("arguments");
        for (var entry : engine.headerParameters(toolName).entrySet()) {
            var parameterName = entry.getKey();
            var headerName = "Mcp-Param-" + entry.getValue();
            var bodyValue = arguments == null ? null : arguments.getMember(parameterName);
            var headerValue = McpHttpBinding.firstHeader(headers, headerName);

            if (bodyValue == null && headerValue == null) {
                continue;
            }
            if (bodyValue == null || headerValue == null || !bodyValue.isType(ShapeType.STRING)) {
                return headerMismatch(request, headerName + " does not match the JSON body parameter");
            }

            final String decodedHeader;
            try {
                decodedHeader = McpHttpBinding.decodeParameter(headerValue);
            } catch (IllegalArgumentException e) {
                return headerMismatch(request, headerName + " contains invalid Base64");
            }
            if (!bodyValue.asString().equals(decodedHeader)) {
                return headerMismatch(request, headerName + " does not match the JSON body parameter");
            }
        }
        return null;
    }

    private JsonRpcResponse validateLoopbackHeaders(
            JsonRpcRequest request,
            Map<String, List<String>> headers
    ) {
        var host = McpHttpBinding.firstHeader(headers, "host");
        if (!isLoopbackAuthority(host)) {
            return headerMismatch(request, "Host header is not a loopback address");
        }

        var origin = McpHttpBinding.firstHeader(headers, "origin");
        if (origin != null && !isLoopbackOrigin(origin)) {
            return headerMismatch(request, "Origin header is not a loopback origin");
        }
        return null;
    }

    private boolean isLoopbackOrigin(String value) {
        try {
            return isLoopbackHost(new URI(value).getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean isLoopbackAuthority(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return isLoopbackHost(new URI("http://" + value).getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }

    private JsonRpcResponse headerMismatch(JsonRpcRequest request, String message) {
        return JsonRpcResponse.builder()
                .jsonrpc("2.0")
                .id(request.getId())
                .error(JsonRpcErrorResponse.builder()
                        .code(HEADER_MISMATCH_ERROR_CODE)
                        .message(message)
                        .build())
                .build();
    }

    /**
     * A Streamable HTTP response.
     *
     * @param statusCode HTTP status code.
     * @param body JSON-RPC body, or {@code null} when no response body is required.
     */
    public record Response(int statusCode, JsonRpcResponse body) {}
}
