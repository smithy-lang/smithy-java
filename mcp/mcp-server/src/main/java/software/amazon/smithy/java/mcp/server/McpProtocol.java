/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Set;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.model.shapes.ShapeType;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Behavior of one MCP protocol version.
 *
 * <p>Supported methods and wire features are declared as immutable protocol data.
 */
@SmithyUnstableApi
public sealed interface McpProtocol permits BuiltInProtocol, ExtensionMcpProtocol {
    McpProtocolId id();

    default Set<McpMethod.Standard> supportedMethods() {
        return Set.of();
    }

    default McpProtocolFeatures features() {
        return McpProtocolFeatures.NONE;
    }

    default ProtocolVersion protocolVersion() {
        return ProtocolVersion.parse(id().identifier());
    }

    default void validate(McpCall call, McpRequestContext context) {
        var id = call.id();
        if (id != null
                && !(id.isType(ShapeType.INTEGER)
                        || id.isType(ShapeType.LONG)
                        || id.isType(ShapeType.BIG_INTEGER)
                        || id.isType(ShapeType.STRING))) {
            throw new McpProtocolException(-32602, "Request id is of invalid type " + id.type().name());
        }
        if (id == null && !call.method().wireName().startsWith("notifications/")) {
            throw new McpProtocolException(-32602, "Requests are expected to have ids");
        }
        if (!usesStatelessMetadata()) {
            return;
        }

        var metadata = call.metadata();
        if (metadata.protocolVersion() == null) {
            throw new McpProtocolException(
                    -32602,
                    "Missing " + McpWireNames.PROTOCOL_VERSION + " in params._meta");
        }
        if (!metadata.protocolVersion().identifier().equals(id().identifier())) {
            throw new McpProtocolException(
                    -32022,
                    "Unsupported protocol version: " + metadata.protocolVersion().identifier());
        }
        var capabilities = metadata.clientCapabilities();
        if (capabilities == null
                || !(capabilities.isType(ShapeType.MAP) || capabilities.isType(ShapeType.STRUCTURE))) {
            throw new McpProtocolException(
                    -32602,
                    "Missing or invalid " + McpWireNames.CLIENT_CAPABILITIES + " in params._meta");
        }
    }

    default McpOutcome dispatch(McpCall call, McpOperations operations, McpRequestContext context) {
        if (call instanceof McpCall.ExtensionCall<?> extension) {
            return executeExtension(extension, context);
        }
        if (!(call.method() instanceof McpMethod.Standard standard)
                || !supportedMethods().contains(standard)) {
            throw unsupported(call.method());
        }

        return switch (call) {
            case McpCall.Initialize initialize -> operations.initialize(initialize, context);
            case McpCall.Ping ping -> operations.ping(ping, context);
            case McpCall.Discover discover -> operations.discover(discover, context);
            case McpCall.ListTools listTools -> operations.listTools(listTools, context);
            case McpCall.CallTool callTool -> operations.callTool(callTool, context);
            case McpCall.ListPrompts listPrompts -> operations.listPrompts(listPrompts, context);
            case McpCall.GetPrompt getPrompt -> operations.getPrompt(getPrompt, context);
            case McpCall.Complete complete -> operations.complete(complete, context);
            case McpCall.SetLogLevel setLogLevel -> {
                if (setLogLevel.level() == null) {
                    throw new McpProtocolException(-32602, "Missing or invalid string parameter: level");
                }
                yield operations.setLogLevel(setLogLevel, context);
            }
            case McpCall.ReadResource readResource -> operations.readResource(readResource, context);
            case McpCall.Notification ignored -> McpOutcome.NoResponse.INSTANCE;
            case McpCall.ExtensionCall<?> ignored ->
                throw new IllegalStateException("Extension calls are dispatched before standard calls");
            case McpCall.UnknownCall unknown -> throw unsupported(unknown.method());
        };
    }

    private static <P> McpOutcome executeExtension(
            McpCall.ExtensionCall<P> extension,
            McpRequestContext context
    ) {
        return extension.extension().execute(extension, context);
    }

    default boolean supportsOutputSchema() {
        return features().outputSchema();
    }

    default boolean supportsAnnotations() {
        return features().annotations();
    }

    default boolean usesStatelessMetadata() {
        return features().statelessMetadata();
    }

    default boolean usesHttpMethodHeaders() {
        return features().httpMethodHeaders();
    }

    default Document decorateResult(
            Document result,
            McpMethod method,
            McpServerIdentity serverIdentity
    ) {
        return result;
    }

    default McpUnsupportedMethodException unsupported(McpMethod method) {
        return new McpUnsupportedMethodException(method, id());
    }
}
