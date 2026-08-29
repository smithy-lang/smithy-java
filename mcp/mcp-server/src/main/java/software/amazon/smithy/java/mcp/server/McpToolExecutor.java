/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import software.amazon.smithy.java.core.schema.SerializableShape;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.CallToolResult;
import software.amazon.smithy.java.mcp.model.TextContent;

/**
 * Executes local and remote tool targets behind one blocking interface.
 */
final class McpToolExecutor {
    private final McpSources sources;
    private final McpWireCodec wireCodec;
    private final McpInterceptor interceptor;
    private final McpProtocolRegistry protocols;

    McpToolExecutor(
            McpSources sources,
            McpWireCodec wireCodec,
            McpInterceptor interceptor,
            McpProtocolRegistry protocols
    ) {
        this.sources = sources;
        this.wireCodec = wireCodec;
        this.interceptor = interceptor;
        this.protocols = protocols;
    }

    McpOutcome execute(McpCall.CallTool call, McpRequestContext requestContext) {
        var descriptor = sources.tool(call.name());
        if (descriptor == null) {
            return new McpOutcome.Failure(
                    call.id(),
                    new McpError(-32602, "No such tool: " + call.name(), null));
        }

        var toolContext = new McpToolExecutionContext(
                call,
                requestContext,
                descriptor.serverId(),
                descriptor.target() instanceof McpToolDescriptor.RemoteTarget);
        McpOutcome outcome = null;
        RuntimeException error = null;
        try {
            interceptor.readBeforeToolCall(toolContext);
            call = interceptor.modifyBeforeToolCall(toolContext);
            toolContext = toolContext.withCall(call);
            outcome = invoke(descriptor, call, requestContext);
        } catch (RuntimeException e) {
            error = e;
        }

        try {
            interceptor.readAfterToolCall(toolContext, outcome, error);
        } catch (RuntimeException e) {
            if (error == null) {
                error = e;
            } else if (error != e) {
                error.addSuppressed(e);
            }
        }
        return interceptor.modifyAfterToolCall(toolContext, outcome, error);
    }

    private McpOutcome invoke(
            McpToolDescriptor descriptor,
            McpCall.CallTool call,
            McpRequestContext requestContext
    ) {
        return switch (descriptor.target()) {
            case McpToolDescriptor.LocalTarget local -> invokeLocal(descriptor, local, call, requestContext);
            case McpToolDescriptor.RemoteTarget remote -> invokeRemote(remote, call);
        };
    }

    private McpOutcome invokeLocal(
            McpToolDescriptor descriptor,
            McpToolDescriptor.LocalTarget target,
            McpCall.CallTool call,
            McpRequestContext requestContext
    ) {
        var operation = target.operation();
        var adapter = sources.snapshot().documentAdapter();
        var inputDocument = adapter.toSmithy(call.arguments(), operation.getApiOperation().inputSchema());
        var input = inputDocument.asShape(operation.getApiOperation().inputBuilder());

        final SerializableShape output;
        try {
            output = (SerializableShape) operation.function().apply(input, null);
        } catch (RuntimeException e) {
            return toolFailure(call, e);
        }

        var outputDocument = adapter.fromSmithy(
                Document.of(output),
                operation.getApiOperation().outputSchema());
        var result = CallToolResult.builder()
                .content(List.of(TextContent.builder()
                        .text(McpJson.CODEC.serializeToString(outputDocument))
                        .build()));
        var protocol = protocols.require(requestContext.protocolVersion());
        if (protocol.supportsOutputSchema()) {
            result.structuredContent(outputDocument);
        }
        return new McpOutcome.Success(call.id(), Document.of(result.build()));
    }

    private McpOutcome invokeRemote(
            McpToolDescriptor.RemoteTarget target,
            McpCall.CallTool call
    ) {
        return wireCodec.decode(target.client().exchange(wireCodec.encode(call)));
    }

    private McpOutcome toolFailure(McpCall.CallTool call, RuntimeException exception) {
        var cause = unwrap(exception);
        var message = cause.getMessage();
        if (message == null || message.isBlank()) {
            message = cause.getClass().getSimpleName();
        }
        var result = CallToolResult.builder()
                .content(List.of(TextContent.builder().text(message).build()))
                .isError(true)
                .build();
        return new McpOutcome.Success(call.id(), Document.of(result));
    }

    private Throwable unwrap(Throwable exception) {
        return switch (exception) {
            case CompletionException completion when completion.getCause() != null ->
                completion.getCause();
            case ExecutionException execution when execution.getCause() != null ->
                execution.getCause();
            default -> exception;
        };
    }
}
