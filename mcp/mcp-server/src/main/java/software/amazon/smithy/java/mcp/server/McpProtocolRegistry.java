/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Immutable registry of built-in and extension MCP protocols.
 */
final class McpProtocolRegistry {
    private final Map<McpProtocolId, McpProtocol> protocols;
    private final List<String> supportedIdentifiers;
    private final McpProtocol initializationFallbackProtocol;

    private McpProtocolRegistry(Map<McpProtocolId, McpProtocol> protocols) {
        this.protocols = Collections.unmodifiableMap(new LinkedHashMap<>(protocols));
        supportedIdentifiers = protocols.keySet().stream().map(McpProtocolId::identifier).toList();
        initializationFallbackProtocol = this.protocols.values()
                .stream()
                .filter(protocol -> protocol.supportedMethods().contains(McpMethod.Standard.INITIALIZE))
                .filter(protocol -> !protocol.usesStatelessMetadata())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No initialization-capable MCP protocol is registered"));
    }

    static McpProtocolRegistry create(
            Collection<? extends ExtensionMcpProtocol> additions,
            Collection<? extends ExtensionMcpProtocol> overrides,
            boolean discover
    ) {
        return discover
                ? create(
                        additions,
                        overrides,
                        McpProtocolProvider.class.getClassLoader())
                : create(additions, overrides, List.of());
    }

    static McpProtocolRegistry create(
            Collection<? extends ExtensionMcpProtocol> additions,
            Collection<? extends ExtensionMcpProtocol> overrides,
            ClassLoader classLoader
    ) {
        return create(
                additions,
                overrides,
                ServiceLoader.load(McpProtocolProvider.class, classLoader));
    }

    static McpProtocolRegistry create(
            Collection<? extends ExtensionMcpProtocol> additions,
            Collection<? extends ExtensionMcpProtocol> overrides,
            Iterable<McpProtocolProvider> providers
    ) {
        var candidates = new LinkedHashMap<McpProtocolId, List<Candidate>>();
        for (var protocol : BuiltInProtocols.all()) {
            addCandidate(candidates, protocol, "built in");
        }
        for (var provider : providers) {
            Objects.requireNonNull(provider, "MCP protocol provider");
            var provided = Objects.requireNonNull(
                    provider.protocols(),
                    () -> "MCP protocol provider returned null: " + provider.getClass().getName());
            for (var protocol : provided) {
                addCandidate(
                        candidates,
                        protocol,
                        "SPI provider " + provider.getClass().getName());
            }
        }
        for (var protocol : additions) {
            addCandidate(candidates, protocol, "engine builder");
        }

        var explicitOverrides = new HashMap<McpProtocolId, ExtensionMcpProtocol>();
        for (var protocol : overrides) {
            Objects.requireNonNull(protocol, "MCP protocol override");
            var previous = explicitOverrides.put(protocol.id(), protocol);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate MCP protocol override: " + protocol.id().identifier());
            }
        }

        var resolved = new LinkedHashMap<McpProtocolId, McpProtocol>();
        for (var entry : candidates.entrySet()) {
            var override = explicitOverrides.remove(entry.getKey());
            if (override != null) {
                resolved.put(entry.getKey(), override);
                continue;
            }

            var registrations = entry.getValue();
            if (registrations.size() > 1) {
                throw conflict(entry.getKey(), registrations);
            }
            resolved.put(entry.getKey(), registrations.getFirst().protocol());
        }
        if (!explicitOverrides.isEmpty()) {
            var id = explicitOverrides.keySet().iterator().next();
            throw new IllegalArgumentException(
                    "Cannot override unregistered MCP protocol: " + id.identifier());
        }
        return new McpProtocolRegistry(resolved);
    }

    McpProtocol require(ProtocolVersion version) {
        var protocol = find(version);
        if (protocol != null) {
            return protocol;
        }
        throw new McpProtocolException(
                -32022,
                "Unsupported protocol version: " + version.identifier(),
                Document.of(Map.of(
                        "requested",
                        Document.of(version.identifier()),
                        "supported",
                        Document.of(supportedIdentifiers.stream()
                                .map(Document::of)
                                .toList()))));
    }

    McpProtocol find(ProtocolVersion version) {
        return protocols.get(McpProtocolId.of(version.identifier()));
    }

    McpProtocol defaultProtocol() {
        return protocols.get(ProtocolVersion.defaultVersion().id());
    }

    McpProtocol initializationFallbackProtocol() {
        return initializationFallbackProtocol;
    }

    List<String> supportedIdentifiers() {
        return supportedIdentifiers;
    }

    private static void addCandidate(
            Map<McpProtocolId, List<Candidate>> candidates,
            McpProtocol protocol,
            String source
    ) {
        Objects.requireNonNull(protocol, "MCP protocol");
        Objects.requireNonNull(protocol.id(), "MCP protocol id");
        candidates.computeIfAbsent(protocol.id(), ignored -> new ArrayList<>())
                .add(new Candidate(protocol, source));
    }

    private static IllegalStateException conflict(
            McpProtocolId id,
            List<Candidate> candidates
    ) {
        var sources = candidates.stream()
                .map(candidate -> candidate.protocol().getClass().getName() + " from " + candidate.source())
                .sorted()
                .toList();
        return new IllegalStateException(
                "Conflicting MCP protocol implementations for "
                        + id.identifier()
                        + ": "
                        + String.join(", ", sources)
                        + ". Use overrideProtocol to select one explicitly.");
    }

    private record Candidate(McpProtocol protocol, String source) {}
}
