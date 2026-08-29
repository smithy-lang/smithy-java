/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.mcp.model.Capabilities;
import software.amazon.smithy.java.mcp.model.InitializeResult;
import software.amazon.smithy.java.mcp.model.ListPromptsResult;
import software.amazon.smithy.java.mcp.model.ListToolsResult;
import software.amazon.smithy.java.mcp.model.Prompts;
import software.amazon.smithy.java.mcp.model.ServerInfo;
import software.amazon.smithy.java.mcp.model.ToolInfo;
import software.amazon.smithy.java.mcp.model.Tools;
import software.amazon.smithy.model.shapes.ShapeType;

/**
 * Protocol-independent implementation of MCP domain operations.
 */
final class McpDomainOperations implements McpOperations {
    private final McpSources sources;
    private final McpWireCodec wireCodec;
    private final McpServerIdentity identity;
    private final ToolFilter toolFilter;
    private final McpMetricsObserver metricsObserver;
    private final McpToolExecutor toolExecutor;
    private final McpProtocolRegistry protocols;

    McpDomainOperations(
            McpSources sources,
            McpWireCodec wireCodec,
            McpServerIdentity identity,
            ToolFilter toolFilter,
            McpMetricsObserver metricsObserver,
            McpInterceptor interceptor,
            McpProtocolRegistry protocols
    ) {
        this.sources = sources;
        this.wireCodec = wireCodec;
        this.identity = identity;
        this.toolFilter = toolFilter;
        this.metricsObserver = metricsObserver;
        this.protocols = protocols;
        this.toolExecutor = new McpToolExecutor(
                sources,
                wireCodec,
                interceptor,
                protocols,
                toolFilter);
    }

    @Override
    public McpOutcome initialize(McpCall.Initialize call, McpRequestContext context) {
        observeInitialize(call);
        var protocol = protocols.require(context.protocolVersion());
        sources.initializeRemoteClients(protocol);

        var result = InitializeResult.builder()
                .protocolVersion(context.protocolVersion().identifier())
                .capabilities(initializeCapabilities(protocol, context.transport()))
                .serverInfo(ServerInfo.builder()
                        .name(identity.name())
                        .version(identity.version())
                        .build())
                .build();
        return new McpOutcome.Success(call.id(), Document.of(result));
    }

    @Override
    public McpOutcome ping(McpCall.Ping call, McpRequestContext context) {
        return new McpOutcome.Success(call.id(), Document.of(Map.of()));
    }

    @Override
    public McpOutcome discover(McpCall.Discover call, McpRequestContext context) {
        var protocol = protocols.require(context.protocolVersion());
        sources.ensureRemoteCatalogLoaded(protocol);
        var capabilities = discoverCapabilities(protocol);
        return new McpOutcome.Success(
                call.id(),
                Document.of(Map.of(
                        "supportedVersions",
                        Document.of(protocols.supportedIdentifiers()
                                .stream()
                                .map(Document::of)
                                .toList()),
                        "capabilities",
                        capabilities)));
    }

    @Override
    public McpOutcome listTools(McpCall.ListTools call, McpRequestContext context) {
        var protocol = protocols.require(context.protocolVersion());
        sources.ensureRemoteCatalogLoaded(protocol);
        var page = sources.listTools(call.cursor());
        var tools = page.items()
                .stream()
                .filter(tool -> toolFilter.allowTool(tool.serverId(), tool.info().getName()))
                .map(tool -> projectTool(tool.info(), protocol))
                .toList();
        var result = ListToolsResult.builder().tools(tools);
        if (page.nextCursor() != null) {
            result.nextCursor(page.nextCursor());
        }
        return new McpOutcome.Success(
                call.id(),
                Document.of(result.build()));
    }

    @Override
    public McpOutcome callTool(McpCall.CallTool call, McpRequestContext context) {
        sources.ensureRemoteCatalogLoaded(protocols.require(context.protocolVersion()));
        if (metricsObserver != null) {
            metricsObserver.onToolCall(call.method().wireName(), call.name());
        }
        return toolExecutor.execute(call, context);
    }

    @Override
    public McpOutcome listPrompts(McpCall.ListPrompts call, McpRequestContext context) {
        sources.ensureRemoteCatalogLoaded(protocols.require(context.protocolVersion()));
        var page = sources.listPrompts(call.cursor());
        var prompts = page.items()
                .stream()
                .map(descriptor -> descriptor.prompt().promptInfo())
                .toList();
        var result = ListPromptsResult.builder().prompts(prompts);
        if (page.nextCursor() != null) {
            result.nextCursor(page.nextCursor());
        }
        return new McpOutcome.Success(
                call.id(),
                Document.of(result.build()));
    }

    @Override
    public McpOutcome getPrompt(McpCall.GetPrompt call, McpRequestContext context) {
        var protocol = protocols.require(context.protocolVersion());
        sources.ensureRemoteCatalogLoaded(protocol);
        var prompt = sources.prompt(PromptLoader.normalize(call.name()));
        if (prompt == null) {
            return new McpOutcome.Failure(
                    call.id(),
                    new McpError(-32602, "Prompt not found: " + call.name(), null));
        }
        var arguments = call.arguments().isEmpty() ? null : Document.of(call.arguments());
        return new McpOutcome.Success(
                call.id(),
                Document.of(prompt.prompt()
                        .getPromptResult(
                                arguments,
                                call.id(),
                                call.metadata(),
                                protocol)));
    }

    private Capabilities initializeCapabilities(
            McpProtocol protocol,
            McpTransportContext transport
    ) {
        var builder = Capabilities.builder();
        if (supports(protocol, McpMethod.Standard.TOOLS_LIST)) {
            var tools = Tools.builder();
            if (transport.supportsServerNotifications()) {
                tools.listChanged(true);
            }
            builder.tools(tools.build());
        }
        if (supports(protocol, McpMethod.Standard.PROMPTS_LIST)) {
            var prompts = Prompts.builder();
            if (transport.supportsServerNotifications()) {
                prompts.listChanged(true);
            }
            builder.prompts(prompts.build());
        }
        return builder.build();
    }

    private Document discoverCapabilities(McpProtocol protocol) {
        var capabilities = new HashMap<String, Document>();
        if (supports(protocol, McpMethod.Standard.TOOLS_LIST)) {
            capabilities.put("tools", Document.of(Map.of()));
        }
        if (supports(protocol, McpMethod.Standard.PROMPTS_LIST)) {
            capabilities.put("prompts", Document.of(Map.of()));
        }
        return Document.of(capabilities);
    }

    private boolean supports(McpProtocol protocol, McpMethod.Standard method) {
        return protocol.supportedMethods().contains(method);
    }

    @Override
    public McpOutcome complete(McpCall.Complete call, McpRequestContext context) {
        var completion = Document.of(Map.of(
                "values",
                Document.of(List.of()),
                "total",
                Document.of(0),
                "hasMore",
                Document.of(false)));
        return new McpOutcome.Success(call.id(), Document.of(Map.of("completion", completion)));
    }

    @Override
    public McpOutcome setLogLevel(McpCall.SetLogLevel call, McpRequestContext context) {
        return new McpOutcome.Success(call.id(), Document.of(Map.of()));
    }

    private ToolInfo projectTool(ToolInfo tool, McpProtocol protocol) {
        boolean stripOutput = !protocol.supportsOutputSchema() && tool.getOutputSchema() != null;
        boolean stripAnnotations = !protocol.supportsAnnotations() && tool.getAnnotations() != null;
        if (!stripOutput && !stripAnnotations) {
            return tool;
        }
        var builder = tool.toBuilder();
        if (stripOutput) {
            builder.outputSchema(null);
        }
        if (stripAnnotations) {
            builder.annotations(null);
        }
        return builder.build();
    }

    private void observeInitialize(McpCall.Initialize call) {
        if (metricsObserver == null) {
            return;
        }
        var capabilities = call.capabilities();
        var clientInfo = call.clientInfo();
        var roots = objectMember(capabilities, "roots");
        var listChanged = objectMember(roots, "listChanged");
        boolean rootsListChanged = listChanged != null
                && listChanged.isType(ShapeType.BOOLEAN)
                && listChanged.asBoolean();
        boolean sampling = objectMember(capabilities, "sampling") != null;
        boolean elicitation = objectMember(capabilities, "elicitation") != null;
        metricsObserver.onInitialize(
                call.method().wireName(),
                call.requestedVersion().identifier(),
                rootsListChanged,
                sampling,
                elicitation,
                stringMember(clientInfo, "name"),
                stringMember(clientInfo, "title"));
    }

    private String stringMember(Document document, String name) {
        var member = objectMember(document, name);
        return member != null && member.isType(ShapeType.STRING) ? member.asString() : null;
    }

    private Document objectMember(Document document, String name) {
        return McpHttpBinding.isObject(document) ? document.getMember(name) : null;
    }
}
