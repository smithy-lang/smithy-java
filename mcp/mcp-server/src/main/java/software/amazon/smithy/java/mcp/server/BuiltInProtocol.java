/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Declarative behavior for one built-in MCP protocol version.
 */
record BuiltInProtocol(
        KnownProtocolVersion version,
        Set<McpMethod.Standard> supportedMethods,
        McpProtocolFeatures features) implements McpProtocol {

    BuiltInProtocol {
        supportedMethods = Set.copyOf(supportedMethods);
    }

    @Override
    public McpProtocolId id() {
        return version.id();
    }

    @Override
    public Document decorateResult(
            Document result,
            McpMethod method,
            McpServerIdentity serverIdentity
    ) {
        if (!features.statelessResults()) {
            return result;
        }

        var members = new HashMap<>(result.asStringMap());
        members.put("resultType", Document.of("complete"));

        var meta = members.containsKey("_meta")
                ? new HashMap<>(members.get("_meta").asStringMap())
                : new HashMap<String, Document>();
        meta.put(McpWireNames.SERVER_INFO,
                Document.of(Map.of(
                        "name",
                        Document.of(serverIdentity.name()),
                        "version",
                        Document.of(serverIdentity.version()))));
        members.put("_meta", Document.of(meta));

        if (switch (method) {
            case McpMethod.Standard.SERVER_DISCOVER,
                    McpMethod.Standard.TOOLS_LIST,
                    McpMethod.Standard.PROMPTS_LIST ->
                true;
            default -> false;
        }) {
            members.put("ttlMs", Document.of(0));
            members.put("cacheScope", Document.of("private"));
        }
        return Document.of(members);
    }

}
