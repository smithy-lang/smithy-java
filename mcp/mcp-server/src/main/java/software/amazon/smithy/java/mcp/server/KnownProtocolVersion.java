/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Protocol versions implemented by the server.
 *
 * <p>The declaration order is chronological. Code that varies by protocol behavior
 * must use {@link BuiltInProtocols#protocol(KnownProtocolVersion)} rather than comparing
 * versions.
 */
@SmithyUnstableApi
public enum KnownProtocolVersion implements ProtocolVersion {
    V2024_11_05("2024-11-05"),
    V2025_03_26("2025-03-26"),
    V2025_06_18("2025-06-18"),
    V2025_11_25("2025-11-25"),
    V2026_07_28("2026-07-28");

    private static final Map<String, KnownProtocolVersion> BY_IDENTIFIER;
    private static final List<String> SUPPORTED_IDENTIFIERS;

    static {
        var byIdentifier = new LinkedHashMap<String, KnownProtocolVersion>();
        for (var version : values()) {
            if (byIdentifier.put(version.identifier, version) != null) {
                throw new IllegalStateException("Duplicate MCP protocol version: " + version.identifier);
            }
        }
        BY_IDENTIFIER = Collections.unmodifiableMap(byIdentifier);
        var versions = values();
        var supported = new ArrayList<String>(versions.length);
        for (int index = versions.length - 1; index >= 0; index--) {
            supported.add(versions[index].identifier());
        }
        SUPPORTED_IDENTIFIERS = List.copyOf(supported);
    }

    private final String identifier;
    private final McpProtocolId id;

    KnownProtocolVersion(String identifier) {
        this.identifier = identifier;
        this.id = McpProtocolId.of(identifier);
    }

    @Override
    public String identifier() {
        return identifier;
    }

    public McpProtocolId id() {
        return id;
    }

    static KnownProtocolVersion fromIdentifier(String identifier) {
        return BY_IDENTIFIER.get(identifier);
    }

    static List<String> supportedIdentifiers() {
        return SUPPORTED_IDENTIFIERS;
    }
}
