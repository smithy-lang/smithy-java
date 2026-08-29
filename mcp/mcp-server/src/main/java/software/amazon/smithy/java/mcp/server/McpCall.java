/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * A decoded, typed MCP call.
 *
 * <p>Dynamic Smithy tool arguments and extension payloads remain documents because
 * their schemas are selected at runtime. Standard protocol parameters are represented
 * explicitly by records.
 */
@SmithyUnstableApi
public sealed interface McpCall permits
        McpCall.Initialize,
        McpCall.Ping,
        McpCall.Discover,
        McpCall.ListTools,
        McpCall.CallTool,
        McpCall.ListPrompts,
        McpCall.GetPrompt,
        McpCall.Complete,
        McpCall.SetLogLevel,
        McpCall.ReadResource,
        McpCall.Notification,
        McpCall.ExtensionCall,
        McpCall.UnknownCall {

    Document id();

    McpMethod method();

    McpMetadata metadata();

    record Initialize(
            Document id,
            ProtocolVersion requestedVersion,
            Document clientInfo,
            Document capabilities,
            McpMetadata metadata) implements McpCall {
        public Initialize {
            metadata = metadata == null ? McpMetadata.EMPTY : metadata;
        }

        @Override
        public McpMethod method() {
            return McpMethod.Standard.INITIALIZE;
        }
    }

    record Ping(Document id, McpMetadata metadata) implements McpCall {
        public Ping {
            metadata = metadata == null ? McpMetadata.EMPTY : metadata;
        }

        @Override
        public McpMethod method() {
            return McpMethod.Standard.PING;
        }
    }

    record Discover(Document id, McpMetadata metadata) implements McpCall {
        public Discover {
            metadata = metadata == null ? McpMetadata.EMPTY : metadata;
        }

        @Override
        public McpMethod method() {
            return McpMethod.Standard.SERVER_DISCOVER;
        }
    }

    record ListTools(Document id, String cursor, McpMetadata metadata) implements McpCall {
        public ListTools {
            metadata = metadata == null ? McpMetadata.EMPTY : metadata;
        }

        @Override
        public McpMethod method() {
            return McpMethod.Standard.TOOLS_LIST;
        }
    }

    record CallTool(Document id, String name, Document arguments, McpMetadata metadata) implements McpCall {
        public CallTool {
            Objects.requireNonNull(name, "name");
            arguments = arguments == null ? Document.of(Map.of()) : arguments;
            metadata = metadata == null ? McpMetadata.EMPTY : metadata;
        }

        @Override
        public McpMethod method() {
            return McpMethod.Standard.TOOLS_CALL;
        }
    }

    record ListPrompts(Document id, String cursor, McpMetadata metadata) implements McpCall {
        public ListPrompts {
            metadata = metadata == null ? McpMetadata.EMPTY : metadata;
        }

        @Override
        public McpMethod method() {
            return McpMethod.Standard.PROMPTS_LIST;
        }
    }

    record GetPrompt(
            Document id,
            String name,
            Map<String, Document> arguments,
            McpMetadata metadata) implements McpCall {
        public GetPrompt {
            Objects.requireNonNull(name, "name");
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
            metadata = metadata == null ? McpMetadata.EMPTY : metadata;
        }

        @Override
        public McpMethod method() {
            return McpMethod.Standard.PROMPTS_GET;
        }
    }

    record Complete(
            Document id,
            CompletionReference reference,
            CompletionArgument argument,
            McpMetadata metadata) implements McpCall {
        public Complete {
            metadata = metadata == null ? McpMetadata.EMPTY : metadata;
        }

        @Override
        public McpMethod method() {
            return McpMethod.Standard.COMPLETION_COMPLETE;
        }
    }

    record CompletionReference(String type, String name) {}

    record CompletionArgument(String name, String value) {}

    record SetLogLevel(Document id, String level, McpMetadata metadata) implements McpCall {
        public SetLogLevel {
            metadata = metadata == null ? McpMetadata.EMPTY : metadata;
        }

        @Override
        public McpMethod method() {
            return McpMethod.Standard.LOGGING_SET_LEVEL;
        }
    }

    record ReadResource(Document id, String uri, McpMetadata metadata) implements McpCall {
        public ReadResource {
            Objects.requireNonNull(uri, "uri");
            metadata = metadata == null ? McpMetadata.EMPTY : metadata;
        }

        @Override
        public McpMethod method() {
            return McpMethod.Standard.RESOURCES_READ;
        }
    }

    record Notification(McpMethod.Standard method, Document params, McpMetadata metadata) implements McpCall {
        public Notification {
            Objects.requireNonNull(method, "method");
            params = params == null ? Document.of(Map.of()) : params;
            metadata = metadata == null ? McpMetadata.EMPTY : metadata;
            if (!method.wireName().startsWith("notifications/")) {
                throw new IllegalArgumentException("Not a notification method: " + method.wireName());
            }
        }

        @Override
        public Document id() {
            return null;
        }
    }

    record ExtensionCall<P>(
            Document id,
            McpExtensionMethod<P> extension,
            P parameters,
            McpMetadata metadata) implements McpCall {
        public ExtensionCall {
            Objects.requireNonNull(extension, "extension");
            metadata = metadata == null ? McpMetadata.EMPTY : metadata;
        }

        @Override
        public McpMethod method() {
            return new McpMethod.Extension(extension.method());
        }
    }

    record UnknownCall(
            Document id,
            McpMethod.Unknown method,
            Document params,
            McpMetadata metadata) implements McpCall {
        public UnknownCall {
            Objects.requireNonNull(method, "method");
            params = params == null ? Document.of(Map.of()) : params;
            metadata = metadata == null ? McpMetadata.EMPTY : metadata;
        }
    }
}
