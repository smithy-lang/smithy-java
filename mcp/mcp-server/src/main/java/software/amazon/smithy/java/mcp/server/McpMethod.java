/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * A typed MCP method name.
 */
@SmithyUnstableApi
public sealed interface McpMethod permits McpMethod.Standard, McpMethod.Extension, McpMethod.Unknown {

    /**
     * Returns the wire method name.
     */
    String wireName();

    /**
     * Parses a wire method name.
     */
    static McpMethod parse(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        var standard = Standard.fromWireName(wireName);
        return standard == null ? new Unknown(wireName) : standard;
    }

    /**
     * Methods defined by the MCP protocol.
     */
    enum Standard implements McpMethod {
        INITIALIZE("initialize"),
        PING("ping"),
        SERVER_DISCOVER("server/discover"),
        PROMPTS_LIST("prompts/list"),
        PROMPTS_GET("prompts/get"),
        COMPLETION_COMPLETE("completion/complete"),
        LOGGING_SET_LEVEL("logging/setLevel"),
        TOOLS_LIST("tools/list"),
        TOOLS_CALL("tools/call"),
        RESOURCES_READ("resources/read"),
        NOTIFICATIONS_INITIALIZED("notifications/initialized"),
        NOTIFICATIONS_TOOLS_LIST_CHANGED("notifications/tools/list_changed");

        private static final Map<String, Standard> BY_WIRE_NAME;

        static {
            var methods = new HashMap<String, Standard>();
            for (var method : values()) {
                if (methods.put(method.wireName, method) != null) {
                    throw new IllegalStateException("Duplicate MCP method: " + method.wireName);
                }
            }
            BY_WIRE_NAME = Collections.unmodifiableMap(methods);
        }

        private final String wireName;

        Standard(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        static Standard fromWireName(String wireName) {
            return BY_WIRE_NAME.get(wireName);
        }
    }

    /**
     * A registered extension method.
     */
    record Extension(String wireName) implements McpMethod {
        public Extension {
            requireWireName(wireName);
        }
    }

    /**
     * An unrecognized method received from a peer.
     */
    record Unknown(String wireName) implements McpMethod {
        public Unknown {
            requireWireName(wireName);
        }
    }

    private static void requireWireName(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        if (wireName.isBlank()) {
            throw new IllegalArgumentException("MCP method name must not be blank");
        }
    }
}
