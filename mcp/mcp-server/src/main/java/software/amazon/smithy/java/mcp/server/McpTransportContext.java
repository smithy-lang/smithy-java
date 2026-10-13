/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.mcp.server;

import java.util.List;
import java.util.Map;
import software.amazon.smithy.utils.SmithyUnstableApi;

/**
 * Transport-specific request information available during protocol validation.
 */
@SmithyUnstableApi
public interface McpTransportContext {
    McpTransportContext STDIO = new Stdio();

    /**
     * Returns whether this transport can deliver unsolicited server messages.
     */
    default boolean supportsServerNotifications() {
        return false;
    }

    /**
     * Returns whether this transport can host long-lived {@code subscriptions/listen} streams.
     */
    default boolean supportsSubscriptions() {
        return false;
    }

    record Stdio() implements McpTransportContext {
        @Override
        public boolean supportsServerNotifications() {
            return true;
        }

        @Override
        public boolean supportsSubscriptions() {
            return true;
        }
    }

    record Http(Map<String, List<String>> headers, boolean loopbackOnly) implements McpTransportContext {
        public Http {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }
    }
}
