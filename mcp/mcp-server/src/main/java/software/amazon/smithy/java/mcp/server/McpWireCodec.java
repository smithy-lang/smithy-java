/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.HashMap;
import java.util.Map;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.JsonRpcErrorResponse;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;

final class McpWireCodec {
    private final McpRequestDecoder decoder;

    McpWireCodec(Map<String, McpExtensionMethod<?>> extensions) {
        decoder = new McpRequestDecoder(extensions);
    }

    McpCall decode(JsonRpcRequest request) {
        return decoder.decode(request);
    }

    JsonRpcRequest encode(McpCall call) {
        var params = switch (call) {
            case McpCall.Initialize c -> initializeParams(c);
            case McpCall.Ping ignored -> Document.of(Map.of());
            case McpCall.Discover ignored -> Document.of(Map.of());
            case McpCall.ListTools c -> optionalParam("cursor", c.cursor());
            case McpCall.CallTool c -> Document.of(Map.of(
                    "name",
                    Document.of(c.name()),
                    "arguments",
                    c.arguments()));
            case McpCall.ListPrompts c -> optionalParam("cursor", c.cursor());
            case McpCall.GetPrompt c -> {
                var values = new HashMap<String, Document>();
                values.put("name", Document.of(c.name()));
                if (!c.arguments().isEmpty()) {
                    values.put("arguments", Document.of(c.arguments()));
                }
                yield Document.of(values);
            }
            case McpCall.Complete c -> completionParams(c);
            case McpCall.SetLogLevel c -> Document.of(Map.of("level", Document.of(c.level())));
            case McpCall.ReadResource c -> Document.of(Map.of("uri", Document.of(c.uri())));
            case McpCall.Notification c -> c.params();
            case McpCall.ExtensionCall<?> extension -> encodeExtension(extension);
            case McpCall.UnknownCall c -> c.params();
        };
        params = withMetadata(params, call.metadata());
        return JsonRpcRequest.builder()
                .jsonrpc("2.0")
                .id(call.id())
                .method(call.method().wireName())
                .params(params)
                .build();
    }

    JsonRpcResponse encode(McpOutcome outcome) {
        return switch (outcome) {
            case McpOutcome.Success success -> JsonRpcResponse.builder()
                    .jsonrpc("2.0")
                    .id(success.id())
                    .result(success.result())
                    .build();
            case McpOutcome.Failure failure -> {
                var error = JsonRpcErrorResponse.builder()
                        .code(failure.error().code())
                        .message(failure.error().message());
                if (failure.error().data() != null) {
                    error.data(failure.error().data());
                }
                yield JsonRpcResponse.builder()
                        .jsonrpc("2.0")
                        .id(failure.id())
                        .error(error.build())
                        .build();
            }
            case McpOutcome.NoResponse ignored -> null;
        };
    }

    McpOutcome decode(JsonRpcResponse response) {
        if (response == null) {
            return McpOutcome.NoResponse.INSTANCE;
        }
        if (response.getError() != null) {
            return new McpOutcome.Failure(
                    response.getId(),
                    new McpError(
                            response.getError().getCode(),
                            response.getError().getMessage(),
                            response.getError().getData()));
        }
        return new McpOutcome.Success(response.getId(), response.getResult());
    }

    private <P> Document encodeExtension(McpCall.ExtensionCall<P> call) {
        return call.extension().encode(call.parameters());
    }

    private Document initializeParams(McpCall.Initialize call) {
        var values = new HashMap<String, Document>();
        values.put("protocolVersion", Document.of(call.requestedVersion().identifier()));
        if (call.clientInfo() != null) {
            values.put("clientInfo", call.clientInfo());
        }
        if (call.capabilities() != null) {
            values.put("capabilities", call.capabilities());
        }
        return Document.of(values);
    }

    private Document completionParams(McpCall.Complete call) {
        var values = new HashMap<String, Document>();
        if (call.reference() != null) {
            var reference = new HashMap<String, Document>();
            if (call.reference().type() != null) {
                reference.put("type", Document.of(call.reference().type()));
            }
            if (call.reference().name() != null) {
                reference.put("name", Document.of(call.reference().name()));
            }
            values.put("ref", Document.of(reference));
        }
        if (call.argument() != null) {
            var argument = new HashMap<String, Document>();
            if (call.argument().name() != null) {
                argument.put("name", Document.of(call.argument().name()));
            }
            if (call.argument().value() != null) {
                argument.put("value", Document.of(call.argument().value()));
            }
            values.put("argument", Document.of(argument));
        }
        return Document.of(values);
    }

    private Document optionalParam(String name, String value) {
        return value == null ? Document.of(Map.of()) : Document.of(Map.of(name, Document.of(value)));
    }

    private Document withMetadata(Document params, McpMetadata metadata) {
        if (metadata == null || metadata == McpMetadata.EMPTY) {
            return params;
        }
        var values = new HashMap<>(params.asStringMap());
        var meta = new HashMap<>(metadata.extensions());
        if (metadata.protocolVersion() != null) {
            meta.put(McpWireNames.PROTOCOL_VERSION, Document.of(metadata.protocolVersion().identifier()));
        }
        if (metadata.clientInfo() != null) {
            meta.put(McpWireNames.CLIENT_INFO, metadata.clientInfo());
        }
        if (metadata.clientCapabilities() != null) {
            meta.put(McpWireNames.CLIENT_CAPABILITIES, metadata.clientCapabilities());
        }
        if (!meta.isEmpty()) {
            values.put("_meta", Document.of(meta));
        }
        return Document.of(values);
    }
}
