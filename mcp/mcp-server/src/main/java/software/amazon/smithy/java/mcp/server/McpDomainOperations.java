/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

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
        this.toolExecutor = new McpToolExecutor(sources, wireCodec, interceptor, protocols);
    }

    @Override
    public McpOutcome initialize(McpCall.Initialize call, McpRequestContext context) {
        observeInitialize(call);
        sources.initializeRemoteClients(
                wireCodec.encode(call),
                protocols.require(context.protocolVersion()));

        var result = InitializeResult.builder()
                .protocolVersion(context.protocolVersion().identifier())
                .capabilities(Capabilities.builder()
                        .completions(Document.of(Map.of()))
                        .logging(Document.of(Map.of()))
                        .tools(Tools.builder().listChanged(true).build())
                        .prompts(Prompts.builder().listChanged(true).build())
                        .build())
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
        sources.ensureRemoteCatalogLoaded();
        var capabilities = Document.of(Map.of(
                "completions",
                Document.of(Map.of()),
                "tools",
                Document.of(Map.of()),
                "prompts",
                Document.of(Map.of())));
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
        sources.ensureRemoteCatalogLoaded();
        var protocol = protocols.require(context.protocolVersion());
        var tools = sources.snapshot()
                .tools()
                .values()
                .stream()
                .filter(tool -> toolFilter.allowTool(tool.serverId(), tool.info().getName()))
                .map(tool -> projectTool(tool.info(), protocol))
                .toList();
        return new McpOutcome.Success(
                call.id(),
                Document.of(ListToolsResult.builder().tools(tools).build()));
    }

    @Override
    public McpOutcome callTool(McpCall.CallTool call, McpRequestContext context) {
        sources.ensureRemoteCatalogLoaded();
        if (metricsObserver != null) {
            metricsObserver.onToolCall(call.method().wireName(), call.name());
        }
        return toolExecutor.execute(call, context);
    }

    @Override
    public McpOutcome listPrompts(McpCall.ListPrompts call, McpRequestContext context) {
        sources.ensureRemoteCatalogLoaded();
        var prompts = sources.snapshot()
                .prompts()
                .values()
                .stream()
                .map(descriptor -> descriptor.prompt().promptInfo())
                .toList();
        return new McpOutcome.Success(
                call.id(),
                Document.of(ListPromptsResult.builder().prompts(prompts).build()));
    }

    @Override
    public McpOutcome getPrompt(McpCall.GetPrompt call, McpRequestContext context) {
        sources.ensureRemoteCatalogLoaded();
        var prompt = sources.prompt(PromptLoader.normalize(call.name()));
        if (prompt == null) {
            return new McpOutcome.Failure(
                    call.id(),
                    new McpError(-32602, "Prompt not found: " + call.name(), null));
        }
        var arguments = call.arguments().isEmpty() ? null : Document.of(call.arguments());
        return new McpOutcome.Success(
                call.id(),
                Document.of(prompt.prompt().getPromptResult(arguments, call.id())));
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

    @Override
    public McpOutcome readResource(McpCall.ReadResource call, McpRequestContext context) {
        throw new UnsupportedOperationException("resources/read is not implemented");
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
        boolean rootsListChanged = capabilities != null
                && capabilities.getMember("roots") != null
                && capabilities.getMember("roots").getMember("listChanged") != null
                && capabilities.getMember("roots").getMember("listChanged").asBoolean();
        boolean sampling = capabilities != null && capabilities.getMember("sampling") != null;
        boolean elicitation = capabilities != null && capabilities.getMember("elicitation") != null;
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
        if (document == null) {
            return null;
        }
        var member = document.getMember(name);
        return member == null ? null : member.asString();
    }
}
