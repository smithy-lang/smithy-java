/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.http.api.HeaderName;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Shared Streamable HTTP binding rules used by both HTTP peers.
 */
final class McpHttpBinding {
    static final HeaderName PROTOCOL_VERSION = HeaderName.of("mcp-protocol-version");
    static final HeaderName SESSION_ID = HeaderName.of("mcp-session-id");
    static final HeaderName METHOD = HeaderName.of("mcp-method");
    static final HeaderName NAME = HeaderName.of("mcp-name");

    private McpHttpBinding() {}

    static boolean usesMethodHeaders(McpProtocol protocol) {
        return protocol.usesHttpMethodHeaders();
    }

    static boolean isInitialize(JsonRpcRequest request) {
        return McpMethod.parse(request.getMethod()) == McpMethod.Standard.INITIALIZE;
    }

    static boolean isToolCall(JsonRpcRequest request) {
        return McpMethod.parse(request.getMethod()) == McpMethod.Standard.TOOLS_CALL;
    }

    static String requestName(JsonRpcRequest request) {
        var params = request.getParams();
        if (params == null) {
            return null;
        }
        return switch (McpMethod.parse(request.getMethod())) {
            case McpMethod.Standard.TOOLS_CALL, McpMethod.Standard.PROMPTS_GET ->
                stringMember(params, "name");
            case McpMethod.Standard.RESOURCES_READ -> stringMember(params, "uri");
            default -> null;
        };
    }

    static String protocolVersionFromMetadata(JsonRpcRequest request) {
        var params = request.getParams();
        var metadata = params == null ? null : params.getMember("_meta");
        return metadata == null ? null : stringMember(metadata, McpWireNames.PROTOCOL_VERSION);
    }

    static String stringMember(Document document, String name) {
        var member = document.getMember(name);
        return member == null || !member.isType(ShapeType.STRING) ? null : member.asString();
    }

    static String firstHeader(Map<String, List<String>> headers, String name) {
        var values = headers.get(name.toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    static Map<String, List<String>> normalizeHeaders(Map<String, List<String>> headers) {
        var normalized = new HashMap<String, List<String>>();
        headers.forEach((name, values) -> normalized.put(name.toLowerCase(Locale.ROOT), List.copyOf(values)));
        return Map.copyOf(normalized);
    }

    static String encodeParameter(String value) {
        if (value.startsWith("=?base64?")) {
            return encodeBase64(value);
        }
        for (int index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if (character < 0x20 || character > 0x7e) {
                return encodeBase64(value);
            }
        }
        return value;
    }

    private static String encodeBase64(String value) {
        return "=?base64?"
                + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8))
                + "?=";
    }

    static String decodeParameter(String value) {
        if (!value.startsWith("=?base64?") || !value.endsWith("?=")) {
            return value;
        }

        var encoded = value.substring("=?base64?".length(), value.length() - 2);
        if (encoded.length() % 4 != 0
                || !encoded.matches("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?")) {
            throw new IllegalArgumentException("Invalid Base64");
        }
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    static Map<String, String> headerParameters(ToolInfo tool) {
        var result = new HashMap<String, String>();
        var inputSchema = tool.getInputSchema();
        if (inputSchema == null || inputSchema.getProperties() == null) {
            return result;
        }

        for (var entry : inputSchema.getProperties().entrySet()) {
            var suffix = entry.getValue().getMember("x-mcp-header");
            if (suffix != null
                    && suffix.isType(ShapeType.STRING)
                    && suffix.asString().matches("[A-Za-z0-9][A-Za-z0-9_-]*")) {
                result.put(entry.getKey(), suffix.asString());
            }
        }
        return Map.copyOf(result);
    }

    static int statusCode(
            JsonRpcResponse response,
            boolean statelessClaim,
            boolean protocolVersionHeaderPresent
    ) {
        if (response.getError() == null) {
            return 200;
        }
        if (protocolVersionHeaderPresent && response.getError().getCode() == -32022) {
            return 400;
        }
        if (!statelessClaim) {
            return 200;
        }
        return switch (response.getError().getCode()) {
            case -32601 -> 404;
            case -32602, -32020, -32021, -32022 -> 400;
            default -> 200;
        };
    }
}
