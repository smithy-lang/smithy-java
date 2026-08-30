/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.model.shapes.ShapeType;

final class McpRequestDecoder {
    private static final int INVALID_PARAMS = -32602;

    private final Map<String, McpExtensionMethod<?>> extensions;

    McpRequestDecoder(Map<String, McpExtensionMethod<?>> extensions) {
        this.extensions = Map.copyOf(extensions);
    }

    McpCall decode(JsonRpcRequest request) {
        Objects.requireNonNull(request, "request");
        var params = request.getParams();
        var metadata = decodeMetadata(params);
        return switch (McpMethod.parse(request.getMethod())) {
            case McpMethod.Standard.INITIALIZE -> decodeInitialize(request, params, metadata);
            case McpMethod.Standard.PING -> new McpCall.Ping(request.getId(), metadata);
            case McpMethod.Standard.SERVER_DISCOVER -> new McpCall.Discover(request.getId(), metadata);
            case McpMethod.Standard.TOOLS_LIST ->
                new McpCall.ListTools(request.getId(), optionalString(params, "cursor"), metadata);
            case McpMethod.Standard.TOOLS_CALL -> new McpCall.CallTool(
                    request.getId(),
                    requiredString(params, "name"),
                    member(params, "arguments"),
                    metadata);
            case McpMethod.Standard.PROMPTS_LIST ->
                new McpCall.ListPrompts(request.getId(), optionalString(params, "cursor"), metadata);
            case McpMethod.Standard.PROMPTS_GET -> new McpCall.GetPrompt(
                    request.getId(),
                    requiredString(params, "name"),
                    documentMap(member(params, "arguments")),
                    metadata);
            case McpMethod.Standard.COMPLETION_COMPLETE -> decodeComplete(request, params, metadata);
            case McpMethod.Standard.LOGGING_SET_LEVEL ->
                new McpCall.SetLogLevel(request.getId(), optionalString(params, "level"), metadata);
            case McpMethod.Standard.RESOURCES_READ ->
                new McpCall.ReadResource(request.getId(), requiredString(params, "uri"), metadata);
            case McpMethod.Standard standard when standard.wireName().startsWith("notifications/") ->
                new McpCall.Notification(standard, params, metadata);
            case McpMethod.Standard standard ->
                new McpCall.UnknownCall(
                        request.getId(),
                        new McpMethod.Unknown(standard.wireName()),
                        params,
                        metadata);
            case McpMethod.Extension extension -> decodeExtension(request, extension, params, metadata);
            case McpMethod.Unknown unknown -> {
                var extension = extensions.get(unknown.wireName());
                yield extension == null
                        ? new McpCall.UnknownCall(request.getId(), unknown, params, metadata)
                        : decodeExtension(request, new McpMethod.Extension(unknown.wireName()), params, metadata);
            }
        };
    }

    private McpCall.Initialize decodeInitialize(
            JsonRpcRequest request,
            Document params,
            McpMetadata metadata
    ) {
        var identifier = optionalString(params, "protocolVersion");
        return new McpCall.Initialize(
                request.getId(),
                ProtocolVersion.parse(identifier),
                member(params, "clientInfo"),
                member(params, "capabilities"),
                metadata);
    }

    private McpCall.Complete decodeComplete(
            JsonRpcRequest request,
            Document params,
            McpMetadata metadata
    ) {
        var reference = member(params, "ref");
        var argument = member(params, "argument");
        return new McpCall.Complete(
                request.getId(),
                reference == null
                        ? null
                        : new McpCall.CompletionReference(
                                optionalString(reference, "type"),
                                optionalString(reference, "name")),
                argument == null
                        ? null
                        : new McpCall.CompletionArgument(
                                optionalString(argument, "name"),
                                optionalString(argument, "value")),
                metadata);
    }

    @SuppressWarnings("unchecked")
    private <P> McpCall.ExtensionCall<P> decodeExtension(
            JsonRpcRequest request,
            McpMethod.Extension method,
            Document params,
            McpMetadata metadata
    ) {
        var extension = (McpExtensionMethod<P>) extensions.get(method.wireName());
        if (extension == null) {
            throw new IllegalStateException("Unregistered MCP extension: " + method.wireName());
        }
        return new McpCall.ExtensionCall<>(request.getId(), extension, extension.decode(params), metadata);
    }

    private McpMetadata decodeMetadata(Document params) {
        var meta = member(params, "_meta");
        if (meta == null) {
            return McpMetadata.EMPTY;
        }
        if (!isObject(meta)) {
            throw invalidParams("params._meta must be an object");
        }

        var values = new HashMap<>(meta.asStringMap());
        var version = removeString(values, McpWireNames.PROTOCOL_VERSION);
        var clientInfo = values.remove(McpWireNames.CLIENT_INFO);
        var capabilities = values.remove(McpWireNames.CLIENT_CAPABILITIES);
        return new McpMetadata(
                version == null ? null : ProtocolVersion.parse(version),
                clientInfo,
                capabilities,
                values);
    }

    private String removeString(Map<String, Document> values, String name) {
        var value = values.remove(name);
        if (value == null) {
            return null;
        }
        if (!value.isType(ShapeType.STRING)) {
            throw invalidParams(name + " must be a string");
        }
        return value.asString();
    }

    private String requiredString(Document document, String name) {
        var value = optionalString(document, name);
        if (value == null) {
            throw invalidParams("Missing or invalid string parameter: " + name);
        }
        return value;
    }

    private String optionalString(Document document, String name) {
        var value = member(document, name);
        if (value == null) {
            return null;
        }
        if (!value.isType(ShapeType.STRING)) {
            throw invalidParams(name + " must be a string");
        }
        return value.asString();
    }

    private Document member(Document document, String name) {
        return document == null || !isObject(document) ? null : document.getMember(name);
    }

    private Map<String, Document> documentMap(Document document) {
        return document == null ? Map.of() : Map.copyOf(document.asStringMap());
    }

    private boolean isObject(Document document) {
        return document.isType(ShapeType.MAP) || document.isType(ShapeType.STRUCTURE);
    }

    private McpProtocolException invalidParams(String message) {
        return new McpProtocolException(INVALID_PARAMS, message);
    }
}
