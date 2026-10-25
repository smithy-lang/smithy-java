/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.List;
import java.util.Set;

final class BuiltInProtocols {
    private static final McpProtocolFeatures LEGACY =
            new McpProtocolFeatures(false, false, false, false, false);
    private static final McpProtocolFeatures ANNOTATIONS =
            new McpProtocolFeatures(false, true, false, false, false);
    private static final McpProtocolFeatures STRUCTURED_OUTPUT =
            new McpProtocolFeatures(true, true, false, false, false);
    private static final McpProtocolFeatures STATELESS =
            new McpProtocolFeatures(true, true, true, true, true);

    private static final Set<McpMethod.Standard> LEGACY_METHODS = Set.of(
            McpMethod.Standard.INITIALIZE,
            McpMethod.Standard.PING,
            McpMethod.Standard.PROMPTS_LIST,
            McpMethod.Standard.PROMPTS_GET,
            McpMethod.Standard.COMPLETION_COMPLETE,
            McpMethod.Standard.LOGGING_SET_LEVEL,
            McpMethod.Standard.TOOLS_LIST,
            McpMethod.Standard.TOOLS_CALL,
            McpMethod.Standard.NOTIFICATIONS_INITIALIZED,
            McpMethod.Standard.NOTIFICATIONS_TOOLS_LIST_CHANGED);
    private static final Set<McpMethod.Standard> STATELESS_METHODS = Set.of(
            McpMethod.Standard.SERVER_DISCOVER,
            McpMethod.Standard.PROMPTS_LIST,
            McpMethod.Standard.PROMPTS_GET,
            McpMethod.Standard.COMPLETION_COMPLETE,
            McpMethod.Standard.TOOLS_LIST,
            McpMethod.Standard.TOOLS_CALL);

    private static final BuiltInProtocol V2024_11_05 = new BuiltInProtocol(
            KnownProtocolVersion.V2024_11_05,
            LEGACY_METHODS,
            LEGACY);
    private static final BuiltInProtocol V2025_03_26 = new BuiltInProtocol(
            KnownProtocolVersion.V2025_03_26,
            LEGACY_METHODS,
            ANNOTATIONS);
    private static final BuiltInProtocol V2025_06_18 = new BuiltInProtocol(
            KnownProtocolVersion.V2025_06_18,
            LEGACY_METHODS,
            STRUCTURED_OUTPUT);
    private static final BuiltInProtocol V2025_11_25 = new BuiltInProtocol(
            KnownProtocolVersion.V2025_11_25,
            LEGACY_METHODS,
            STRUCTURED_OUTPUT);
    private static final BuiltInProtocol V2026_07_28 = new BuiltInProtocol(
            KnownProtocolVersion.V2026_07_28,
            STATELESS_METHODS,
            STATELESS);

    private static final List<BuiltInProtocol> ALL = List.of(
            V2026_07_28,
            V2025_11_25,
            V2025_06_18,
            V2025_03_26,
            V2024_11_05);

    private BuiltInProtocols() {}

    static BuiltInProtocol protocol(KnownProtocolVersion version) {
        return switch (version) {
            case V2024_11_05 -> V2024_11_05;
            case V2025_03_26 -> V2025_03_26;
            case V2025_06_18 -> V2025_06_18;
            case V2025_11_25 -> V2025_11_25;
            case V2026_07_28 -> V2026_07_28;
        };
    }

    static List<BuiltInProtocol> all() {
        return ALL;
    }
}
