/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.serde.document.Document;
import software.amazon.smithy.java.logging.InternalLogger;
import software.amazon.smithy.java.mcp.model.JsonRpcRequest;
import software.amazon.smithy.java.mcp.model.JsonRpcResponse;
import software.amazon.smithy.java.server.Service;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Blocking, transport-independent MCP execution engine.
 *
 * <p>The engine operates on typed calls and outcomes. Transports own concurrency and
 * should invoke this blocking API from virtual threads when concurrent execution is
 * desired.
 */
@SmithyUnstableApi
public final class McpEngine implements AutoCloseable {
    private static final InternalLogger LOG = InternalLogger.getLogger(McpEngine.class);

    private final McpSources sources;
    private final McpDomainOperations operations;
    private final McpWireCodec wireCodec;
    private final McpInterceptor interceptor;
    private final McpServerIdentity identity;
    private final McpProtocolRegistry protocols;

    private McpEngine(Builder builder) {
        identity = new McpServerIdentity(builder.name, builder.version);
        protocols = McpProtocolRegistry.create(
                builder.protocols.values(),
                builder.protocolOverrides.values(),
                builder.discoverProtocols);
        wireCodec = new McpWireCodec(builder.extensions);
        interceptor = builder.interceptor;
        sources = new McpCatalog(builder.services, builder.remoteClients);
        operations = new McpDomainOperations(
                sources,
                wireCodec,
                identity,
                builder.toolFilter,
                builder.metricsObserver,
                interceptor,
                protocols);
    }

    /**
     * Executes a typed call synchronously.
     */
    public McpOutcome execute(McpCall call, McpRequestContext requestContext) {
        var executionContext = new McpExecutionContext(call, requestContext);
        McpOutcome outcome = null;
        RuntimeException error = null;

        try {
            interceptor.readBeforeExecution(executionContext);
            call = interceptor.modifyBeforeExecution(executionContext);
            executionContext = executionContext.withCall(call);

            var protocol = protocols.require(requestContext.protocolVersion());
            protocol.validate(call, requestContext);
            outcome = protocol.dispatch(call, operations, requestContext);
            if (outcome instanceof McpOutcome.Success(Document id, Document result)) {
                outcome = new McpOutcome.Success(
                        id,
                        protocol.decorateResult(result, call.method(), identity));
            }
        } catch (RuntimeException e) {
            error = e;
        }

        try {
            interceptor.readAfterExecution(executionContext, outcome, error);
        } catch (RuntimeException e) {
            error = preserveOriginal(error, e);
        }

        try {
            return interceptor.modifyAfterExecution(executionContext, outcome, error);
        } catch (RuntimeException e) {
            return errorOutcome(call, e);
        }
    }

    /**
     * Executes a decoded JSON-RPC request with an explicit protocol claim.
     *
     * <p>This is primarily useful for transport adapters. Application code should
     * prefer the typed-call overload.
     */
    public JsonRpcResponse execute(JsonRpcRequest request, ProtocolVersion protocolVersion) {
        var session = newSession();
        var outcome = execute(request, session, protocolVersion, McpTransportContext.STDIO);
        return encode(outcome);
    }

    McpOutcome execute(
            JsonRpcRequest request,
            McpSession session,
            ProtocolVersion transportClaim,
            McpTransportContext transportContext
    ) {
        final McpCall call;
        try {
            call = wireCodec.decode(request);
        } catch (RuntimeException e) {
            return errorOutcome(request.getId(), e);
        }

        final ProtocolVersion version;
        try {
            version = session.negotiate(call, transportClaim);
        } catch (RuntimeException e) {
            return errorOutcome(call, e);
        }
        return execute(call, new McpRequestContext(version, transportContext, Context.create()));
    }

    JsonRpcRequest encode(McpCall call) {
        return wireCodec.encode(call);
    }

    JsonRpcResponse encode(McpOutcome outcome) {
        return wireCodec.encode(outcome);
    }

    McpOutcome decode(JsonRpcResponse response) {
        return wireCodec.decode(response);
    }

    McpServerIdentity identity() {
        return identity;
    }

    McpSession newSession() {
        return new McpSession(protocols);
    }

    McpProtocol protocol(ProtocolVersion version) {
        return protocols.require(version);
    }

    McpProtocol findProtocol(ProtocolVersion version) {
        return protocols.find(version);
    }

    void bindTransport(
            Consumer<JsonRpcRequest> notificationWriter,
            Consumer<JsonRpcResponse> responseWriter
    ) {
        sources.bindTransport(notificationWriter, responseWriter);
    }

    void addService(String id, Service service) {
        sources.addService(id, service);
    }

    void addRemoteClient(McpRemoteClient client) {
        sources.addRemoteClient(client);
    }

    boolean containsServer(String id) {
        return sources.containsServer(id);
    }

    Map<String, McpRemoteClient> remoteClients() {
        return sources.remoteClients();
    }

    Map<String, String> headerParameters(String toolName) {
        return sources.headerParameters(toolName);
    }

    @Override
    public void close() {
        sources.close();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, Service> services = new HashMap<>();
        private final List<McpRemoteClient> remoteClients = new ArrayList<>();
        private final Map<String, McpExtensionMethod<?>> extensions = new HashMap<>();
        private final Map<McpProtocolId, ExtensionMcpProtocol> protocols = new LinkedHashMap<>();
        private final Map<McpProtocolId, ExtensionMcpProtocol> protocolOverrides = new LinkedHashMap<>();
        private McpInterceptor interceptor = McpInterceptor.NOOP;
        private String name = "mcp-server";
        private String version = "1.0.0";
        private ToolFilter toolFilter = (serverId, toolName) -> true;
        private McpMetricsObserver metricsObserver;
        private boolean discoverProtocols = true;

        public Builder services(Map<String, Service> services) {
            this.services.clear();
            this.services.putAll(services);
            return this;
        }

        public Builder addService(String id, Service service) {
            services.put(id, service);
            return this;
        }

        public Builder remoteClients(List<McpRemoteClient> remoteClients) {
            this.remoteClients.clear();
            this.remoteClients.addAll(remoteClients);
            return this;
        }

        public Builder addRemoteClient(McpRemoteClient remoteClient) {
            remoteClients.add(remoteClient);
            return this;
        }

        public Builder addExtension(McpExtensionMethod<?> extension) {
            Objects.requireNonNull(extension, "extension");
            if (McpMethod.parse(extension.method()) instanceof McpMethod.Standard) {
                throw new IllegalArgumentException("Cannot replace standard MCP method: " + extension.method());
            }
            if (extensions.put(extension.method(), extension) != null) {
                throw new IllegalArgumentException("Duplicate MCP extension method: " + extension.method());
            }
            return this;
        }

        public Builder addProtocol(ExtensionMcpProtocol protocol) {
            putProtocol(protocols, protocol, "protocol");
            return this;
        }

        public Builder overrideProtocol(ExtensionMcpProtocol protocol) {
            putProtocol(protocolOverrides, protocol, "protocol override");
            return this;
        }

        public Builder discoverProtocols(boolean discoverProtocols) {
            this.discoverProtocols = discoverProtocols;
            return this;
        }

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        public Builder version(String version) {
            this.version = Objects.requireNonNull(version, "version");
            return this;
        }

        public Builder toolFilter(ToolFilter toolFilter) {
            this.toolFilter = Objects.requireNonNull(toolFilter, "toolFilter");
            return this;
        }

        public Builder metricsObserver(McpMetricsObserver metricsObserver) {
            this.metricsObserver = metricsObserver;
            return this;
        }

        public Builder interceptor(McpInterceptor interceptor) {
            this.interceptor = Objects.requireNonNull(interceptor, "interceptor");
            return this;
        }

        public McpEngine build() {
            return new McpEngine(this);
        }

        private void putProtocol(
                Map<McpProtocolId, ExtensionMcpProtocol> destination,
                ExtensionMcpProtocol protocol,
                String kind
        ) {
            Objects.requireNonNull(protocol, kind);
            Objects.requireNonNull(protocol.id(), kind + " id");
            if (destination.put(protocol.id(), protocol) != null) {
                throw new IllegalArgumentException(
                        "Duplicate MCP " + kind + ": " + protocol.id().identifier());
            }
        }
    }

    private McpOutcome errorOutcome(McpCall call, RuntimeException error) {
        if (call.id() == null) {
            return McpOutcome.NoResponse.INSTANCE;
        }
        if (error instanceof McpUnsupportedMethodException) {
            return new McpOutcome.Failure(
                    call.id(),
                    new McpError(-32601, "Method not found: " + call.method().wireName(), null));
        }
        return errorOutcome(call.id(), error);
    }

    private McpOutcome errorOutcome(Document id, RuntimeException error) {
        if (error instanceof McpProtocolException protocolError) {
            return new McpOutcome.Failure(
                    id,
                    new McpError(protocolError.code(), protocolError.getMessage(), protocolError.data()));
        }
        LOG.error("Unexpected MCP engine error", error);
        return new McpOutcome.Failure(id, new McpError(-32603, "Internal error", null));
    }

    private RuntimeException preserveOriginal(
            RuntimeException original,
            RuntimeException afterHookFailure
    ) {
        if (original == null) {
            return afterHookFailure;
        }
        if (original != afterHookFailure) {
            original.addSuppressed(afterHookFailure);
        }
        return original;
    }

}
